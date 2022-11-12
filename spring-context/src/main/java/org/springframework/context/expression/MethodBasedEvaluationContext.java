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

package org.springframework.context.expression;

import org.springframework.cache.interceptor.CacheAspectSupport;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * A method-based {@link org.springframework.expression.EvaluationContext} that
 * provides explicit support for method-based invocations.
 *
 * <p>Expose the actual method arguments using the following aliases:
 * <ol>
 * <li>pX where X is the index of the argument (p0 for the first argument)</li>
 * <li>aX where X is the index of the argument (a1 for the second argument)</li>
 * <li>the name of the parameter as discovered by a configurable {@link ParameterNameDiscoverer}</li>
 * </ol>
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.2
 */
public class MethodBasedEvaluationContext extends StandardEvaluationContext {

    private final Method method;

    private final Object[] arguments;

    private final ParameterNameDiscoverer parameterNameDiscoverer;

    private boolean argumentsLoaded = false;


    public MethodBasedEvaluationContext(Object rootObject, Method method, Object[] arguments,
                                        ParameterNameDiscoverer parameterNameDiscoverer) {

        super(rootObject);
        this.method = method;
        this.arguments = arguments;
        // 解析参数列表 参数名称的
        this.parameterNameDiscoverer = parameterNameDiscoverer;
    }


    /**
     * SpEL 在使用变量是，会回调该方法返回变量
     * {@link StandardEvaluationContext#lookupVariable(String)}
     * @param name variable to lookup
     * @return
     */
    @Override
    @Nullable
    public Object lookupVariable(String name) {
        Object variable = super.lookupVariable(name);
        // 不为 null 就返回
        if (variable != null) {
            return variable;
        }
        // 还未加载过
        if (!this.argumentsLoaded) {
            // 加载参数
            lazyLoadArguments();
            this.argumentsLoaded = true;
            // 再次执行 就能查到了，查不到那就是真的没有
            variable = super.lookupVariable(name);
        }
        // 返回
        return variable;
    }

    /**
     * Load the param information only when needed.
     */
    protected void lazyLoadArguments() {
        // Shortcut if no args need to be loaded
        if (ObjectUtils.isEmpty(this.arguments)) {
            return;
        }

        // 方法的参数列表名称
        // Expose indexed variables as well as parameter names (if discoverable)
        String[] paramNames = this.parameterNameDiscoverer.getParameterNames(this.method);
        // 这是方法的参数列表名称
        int paramCount = (paramNames != null ? paramNames.length : this.method.getParameterCount());
        /**
         * 这个是方法执行时，传入的参数个数
         *
         * 这个是在实例化 CacheOperationContext {@link CacheAspectSupport.CacheOperationContext#CacheOperationContext(CacheAspectSupport.CacheOperationMetadata, Object[], Object)}
         * 解析方法的入参。解析入参，如果方法的最后一个参数是可变参数，会将可变参数铺平和其余入参放在数组中。
         *
         * 比如：
         * public abstract void a(String s,String... ss);
         * */
        int argsCount = this.arguments.length;
        // 遍历
        for (int i = 0; i < paramCount; i++) {
            Object value = null;
            /**
             * argsCount > paramCount 就是 方法的最后一个参数是可变参数 的情况
             * */
            if (argsCount > paramCount && i == paramCount - 1) {
                // 收集成数组
                // Expose remaining arguments as vararg array for last parameter
                value = Arrays.copyOfRange(this.arguments, i, argsCount);
            } else if (argsCount > i) {
                // Actual argument found - otherwise left as null
                value = this.arguments[i];
            }
            /**
             * 设置SpEL表达式变量
             *
             * "#a1 != null"
             * "#p1 != null"
             * "#ss != null"
             * */
            setVariable("a" + i, value);
            setVariable("p" + i, value);
            // 设置方法参数名为变量名
            if (paramNames != null && paramNames[i] != null) {
                setVariable(paramNames[i], value);
            }
        }
    }

}
