package cn.haitaoss.javaconfig.EnableAsync;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-06 17:33
 *
 */
@EnableAsync(mode = AdviceMode.PROXY)
@Component
@Async("executor")
public class EnableAsyncTest {
    //    @Async
    public void asyncTask() {
        System.out.println("EnableAsyncTest.asyncTask--->" + Thread.currentThread().getName());
    }

    //    @Async("executor")
    public void asyncTask2() {
        System.out.println("EnableAsyncTest.asyncTask2--->" + Thread.currentThread().getName());
    }

    @Component
    public static class Config {
        @Bean
        public Executor executor() {
            return Executors.newFixedThreadPool(1, r -> {
                Thread thread = new Thread(r);
                thread.setName("executor-" + Math.random());
                return thread;
            });
        }

        // @Bean
        public AsyncConfigurer asyncConfigurer() {
            return new AsyncConfigurer() {
                @Override
                public Executor getAsyncExecutor() {
                    return Executors.newFixedThreadPool(1, r -> {
                        Thread thread = new Thread(r);
                        thread.setName("asyncConfigurer-" + Math.random());
                        return thread;
                    });
                }

                @Override
                public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                    return new SimpleAsyncUncaughtExceptionHandler();
                }
            };
        }
    }


    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableAsyncTest.class);
        EnableAsyncTest bean = context.getBean(EnableAsyncTest.class);
        bean.asyncTask();
        bean.asyncTask2();
        TimeUnit.SECONDS.sleep(1000);
    }


}
