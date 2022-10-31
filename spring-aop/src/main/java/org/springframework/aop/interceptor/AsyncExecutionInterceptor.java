/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.aop.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * AOP Alliance {@code MethodInterceptor} that processes method invocations
 * asynchronously, using a given {@link org.springframework.core.task.AsyncTaskExecutor}.
 * Typically used with the {@link org.springframework.scheduling.annotation.Async} annotation.
 *
 * <p>In terms of target method signatures, any parameter types are supported.
 * However, the return type is constrained to either {@code void} or
 * {@code java.util.concurrent.Future}. In the latter case, the Future handle
 * returned from the proxy will be an actual asynchronous Future that can be used
 * to track the result of the asynchronous method execution. However, since the
 * target method needs to implement the same signature, it will have to return
 * a temporary Future handle that just passes the return value through
 * (like Spring's {@link org.springframework.scheduling.annotation.AsyncResult}
 * or EJB 3.1's {@code javax.ejb.AsyncResult}).
 *
 * <p>When the return type is {@code java.util.concurrent.Future}, any exception thrown
 * during the execution can be accessed and managed by the caller. With {@code void}
 * return type however, such exceptions cannot be transmitted back. In that case an
 * {@link AsyncUncaughtExceptionHandler} can be registered to process such exceptions.
 *
 * <p>As of Spring 3.1.2 the {@code AnnotationAsyncExecutionInterceptor} subclass is
 * preferred for use due to its support for executor qualification in conjunction with
 * Spring's {@code @Async} annotation.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Stephane Nicoll
 * @since 3.0
 * @see org.springframework.scheduling.annotation.Async
 * @see org.springframework.scheduling.annotation.AsyncAnnotationAdvisor
 * @see org.springframework.scheduling.annotation.AnnotationAsyncExecutionInterceptor
 */
public class AsyncExecutionInterceptor extends AsyncExecutionAspectSupport implements MethodInterceptor, Ordered {

    /**
     * Create a new instance with a default {@link AsyncUncaughtExceptionHandler}.
     * @param defaultExecutor the {@link Executor} (typically a Spring {@link AsyncTaskExecutor}
     * or {@link java.util.concurrent.ExecutorService}) to delegate to;
     * as of 4.2.6, a local executor for this interceptor will be built otherwise
     */
    public AsyncExecutionInterceptor(@Nullable Executor defaultExecutor) {
        super(defaultExecutor);
    }

    /**
     * Create a new {@code AsyncExecutionInterceptor}.
     * @param defaultExecutor the {@link Executor} (typically a Spring {@link AsyncTaskExecutor}
     * or {@link java.util.concurrent.ExecutorService}) to delegate to;
     * as of 4.2.6, a local executor for this interceptor will be built otherwise
     * @param exceptionHandler the {@link AsyncUncaughtExceptionHandler} to use
     */
    public AsyncExecutionInterceptor(@Nullable Executor defaultExecutor, AsyncUncaughtExceptionHandler exceptionHandler) {
        super(defaultExecutor, exceptionHandler);
    }


    /**
     * Intercept the given method invocation, submit the actual calling of the method to
     * the correct task executor and return immediately to the caller.
     * @param invocation the method to intercept and make asynchronous
     * @return {@link Future} if the original method returns {@code Future}; {@code null}
     * otherwise.
     */
    @Override
    @Nullable
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = (invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null);
        Method specificMethod = ClassUtils.getMostSpecificMethod(invocation.getMethod(), targetClass);
        final Method userDeclaredMethod = BridgeMethodResolver.findBridgedMethod(specificMethod);
        /**
         * 拿到方法对应的Executor。
         *
         * @Async的查找顺序：方法 -> 方法声明的类
         *
         *  有注解@Async("beanName")，就通过beanName从容器中获取Executor，拿不到直接报错
         * `@Async` 没有指定beanName，就使用默认的Executor {@link AsyncExecutionAspectSupport#defaultExecutor}，会在构造器设置这个属性
         *
         * 构造器 {@link AsyncExecutionAspectSupport#AsyncExecutionAspectSupport(Executor)}
         *     defaultExecutor 是 {@link org.springframework.scheduling.annotation.AbstractAsyncConfiguration#setConfigurers(Collection)} 设置的
         *    `this.defaultExecutor = new SingletonSupplier<>(defaultExecutor, () -> getDefaultExecutor(this.beanFactory));`
         *          没有 defaultExecutor 也会执行{@link AsyncExecutionInterceptor#getDefaultExecutor(BeanFactory)} 拿到一个
         *          从BeanFactory中找 先找TaskExecutor，没有在找Executor 类型的bean
         *          找不到就 `new SimpleAsyncTaskExecutor()`
         *
         * Tips：方法的Executor，可以通过 @Async("beanName") 注解的值拿到，在BeanFactory中找，找不到就报错。没有设置注解值，就找默认的，
         *      默认查找顺序：AsyncConfigurer -> TaskExecutor -> Executor -> `new SimpleAsyncTaskExecutor()`
         * */
        AsyncTaskExecutor executor = determineAsyncExecutor(userDeclaredMethod);
        // 没得 Executor 就直接报错咯
        if (executor == null) {
            throw new IllegalStateException(
                    "No executor specified and no default executor set on AsyncExecutionInterceptor either");
        }

        Callable<Object> task = () -> {
            try {
                // 执行方法
                Object result = invocation.proceed();
                if (result instanceof Future) {
                    return ((Future<?>) result).get();
                }
            } catch (ExecutionException ex) {
                // 异常处理
                handleError(ex.getCause(), userDeclaredMethod, invocation.getArguments());
            } catch (Throwable ex) {
                // 异常处理
                handleError(ex, userDeclaredMethod, invocation.getArguments());
            }
            return null;
        };

        /**
         * 提交异步任务，就是使用Executor执行任务
         * 支持三种特殊的返回值类型 CompletableFuture、ListenableFuture、Future 会有返回值，其他的返回null
         * */
        return doSubmit(task, executor, invocation.getMethod().getReturnType());
    }

    /**
     * This implementation is a no-op for compatibility in Spring 3.1.2.
     * Subclasses may override to provide support for extracting qualifier information,
     * e.g. via an annotation on the given method.
     * @return always {@code null}
     * @since 3.1.2
     * @see #determineAsyncExecutor(Method)
     */
    @Override
    @Nullable
    protected String getExecutorQualifier(Method method) {
        return null;
    }

    /**
     * This implementation searches for a unique {@link org.springframework.core.task.TaskExecutor}
     * bean in the context, or for an {@link Executor} bean named "taskExecutor" otherwise.
     * If neither of the two is resolvable (e.g. if no {@code BeanFactory} was configured at all),
     * this implementation falls back to a newly created {@link SimpleAsyncTaskExecutor} instance
     * for local use if no default could be found.
     * @see #DEFAULT_TASK_EXECUTOR_BEAN_NAME
     */
    @Override
    @Nullable
    protected Executor getDefaultExecutor(@Nullable BeanFactory beanFactory) {
        Executor defaultExecutor = super.getDefaultExecutor(beanFactory);
        return (defaultExecutor != null ? defaultExecutor : new SimpleAsyncTaskExecutor());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}
