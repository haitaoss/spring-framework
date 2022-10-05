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

package org.springframework.aop.framework.adapter;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

    /**
     * 可以通过设置BeanPostProcessor扩展默认的解析器
     * {@link AdvisorAdapterRegistrationManager#postProcessAfterInitialization(Object, String)}
     */
    private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


    /**
     * Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
     */
    public DefaultAdvisorAdapterRegistry() {
        registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
        registerAdvisorAdapter(new AfterReturningAdviceAdapter());
        registerAdvisorAdapter(new ThrowsAdviceAdapter());
    }


    /**
     * 装饰 adviceObject
     * <p>
     * 1. 是 Advisor 类型的直接返回
     * 2. 是 Advice 类型
     * 2.1 是 MethodInterceptor 类型 装饰成 `new DefaultPointcutAdvisor(advice)`
     * 2.2 遍历适配器 {@link DefaultAdvisorAdapterRegistry#DefaultAdvisorAdapterRegistry()}
     * 判断是否支持 {@link AdvisorAdapter#supportsAdvice(Advice)}
     * 支持 装饰成 `new DefaultPointcutAdvisor(advice)`
     *
     * @param adviceObject an object that should be an advice
     * @return
     * @throws UnknownAdviceTypeException
     */
    @Override
    public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
        if (adviceObject instanceof Advisor) {
            return (Advisor) adviceObject;
        }
        if (!(adviceObject instanceof Advice)) {
            throw new UnknownAdviceTypeException(adviceObject);
        }
        Advice advice = (Advice) adviceObject;
        if (advice instanceof MethodInterceptor) {
            // So well-known it doesn't even need an adapter.
            return new DefaultPointcutAdvisor(advice);
        }
        for (AdvisorAdapter adapter : this.adapters) {
            // Check that it is supported.
            if (adapter.supportsAdvice(advice)) {
                return new DefaultPointcutAdvisor(advice);
            }
        }
        throw new UnknownAdviceTypeException(advice);
    }

    /**
     * 将 advisor 解析成 MethodInterceptor[]，然后返回
     * <p>
     * 使用 AdvisorAdapter 适配{@link AdvisorAdapter#supportsAdvice(Advice)} Advice
     * 适配就解析 {@link AdvisorAdapter#getInterceptor(Advisor)} 成 MethodInterceptor
     *
     * @param advisor the Advisor to find an interceptor for
     * @return
     * @throws UnknownAdviceTypeException
     */
    @Override
    public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
        List<MethodInterceptor> interceptors = new ArrayList<>(3);
        Advice advice = advisor.getAdvice();
        if (advice instanceof MethodInterceptor) {
            // 是这个类型就直接添加
            interceptors.add((MethodInterceptor) advice);
        }
        /**
         * 使用适配器将 advice 解析成 MethodInterceptor
         * 默认 MethodBeforeAdviceAdapter、AfterReturningAdviceAdapter、ThrowsAdviceAdapter 有这三个适配器
         * */
        for (AdvisorAdapter adapter : this.adapters) {
            if (adapter.supportsAdvice(advice)) {
                interceptors.add(adapter.getInterceptor(advisor));
            }
        }
        if (interceptors.isEmpty()) {
            throw new UnknownAdviceTypeException(advisor.getAdvice());
        }
        return interceptors.toArray(new MethodInterceptor[0]);
    }

    @Override
    public void registerAdvisorAdapter(AdvisorAdapter adapter) {
        this.adapters.add(adapter);
    }

}
