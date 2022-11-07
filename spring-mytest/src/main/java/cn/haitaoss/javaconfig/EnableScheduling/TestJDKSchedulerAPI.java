package cn.haitaoss.javaconfig.EnableScheduling;

import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.util.concurrent.*;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-05 09:29
 * JDK提供的定时任务API
 */
public class TestJDKSchedulerAPI {
    /**
     * `ScheduledExecutorService`是基于多线程的，设计的初衷是为了解决`Timer`单线程执行，多个任务之间会互相影响的问题。
     *
     * 它主要包含4个方法：
     *  - `schedule(Runnable command,long delay,TimeUnit unit)`，带延迟时间的调度，只执行一次，调度之后可通过Future.get()阻塞直至任务执行完毕。
     *  - `schedule(Callable<V> callable,long delay,TimeUnit unit)`，带延迟时间的调度，只执行一次，调度之后可通过Future.get()阻塞直至任务执行完毕，并且可以获取执行结果。
     *  - `scheduleAtFixedRate`，表示以固定频率执行的任务，如果当前任务耗时较多，超过定时周期period，则当前任务结束后会立即执行。
     *  - `scheduleWithFixedDelay`，表示以固定延时执行任务，延时是相对当前任务结束为起点计算开始时间。
     *  注：返回值是 ScheduledFuture ,可以执行 {@link Future#cancel(boolean)} 中断定时任务的执行
     * */
    @SneakyThrows
    public static void main(String[] args) {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2);
        Runnable run = () -> {
            System.out.println(LocalDateTime.now() + "--->" + Thread.currentThread().getName() + "--->执行了...Runnable");
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // throw new RuntimeException("中断周期任务的执行");
        };
        Callable<String> call = () -> {
            System.out.println(LocalDateTime.now() + "--->" + Thread.currentThread().getName() + "--->执行了...Callable");
            return "return...msg";
        };

        // 延时3秒执行
        scheduledExecutorService.schedule(run, 3, TimeUnit.SECONDS);
        System.out.println("=============================================");

        // 延时3秒执行,堵塞等待结果
        ScheduledFuture<String> schedule = scheduledExecutorService.schedule(call, 3, TimeUnit.SECONDS);
        schedule.cancel(true);
        /*String ret = schedule.get();
        System.out.println("ret = " + ret);*/
        System.out.println("=============================================");


        // 第一次执行延时一秒，后面按照1秒一次执行
        scheduledExecutorService.scheduleAtFixedRate(run, 1, 1, TimeUnit.SECONDS);
        System.out.println("=============================================");

        // 第一次执行延时一秒，任务执行完之后延时3秒在执行任务
        scheduledExecutorService.scheduleWithFixedDelay(run, 1, 1, TimeUnit.SECONDS);

        //        scheduledExecutorService.shutdown();
    }
}
