package cn.haitaoss;


import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.InjectionMetadata;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ContextAnnotationAutowireCandidateResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {
    public static void main(String[] args) throws Exception {
        // ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("spring.xml");
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        System.out.println(context.getBean(cn.haitaoss.javaconfig.Lazy.Test.class)
                .getX());

        /**
         * 创建bean
         * {@link AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])}
         * {@link AbstractAutowireCapableBeanFactory#doCreateBean(String, RootBeanDefinition, Object[])}
         *
         * 填充bean
         * {@link AbstractAutowireCapableBeanFactory#populateBean(String, RootBeanDefinition, BeanWrapper)}
         *
         * 后置处理器 解析属性值
         * {@link AutowiredAnnotationBeanPostProcessor#postProcessProperties(PropertyValues, Object, String)}
         *      {@link InjectionMetadata#inject(Object, String, PropertyValues)}
         *      {@link AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#inject(Object, String, PropertyValues)}
         *      {@link AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#resolveFieldValue(Field, Object, String)}
         *      {@link DefaultListableBeanFactory#resolveDependency(DependencyDescriptor, String, Set, TypeConverter)}
         *          判断是否是 @Lazy 有就创建代理对象 {@link ContextAnnotationAutowireCandidateResolver#getLazyResolutionProxyIfNecessary(DependencyDescriptor, String)}
         *              {@link ContextAnnotationAutowireCandidateResolver#isLazy(DependencyDescriptor)}
         *              {@link ContextAnnotationAutowireCandidateResolver#buildLazyResolutionProxy(DependencyDescriptor, String)}
         *                  创建代理对象，两种策略 cglib 或者 jdk {@link ProxyFactory#getProxy(ClassLoader)}
         *                      cglib 的代理逻辑是这个 {@link CglibAopProxy.DynamicAdvisedInterceptor#intercept(Object, Method, Object[], MethodProxy)}
         *                      jdk 的代理逻辑是这个 {@link JdkDynamicAopProxy#invoke(Object, Method, Object[])}
         *
         * 将解析的属性值设置到bean中 {@link AbstractAutowireCapableBeanFactory#applyPropertyValues(String, BeanDefinition, BeanWrapper, PropertyValues)}
         * */
        /**
         *
         * 属性注入的时候 注入的是代理对象 {@link ContextAnnotationAutowireCandidateResolver#getLazyResolutionProxyIfNecessary(DependencyDescriptor, String)}
         * */
        /**
         * @EnableAspectJAutoProxy
         *
         * @Import(AspectJAutoProxyRegistrar.class)
         *
         * 会默认注入 {@link AnnotationAwareAspectJAutoProxyCreator } 继承树，可以看到该类实现的 SmartInstantiationAwareBeanPostProcessor 所以可以在bean创建的声明周期进行拦截
         *  SmartInstantiationAwareBeanPostProcessor
         *      AbstractAutoProxyCreator
         *          AbstractAdvisorAutoProxyCreator
         *              AspectJAwareAdvisorAutoProxyCreator
         *                  AnnotationAwareAspectJAutoProxyCreator
         *
         *
         * */

        /**
         依赖解析忽略
         此部分设置哪些接口在进行依赖注入的时候应该被忽略:

         beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
         beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
         beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
         beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
         beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
         bean伪装
         有些对象并不在BeanFactory中，但是我们依然想让它们可以被装配，这就需要伪装一下:

         beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
         beanFactory.registerResolvableDependency(ResourceLoader.class, this);
         beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
         beanFactory.registerResolvableDependency(ApplicationContext.class, this);
         伪装关系保存在一个Map<Class<?>, Object>里。
         * */


    }
}
