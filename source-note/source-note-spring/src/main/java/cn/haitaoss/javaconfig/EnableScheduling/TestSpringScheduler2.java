package cn.haitaoss.javaconfig.EnableScheduling;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-05 10:02
 * `@Scheduled`的使用：定时任务、周期任务、延时任务
 */
@Import(SchedulerConfig.class)
public class TestSpringScheduler2 {
    @Scheduled(cron = "*/1 * * * * ?") // [秒] [分] [时] [日期] [月] [星期] 好像采用的是延时执行
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.SECONDS)
    @Scheduled(fixedDelay = 1, initialDelay = 1)
    @Scheduled(fixedDelay = 1, initialDelayString = "1")
    /**
     *  @Scheduled(initialDelay = 1, initialDelayString = "", cron = "", fixedDelay = 1, fixedDelayString = "", fixedRate = 1, fixedRateString = "", timeUnit = TimeUnit.MILLISECONDS)
     *  1. initialDelayString、fixedDelayString、fixedRateString、cron 表示支持表达式
     *  2. initialDelay、initialDelayString 设置任务启动时延时时间，这两个属性是互斥的只能设置一个
     *  3. cron、fixedDelay、fixedDelayString、fixedRate、fixedRateString 是互斥的只能设置一个。也就是说明一个@Scheduled只能解析成一个Task
     *  4. timeUnit 时间单位。初始延时时间、固定延时、固定频率 的时间会使用这个单位计算
     * */
    public void task1() {
        System.out.println(LocalDateTime.now() + "--->" + Thread.currentThread().getName() + "--->执行了...Runnable");
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException e) {

        }
        throw new RuntimeException("模拟任务出错");
    }

    public static void main(String[] args) {
        new AnnotationConfigApplicationContext(TestSpringScheduler2.class);
    }
}
