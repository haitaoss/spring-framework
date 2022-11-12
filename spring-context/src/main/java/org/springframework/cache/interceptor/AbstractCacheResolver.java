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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * A base {@link CacheResolver} implementation that requires the concrete
 * implementation to provide the collection of cache name(s) based on the
 * invocation context.
 *
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 4.1
 */
public abstract class AbstractCacheResolver implements CacheResolver, InitializingBean {

    @Nullable
    private CacheManager cacheManager;


    /**
     * Construct a new {@code AbstractCacheResolver}.
     * @see #setCacheManager
     */
    protected AbstractCacheResolver() {
    }

    /**
     * Construct a new {@code AbstractCacheResolver} for the given {@link CacheManager}.
     * @param cacheManager the CacheManager to use
     */
    protected AbstractCacheResolver(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }


    /**
     * Set the {@link CacheManager} that this instance should use.
     */
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Return the {@link CacheManager} that this instance uses.
     */
    public CacheManager getCacheManager() {
        Assert.state(this.cacheManager != null, "No CacheManager set");
        return this.cacheManager;
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(this.cacheManager, "CacheManager is required");
    }


    @Override
    public Collection<? extends Cache> resolveCaches(CacheOperationInvocationContext<?> context) {
        /**
         * 就是拿到注解中的name，比如：@Cacheable(cacheNames = {"a", "b"})
         * {@link SimpleCacheResolver#getCacheNames(CacheOperationInvocationContext)}
         * */
        Collection<String> cacheNames = getCacheNames(context);
        if (cacheNames == null) {
            return Collections.emptyList();
        }
        Collection<Cache> result = new ArrayList<>(cacheNames.size());
        for (String cacheName : cacheNames) {
            /**
             * 使用 name 获取其 Cache
             * ConcurrentMapCacheManager 这个的特点是，拿不到Cache就会初始化一个
             *  {@link ConcurrentMapCacheManager#getCache(String)}
             *  {@link ConcurrentMapCacheManager#createConcurrentMapCache(String)}
             * */
            Cache cache = getCacheManager().getCache(cacheName);
            // 获取不到就报错
            if (cache == null) {
                throw new IllegalArgumentException("Cannot find cache named '" +
                        cacheName + "' for " + context.getOperation());
            }
            // 记录起来
            result.add(cache);
        }
        // 返回
        return result;
    }

    /**
     * Provide the name of the cache(s) to resolve against the current cache manager.
     * <p>It is acceptable to return {@code null} to indicate that no cache could
     * be resolved for this invocation.
     * @param context the context of the particular invocation
     * @return the cache name(s) to resolve, or {@code null} if no cache should be resolved
     */
    @Nullable
    protected abstract Collection<String> getCacheNames(CacheOperationInvocationContext<?> context);

}
