package cn.haitaoss.javaconfig.EnableScheduling;

import lombok.SneakyThrows;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-05 10:02
 * 销毁通过 @Scheduled 注册的定时任务
 */
@Import(SchedulerConfig.class)
public class TestSpringScheduler3 {

    @Scheduled(fixedDelay = 1, initialDelayString = "1", timeUnit = TimeUnit.SECONDS)
    public void task1() {
        System.out.println(LocalDateTime.now() + "--->" + Thread.currentThread().getName() + "--->执行了...Runnable");
    }

    @SneakyThrows
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestSpringScheduler3.class);
        TimeUnit.SECONDS.sleep(2);

        /*// 销毁并不会从单例池中删掉，只是会触发 销毁bean的后置处理器，从而销毁依赖该bean的 ScheduledTask
        context.getBeanFactory().destroyBean(context.getBean(TestSpringScheduler3.class));
        System.out.println("TestSpringScheduler3 销毁了，其对应的schedule task 也会被销毁...");*/

        // 销毁全部注册的定时任务，并关闭线程池
        context.getBean(ScheduledAnnotationBeanPostProcessor.class).destroy();
        System.out.println("没有活跃的线程，进程结束了");
    }
}
