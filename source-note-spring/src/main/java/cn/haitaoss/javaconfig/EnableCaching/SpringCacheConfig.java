package cn.haitaoss.javaconfig.EnableCaching;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-07 20:17
 *
 */
@EnableCaching
public class SpringCacheConfig {
    //    @Bean
    public CachingConfigurer cachingConfigurer() {
        return new CachingConfigurerSupport() {
            @Override
            public CacheManager cacheManager() {
                return concurrentMapCacheManager();
            }
        };
    }

    //    @Bean
    public CacheManager concurrentMapCacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        return cacheManager;
    }

    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        return caffeineCacheManager;
    }
}
