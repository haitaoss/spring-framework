package cn.haitaoss.javaconfig.Lifecycle;

import org.springframework.context.Lifecycle;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-12-07 10:48
 *
 */
@Component
public class Test {
    @Component("baseLifecycle")
    public static class BaseLifecycle implements Lifecycle {
        public BaseLifecycle() {
            System.out.println(this.getClass().getSimpleName() + "...");
        }

        @Override
        public void start() {
            System.out.println(this.getClass().getSimpleName() + ".start()");
        }

        @Override
        public void stop() {
            System.out.println(this.getClass().getSimpleName() + ".stop()");
        }

        @Override
        public boolean isRunning() {
            return false;
        }
    }

    @Component("MyLifecycle2")
    public static class MyLifecycle2 extends BaseLifecycle {
    }

    @Component
    @DependsOn("MyLifecycle2") // 通过依赖的方式，触发 Lifecycle 类型的回调
    public static class MySmartLifecycle1 extends BaseLifecycle implements SmartLifecycle {
        /*@Autowired
        private Lifecycle baseLifecycle;*/

        @Override
        public boolean isAutoStartup() {
            return SmartLifecycle.super.isAutoStartup();
            // return false;
        }

        @Override
        public int getPhase() {
            // return SmartLifecycle.super.getPhase();
            return 1;
        }
    }

    @Component
    public static class MySmartLifecycle2 extends BaseLifecycle implements SmartLifecycle {
        @Override
        public boolean isAutoStartup() {
            return SmartLifecycle.super.isAutoStartup();
            // return false;
        }

        @Override
        public int getPhase() {
            // return SmartLifecycle.super.getPhase();
            return 2;
        }
    }

    public static void main(String[] args) {
        /**
         * 构造器会执行 context.refresh()
         *
         * refresh 只会触发 SmartLifecycle.isAutoStartup() == true 的bean的 Lifecycle.start()
         * */
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Test.class);
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));

        //context.start(); // 会回调所有 Lifecycle.start()
    }
}
