/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.io.Serializable;
import java.lang.reflect.Method;

/**
 * A Pointcut that matches if the underlying {@link CacheOperationSource}
 * has an attribute for a given method.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @since 3.1
 */
@SuppressWarnings("serial")
abstract class CacheOperationSourcePointcut extends StaticMethodMatcherPointcut implements Serializable {

    protected CacheOperationSourcePointcut() {
        setClassFilter(new CacheOperationSourceClassFilter());
    }


    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        // 拿到解析注解的东西
        CacheOperationSource cas = getCacheOperationSource();
        /**
         * 解析到注解 就是true，表示匹配。默认是 AnnotationCacheOperationSource 执行的是其父类方法
         *      {@link AbstractFallbackCacheOperationSource#getCacheOperations(Method, Class)}
         * */
        return (cas != null && !CollectionUtils.isEmpty(cas.getCacheOperations(method, targetClass)));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CacheOperationSourcePointcut)) {
            return false;
        }
        CacheOperationSourcePointcut otherPc = (CacheOperationSourcePointcut) other;
        return ObjectUtils.nullSafeEquals(getCacheOperationSource(), otherPc.getCacheOperationSource());
    }

    @Override
    public int hashCode() {
        return CacheOperationSourcePointcut.class.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + getCacheOperationSource();
    }


    /**
     * Obtain the underlying {@link CacheOperationSource} (may be {@code null}).
     * To be implemented by subclasses.
     */
    @Nullable
    protected abstract CacheOperationSource getCacheOperationSource();


    /**
     * {@link ClassFilter} that delegates to {@link CacheOperationSource#isCandidateClass}
     * for filtering classes whose methods are not worth searching to begin with.
     */
    private class CacheOperationSourceClassFilter implements ClassFilter {

        @Override
        public boolean matches(Class<?> clazz) {
            // 是 CacheManager 就不匹配
            if (CacheManager.class.isAssignableFrom(clazz)) {
                return false;
            }
            CacheOperationSource cas = getCacheOperationSource();
            // 可以理解成都是true
            return (cas == null || cas.isCandidateClass(clazz));
        }
    }

}
