package cn.haitaoss;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class CacheTest {

//    @Test
    public void timedCachetest() {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        Cache<String, Thing> cache = Caffeine.newBuilder()
                .maximumSize(10)
                // 访问后失效，就是访问的时候看看时间是不是过了有效期，过了就返回null表示失效了
                .expireAfterAccess(10, TimeUnit.MILLISECONDS)
                // 移出缓存内容的时候(比如缓存失效了)，会触发监听器
                .removalListener(new RemovalListener<Object, Object>() {
                    @Override
                    public void onRemoval(@Nullable Object key, @Nullable Object value, @NonNull RemovalCause cause) {
                        System.out.println("触发监听器---->");
                    }
                })
                .build();

        String key = "key";
        Thing thing = new Thing(key);
        cache.put(key, thing);
        try {
            // 延时 10秒之后获取
            //            while (true) {

            //            }
            for (int i = 0; i < 10; i++) {
                TimeUnit.SECONDS.sleep(1);
                System.out.println(cache.asMap().size());
                // System.out.println(cache.asMap().toString()); // 这样子也会触发监听器
                System.out.println(cache.asMap().hashCode()); // 这样子也会触发监听器
            }
            //            scheduledExecutorService.schedule(() -> System.out.println(cache.getIfPresent(key) + "尝试获取值--->"), 3, TimeUnit.SECONDS);
            System.out.print("尝试获取值--->");
            cache.getIfPresent(key);
            for (int i = 0; i < 100000; i++) {
                TimeUnit.SECONDS.sleep(1);
                System.out.println(cache.asMap().size());
            }
            // cache.invalidateAll();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


    }

    class Thing {
        public Thing(String key) {
            this.name = key;
            this.value = key.hashCode();
        }

        public String getName() {
            return name;
        }

        public Integer getValue() {
            return value;
        }

        private String name;
        private Integer value;
    }
}