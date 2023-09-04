package cn.haitaoss.javaconfig.EnableLoadTimeWeaving;

import cn.haitaoss.javaconfig.EnableLoadTimeWeaving.ajc.AjcBean;
import cn.haitaoss.javaconfig.EnableLoadTimeWeaving.dynamic.DynamicBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.EnableLoadTimeWeaving;
import org.springframework.context.weaving.AspectJWeavingEnabler;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-12-07 16:56
 *
 */

@EnableLoadTimeWeaving
@EnableAspectJAutoProxy
@ComponentScan
public class Main {

    public static void main(String[] args) throws Exception {
        /**
         * VM参数 -javaagent:/Users/haitao/Documents/study-summary/Workspace/IDEAWorkSpace/source_study/spring-framework/spring-instrument/build/libs/spring-instrument-5.3.10.jar
         * VM参数 -javaagent:D:\\workspace\\IDEA_Project\\spring-framework\\spring-instrument\\build\\libs\\spring-instrument-5.3.10.jar
         *
         * 配置文件 resources/META-INF/aop.xml
         * */
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.class) {
            @Override
            protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                /**
                 * @EnableLoadTimeWeaving 没啥用
                 * 因为添加 ClassFileTransformer 的时机太晚了，导致有些class的加载拦截不到，从而出现 Aspectj 加载期织入 失效。不过官方已经修复了，
                 * 这里我就使用蠢方法先解决了再说
                 *
                 * 在 invokeBeanFactoryPostProcessors 之前就开启 AspectJ织入 ，从而保证每个bean都能被拦截到
                 * */
                AspectJWeavingEnabler.enableAspectJWeaving(null, beanFactory.getBeanClassLoader());
            }
        };

        // xml配置文件的方式
        // ApplicationContext context = new ClassPathXmlApplicationContext("spring5.xml");

        BiFunction<Class, String, Method> methodFunction = (clazz, methodName) -> {
            Method method = null;
            try {
                method = clazz.getDeclaredMethod(methodName);
                method.setAccessible(true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            return method;
        };

        System.out.println("测试ajc实现aop 开始---->");
        Method a = methodFunction.apply(AjcBean.class, "a");
        Method a2 = methodFunction.apply(AjcBean.class, "a2");

        AjcBean ajcBean = new AjcBean();
        a.invoke(ajcBean); // 可以实现对私有方法实现 aop
        a2.invoke(ajcBean);

        System.out.println("测试ajc实现aop 结束---->");

        System.out.println("测试动态实现aop 开始---->");
        a = methodFunction.apply(DynamicBean.class, "a");
        a2 = methodFunction.apply(DynamicBean.class, "a2");
        DynamicBean bean = context.getBean(DynamicBean.class);
        a.invoke(bean);  // 不可以实现对私有方法实现 aop
        a2.invoke(bean);
        System.out.println("测试动态实现aop 结束---->");


        /**
         *  Instrumentation 是一个JVM实例 暴露出来的全局上下文对象，所有的class都会使用 Instrumentation 做处理然后再加载到JVM中
         *
         *  不同的ClassLoader 但都属于一个JVM实例，所以下面两个类的加载都会经过 Instrumentation 处理
         * */
        // context.getClassLoader().loadClass("cn.haitaoss.javaconfig.EnableLoadTimeWeaving.AService");
        // new ClassLoader() {}.loadClass("cn.haitaoss.javaconfig.EnableLoadTimeWeaving.AService");
    }
}
