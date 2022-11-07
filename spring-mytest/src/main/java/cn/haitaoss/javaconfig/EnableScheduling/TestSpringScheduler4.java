package cn.haitaoss.javaconfig.EnableScheduling;

import lombok.SneakyThrows;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-05 10:02
 *  动态注册、销毁定时任务
 */
@Import(SchedulerConfig.class)
public class TestSpringScheduler4 implements SchedulingConfigurer {
    private ScheduledTaskRegistrar taskRegistrar;
    private final ConcurrentHashMap<String, ScheduledTask> taskMap = new ConcurrentHashMap<>();

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 暴露出来
        this.taskRegistrar = taskRegistrar;
    }

    public void addTask(String key, CronTask cronTask) {
        ScheduledTask scheduledTask = taskRegistrar.scheduleCronTask(cronTask);
        taskMap.put(key, scheduledTask);
    }

    public void destoryTask(String key) {
        taskMap.remove(key).cancel();
    }


    @SneakyThrows
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestSpringScheduler4.class);
        TestSpringScheduler4 bean = context.getBean(TestSpringScheduler4.class);

        Runnable run = () -> System.out.println(LocalDateTime.now() + "--->" + Thread.currentThread().getName() + "--->执行了...Runnable");
        String t1 = "t1";
        bean.addTask(t1, new CronTask(run, "*/1 * * * * ?"));

        TimeUnit.SECONDS.sleep(2);

        bean.destoryTask(t1);
        System.out.println(String.format("中断：%S 任务", t1));
    }
}
