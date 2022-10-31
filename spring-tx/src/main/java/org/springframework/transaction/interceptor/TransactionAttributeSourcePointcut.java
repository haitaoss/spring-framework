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

package org.springframework.transaction.interceptor;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionManager;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * Abstract class that implements a Pointcut that matches if the underlying
 * {@link TransactionAttributeSource} has an attribute for a given method.
 *
 * @author Juergen Hoeller
 * @since 2.5.5
 */
@SuppressWarnings("serial")
abstract class TransactionAttributeSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {

    protected TransactionAttributeSourcePointcut() {
        /**
         * TransactionAttributeSourceClassFilter 会执行抽象方法 `getTransactionAttributeSource` 然后执行 {@link TransactionAttributeSource#isCandidateClass(Class)}
         * 判断类是否匹配。
         *
         * 注：过滤规则很简单，只要类不是java包下的 不是 Ordered接口 就是匹配
         * */
        setClassFilter(new TransactionAttributeSourceClassFilter());
    }


    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        /**
         * TransactionAttributeSourcePointcut 实现了 MethodMatcher，所以判断方法匹配的时候会执行当前方法。
         * 会执行抽象方法 `getTransactionAttributeSource` 然后执行 {@link TransactionAttributeSource#getTransactionAttribute(Method, Class)}
         *
         * {@link AbstractFallbackTransactionAttributeSource#getTransactionAttribute(Method, Class)}
         * 注：过滤规则很简单，方法 -> 方法声明的类 先找到@Transactional就返回。也就是有注解就是匹配
         * */
        TransactionAttributeSource tas = getTransactionAttributeSource();
        return (tas == null || tas.getTransactionAttribute(method, targetClass) != null);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransactionAttributeSourcePointcut)) {
            return false;
        }
        TransactionAttributeSourcePointcut otherPc = (TransactionAttributeSourcePointcut) other;
        return ObjectUtils.nullSafeEquals(getTransactionAttributeSource(), otherPc.getTransactionAttributeSource());
    }

    @Override
    public int hashCode() {
        return TransactionAttributeSourcePointcut.class.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + getTransactionAttributeSource();
    }


    /**
     * Obtain the underlying TransactionAttributeSource (may be {@code null}).
     * To be implemented by subclasses.
     */
    @Nullable
    protected abstract TransactionAttributeSource getTransactionAttributeSource();


    /**
     * {@link ClassFilter} that delegates to {@link TransactionAttributeSource#isCandidateClass}
     * for filtering classes whose methods are not worth searching to begin with.
     */
    private class TransactionAttributeSourceClassFilter implements ClassFilter {

        @Override
        public boolean matches(Class<?> clazz) {
            if (TransactionalProxy.class.isAssignableFrom(clazz) ||
                    TransactionManager.class.isAssignableFrom(clazz) ||
                    PersistenceExceptionTranslator.class.isAssignableFrom(clazz)) {
                return false;
            }
            TransactionAttributeSource tas = getTransactionAttributeSource();
            return (tas == null || tas.isCandidateClass(clazz));
        }
    }

}
