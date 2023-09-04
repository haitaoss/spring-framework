package cn.haitaoss.javaconfig.EnableScheduling;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.ErrorHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-05 10:02
 * 配置任务调度器
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
    //    @Bean
    public SchedulingConfigurer schedulingConfigurer() {
        return new SchedulingConfigurer() {
            @Override
            public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
                // 可以通过这个扩展要执行的 Task
                taskRegistrar.addFixedRateTask(null);
                taskRegistrar.addFixedDelayTask(null);
                taskRegistrar.addCronTask(null);
                taskRegistrar.addTriggerTask(null);

                /**
                 * 配置 调度器，用来调度 taskRegistrar 里面记录的task。设置了这个 下面@Bean注册的就没用了
                 * 只支持：ScheduledExecutorService、TaskScheduler 两种类型
                 * */
                taskRegistrar.setScheduler(null);
            }
        };
    }

    /**
     * {@link ScheduledAnnotationBeanPostProcessor#DEFAULT_TASK_SCHEDULER_BEAN_NAME} -> taskScheduler
     *
     * 查找顺序 ：TaskScheduler -> ScheduledExecutorService
     * 注：都是byName 'taskScheduler' 找，找不到在byType找。找不到或者匹配多个不会报错只会导致 taskScheduler 是 null而已，后面会设置默认值
     *      this.localExecutor = Executors.newSingleThreadScheduledExecutor();
     *      this.taskScheduler = new ConcurrentTaskScheduler(this.localExecutor);
     * */
    @Bean
    public TaskScheduler taskScheduler() {
        ConcurrentTaskScheduler concurrentTaskScheduler = new ConcurrentTaskScheduler(scheduledExecutorService());
        // 错误处理器，定时任务抛出了异常的处理操作
        concurrentTaskScheduler.setErrorHandler(new ErrorHandler() {
            @Override
            public void handleError(Throwable t) {
                System.err.println("执行任务出问题了，错误信息：" + t.getMessage());

                // 抛出异常就会中断任务
                // throw new RuntimeException(t.getMessage());
            }
        });
        return concurrentTaskScheduler;
    }

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(20);
    }

}
