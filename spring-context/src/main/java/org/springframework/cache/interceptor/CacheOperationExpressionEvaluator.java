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

package org.springframework.cache.interceptor;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cache.Cache;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class handling the SpEL expression parsing.
 * Meant to be used as a reusable, thread-safe component.
 *
 * <p>Performs internal caching for performance reasons
 * using {@link AnnotatedElementKey}.
 *
 * @author Costin Leau
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.1
 */
class CacheOperationExpressionEvaluator extends CachedExpressionEvaluator {

    /**
     * Indicate that there is no result variable.
     */
    public static final Object NO_RESULT = new Object();

    /**
     * Indicate that the result variable cannot be used at all.
     */
    public static final Object RESULT_UNAVAILABLE = new Object();

    /**
     * The name of the variable holding the result object.
     */
    public static final String RESULT_VARIABLE = "result";


    private final Map<ExpressionKey, Expression> keyCache = new ConcurrentHashMap<>(64);

    private final Map<ExpressionKey, Expression> conditionCache = new ConcurrentHashMap<>(64);

    private final Map<ExpressionKey, Expression> unlessCache = new ConcurrentHashMap<>(64);


    /**
     * Create an {@link EvaluationContext}.
     * @param caches the current caches
     * @param method the method
     * @param args the method arguments
     * @param target the target object
     * @param targetClass the target class
     * @param result the return value (can be {@code null}) or
     * {@link #NO_RESULT} if there is no return at this time
     * @return the evaluation context
     */
    public EvaluationContext createEvaluationContext(Collection<? extends Cache> caches,
                                                     Method method, Object[] args, Object target, Class<?> targetClass, Method targetMethod,
                                                     @Nullable Object result, @Nullable BeanFactory beanFactory) {

        // 构造 SpEL 的根对象
        CacheExpressionRootObject rootObject = new CacheExpressionRootObject(
                caches, method, args, target, targetClass);
        /**
         * 构造 解析 SpEL 表达式的上下文。
         *
         * CacheEvaluationContext 主要是重写了 {@link StandardEvaluationContext#lookupVariable(String)}
         * 目的是自定义获取变量的逻辑，重写方法获取变量的逻辑就是 将方法参数名称,p1,a1 设置到变量中
         *        {@link MethodBasedEvaluationContext#lookupVariable(String)}
         *
         * 这也就是为啥 @Cacheable、@CacheEvict、@CachePut 能使用 #p1,@a1,@参数名 来引用方法入参的原理
         * */
        CacheEvaluationContext evaluationContext = new CacheEvaluationContext(
                rootObject, targetMethod, args, getParameterNameDiscoverer());
        // 单独设置 result 变量
        if (result == RESULT_UNAVAILABLE) {
            /**
             * 记录不可用变量，不是SpEL提供的，是CacheEvaluationContext维护的一个集合，在使用变量是这个的时候，直接报错。但是没啥含义 目前就有一个地方引用了，引用的地方直接把异常捕获了
             * 没有做任何处理 {@link CacheAspectSupport#hasCachePut(CacheAspectSupport.CacheOperationContexts)}
             *
             * {@link CacheEvaluationContext#lookupVariable(String)}
             * */
            evaluationContext.addUnavailableVariable(RESULT_VARIABLE);
        } else if (result != NO_RESULT) {
            /**
             * 设置变量
             *
             * 只有这三种的解析才会有方法的返回值(缓存值)
             *     - @CachePut(unless="")
             *     - @Cacheable(unless="")
             *     - @CacheEvict(beforeInvocation = false,condition="")
             *
             * */
            evaluationContext.setVariable(RESULT_VARIABLE, result);
        }
        if (beanFactory != null) {
            // 设置BeanResolver，就是SpEL语法 @bean 引用BeanFactory中的bean，是使用这个解析的
            evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
        }
        // 返回计算上下文
        return evaluationContext;
    }

    @Nullable
    public Object key(String keyExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
        return getExpression(this.keyCache, methodKey, keyExpression).getValue(evalContext);
    }

    public boolean condition(String conditionExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
        return (Boolean.TRUE.equals(getExpression(this.conditionCache, methodKey, conditionExpression).getValue(
                evalContext, Boolean.class)));
    }

    public boolean unless(String unlessExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
        return (Boolean.TRUE.equals(getExpression(this.unlessCache, methodKey, unlessExpression).getValue(
                evalContext, Boolean.class)));
    }

    /**
     * Clear all caches.
     */
    void clear() {
        this.keyCache.clear();
        this.conditionCache.clear();
        this.unlessCache.clear();
    }

}
