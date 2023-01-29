package cn.haitaoss.javaconfig.EnableLoadTimeWeaving;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-12-07 16:56
 *
 */
// 添加 ClassFileTransformer 的时机太晚了，导致有些class的加载拦截不到，从而出现 Aspectj 加载期织入 失效
@EnableLoadTimeWeaving
@Component
@ComponentScan
public class Test {
    public void a() {
        System.out.println("Test.a...");
    }

    @Component
    public static class Bean {
        public void a() {
            System.out.println("Bean.a...");
        }
    }

    @Aspect
    public static class ProfilingAspect {

        @DeclareParents(value = "cn.haitaoss.javaconfig.EnableLoadTimeWeaving.*", defaultImpl = LogAdvice.class)
        private Runnable advice;

        @Around("execution(public * cn.haitaoss..*(..))")
        public Object profile(ProceedingJoinPoint pjp) throws Throwable {
            System.out.println("<----ProfilingAspect.before---->");
            Object proceed = pjp.proceed();
            System.out.println("<----ProfilingAspect.after---->");
            return proceed;
        }

        public static class LogAdvice implements Runnable {

            @Override
            public void run() {
                System.out.println("invoke...");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // VM参数 javaagent:/Users/haitao/Desktop/spring-framework/spring-instrument/build/libs/spring-instrument-5.3.10.jar
        // Java配置类的方式
        ApplicationContext context = new AnnotationConfigApplicationContext(Test.class) {
            @Override
            protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                // 在 invokeBeanFactoryPostProcessors 之前就开启 AspectJ织入 ，从而保证每个bean都能被拦截到
                AspectJWeavingEnabler.enableAspectJWeaving(null, beanFactory.getBeanClassLoader());
            }
        };
        // xml配置文件的方式
        //        ApplicationContext context = new ClassPathXmlApplicationContext("spring5.xml");

        System.out.println("=====");
        context.getBean(Test.class)
                .a();
        context.getBean(Bean.class)
                .a();
        System.out.println("=====");
        new Test().a();
        new Bean().a();
        System.out.println("=====");
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));

        /**
         *  Instrumentation 是一个JVM实例 暴露出来的全局上下文对象，所有的class都会使用 Instrumentation 做处理然后再加载到JVM中
         *
         *  不同的ClassLoader 但都属于一个JVM实例，所以下面两个类的加载都会经过 Instrumentation 处理
         * */
        context.getClassLoader()
                .loadClass("cn.haitaoss.javaconfig.EnableLoadTimeWeaving.AService");
        new ClassLoader() {}.loadClass("cn.haitaoss.javaconfig.EnableLoadTimeWeaving.AService");
    }


}
