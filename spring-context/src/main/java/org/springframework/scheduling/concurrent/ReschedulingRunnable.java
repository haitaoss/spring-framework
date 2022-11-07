/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.scheduling.concurrent;

import org.springframework.lang.Nullable;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.DelegatingErrorHandlingRunnable;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.util.Assert;
import org.springframework.util.ErrorHandler;

import java.time.Clock;
import java.util.Date;
import java.util.concurrent.*;

/**
 * Internal adapter that reschedules an underlying {@link Runnable} according
 * to the next execution time suggested by a given {@link Trigger}.
 *
 * <p>Necessary because a native {@link ScheduledExecutorService} supports
 * delay-driven execution only. The flexibility of the {@link Trigger} interface
 * will be translated onto a delay for the next execution time (repeatedly).
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 3.0
 */
class ReschedulingRunnable extends DelegatingErrorHandlingRunnable implements ScheduledFuture<Object> {

    private final Trigger trigger;

    private final SimpleTriggerContext triggerContext;

    private final ScheduledExecutorService executor;

    @Nullable
    private ScheduledFuture<?> currentFuture;

    @Nullable
    private Date scheduledExecutionTime;

    private final Object triggerContextMonitor = new Object();


    public ReschedulingRunnable(Runnable delegate, Trigger trigger, Clock clock,
                                ScheduledExecutorService executor, ErrorHandler errorHandler) {

        super(delegate, errorHandler);
        this.trigger = trigger;
        this.triggerContext = new SimpleTriggerContext(clock);
        this.executor = executor;
    }


    @Nullable
    public ScheduledFuture<?> schedule() {
        synchronized (this.triggerContextMonitor) {
            // 下次执行时间
            this.scheduledExecutionTime = this.trigger.nextExecutionTime(this.triggerContext);
            if (this.scheduledExecutionTime == null) {
                return null;
            }
            // 计算得出延时时间
            long initialDelay = this.scheduledExecutionTime.getTime() - this.triggerContext.getClock().millis();
            /**
             * 默认是这个
             * {@link ScheduledThreadPoolExecutor#schedule(Runnable, long, TimeUnit)}
             * */
            this.currentFuture = this.executor.schedule(this, initialDelay, TimeUnit.MILLISECONDS);
            return this;
        }
    }

    private ScheduledFuture<?> obtainCurrentFuture() {
        Assert.state(this.currentFuture != null, "No scheduled future");
        return this.currentFuture;
    }

    @Override
    public void run() {
        Date actualExecutionTime = new Date(this.triggerContext.getClock().millis());
        // 执行真正的任务
        super.run();
        Date completionTime = new Date(this.triggerContext.getClock().millis());
        synchronized (this.triggerContextMonitor) {
            Assert.state(this.scheduledExecutionTime != null, "No scheduled execution");
            this.triggerContext.update(this.scheduledExecutionTime, actualExecutionTime, completionTime);
            // 没有取消
            if (!obtainCurrentFuture().isCancelled()) {
                /**
                 * 调度任务。就是执行JDK的延时任务，延时时间到了就会回调当前的run方法，
                 * 从而可以实现定时任务的一直执行
                 * */
                schedule();
            }
        }
    }


    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this.triggerContextMonitor) {
            return obtainCurrentFuture().cancel(mayInterruptIfRunning);
        }
    }

    @Override
    public boolean isCancelled() {
        synchronized (this.triggerContextMonitor) {
            return obtainCurrentFuture().isCancelled();
        }
    }

    @Override
    public boolean isDone() {
        synchronized (this.triggerContextMonitor) {
            return obtainCurrentFuture().isDone();
        }
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        ScheduledFuture<?> curr;
        synchronized (this.triggerContextMonitor) {
            curr = obtainCurrentFuture();
        }
        return curr.get();
    }

    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        ScheduledFuture<?> curr;
        synchronized (this.triggerContextMonitor) {
            curr = obtainCurrentFuture();
        }
        return curr.get(timeout, unit);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        ScheduledFuture<?> curr;
        synchronized (this.triggerContextMonitor) {
            curr = obtainCurrentFuture();
        }
        return curr.getDelay(unit);
    }

    @Override
    public int compareTo(Delayed other) {
        if (this == other) {
            return 0;
        }
        long diff = getDelay(TimeUnit.MILLISECONDS) - other.getDelay(TimeUnit.MILLISECONDS);
        return (diff == 0 ? 0 : ((diff < 0) ? -1 : 1));
    }

}
