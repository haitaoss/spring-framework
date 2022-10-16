package cn.haitaoss.javaconfig.EnableAsync;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurationSelector;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-06 17:33
 *
 */
@EnableAsync
@Component
public class EnableAsyncTest {
    /**
     * 使用`@EnableAsync`会发生什么?
     *      会导入`@Import(AsyncConfigurationSelector.class)`
     *
     * {@link AsyncConfigurationSelector}
     *
     * */
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableAsyncTest.class);
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));
        EnableAsyncTest bean = context.getBean(EnableAsyncTest.class);
        for (int i = 0; i < 10; i++) {
            bean.asyncTask();
        }
        System.out.println("任务发起完了");

        TimeUnit.SECONDS.sleep(1000);
    }


    @Async
    public void asyncTask() {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("EnableAsyncTest.asyncTask--->");
    }
}
