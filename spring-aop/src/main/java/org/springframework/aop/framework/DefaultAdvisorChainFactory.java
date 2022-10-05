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

package org.springframework.aop.framework;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.*;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

    /**
     * 将 advisors 中 method 匹配的 advisor，解析成 InterceptorAndDynamicMethodMatcher、MethodInterceptor 类型的集合
     *
     * @param config      the AOP configuration in the form of an Advised object
     * @param method      the proxied method
     * @param targetClass the target class (may be {@code null} to indicate a proxy without
     *                    target object, in which case the method's declaring class is the next best option)
     * @return
     */
    @Override
    public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass) {

        // This is somewhat tricky... We have to process introductions first,
        // but we need to preserve order in the ultimate list.
        AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
        Advisor[] advisors = config.getAdvisors();
        List<Object> interceptorList = new ArrayList<>(advisors.length);
        Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
        Boolean hasIntroductions = null;

        for (Advisor advisor : advisors) {
            if (advisor instanceof PointcutAdvisor) {
                // Add it conditionally.
                PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
                if (config.isPreFiltered() || pointcutAdvisor.getPointcut()
                        .getClassFilter()
                        .matches(actualClass)) {
                    MethodMatcher mm = pointcutAdvisor.getPointcut()
                            .getMethodMatcher();
                    boolean match;
                    if (mm instanceof IntroductionAwareMethodMatcher) {
                        if (hasIntroductions == null) {
                            // 类匹配
                            hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
                        }
                        // AspectJ 匹配
                        match = ((IntroductionAwareMethodMatcher) mm).matches(method, actualClass, hasIntroductions);
                    } else {
                        // AspectJ 匹配
                        match = mm.matches(method, actualClass);
                    }
                    if (match) {
                        // 将 advisor 通过适配器解析成 MethodInterceptor
                        MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
                        /**
                         * 是运行时。说白了就是需要在执行的时候进行匹配，所以要把 MethodMatcher 传入
                         * 比如@Around、@Before、@After、@AfterThrowing、@AfterReturning生成的Advisor {@link AspectJExpressionPointcut#isRuntime()}
                         * */
                        if (mm.isRuntime()) {
                            /**
                             * 装饰成 InterceptorAndDynamicMethodMatcher
                             * 这个类型的特点是，拦截器执行时会执行 {@link MethodMatcher#matches(Method, Class)}，是true在执行 {@link MethodInterceptor#invoke(MethodInvocation)}
                             *
                             * 执行的代码 {@link ReflectiveMethodInvocation#proceed()}
                             * */
                            // Creating a new object instance in the getInterceptors() method
                            // isn't a problem as we normally cache created chains.
                            for (MethodInterceptor interceptor : interceptors) {
                                interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
                            }
                        } else {
                            interceptorList.addAll(Arrays.asList(interceptors));
                        }
                    }
                }
            } else if (advisor instanceof IntroductionAdvisor) {
                // 类匹配
                IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
                if (config.isPreFiltered() || ia.getClassFilter()
                        .matches(actualClass)) {
                    // 将 advisor 通过适配器解析成 MethodInterceptor
                    Interceptor[] interceptors = registry.getInterceptors(advisor);
                    interceptorList.addAll(Arrays.asList(interceptors));
                }
            } else {
                // 将 advisor 通过适配器解析成 MethodInterceptor
                Interceptor[] interceptors = registry.getInterceptors(advisor);
                interceptorList.addAll(Arrays.asList(interceptors));
            }
        }

        return interceptorList;
    }

    /**
     * Determine whether the Advisors contain matching introductions.
     */
    private static boolean hasMatchingIntroductions(Advisor[] advisors, Class<?> actualClass) {
        for (Advisor advisor : advisors) {
            if (advisor instanceof IntroductionAdvisor) {
                IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
                if (ia.getClassFilter()
                        .matches(actualClass)) {
                    return true;
                }
            }
        }
        return false;
    }

}
