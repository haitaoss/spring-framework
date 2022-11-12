package cn.haitaoss.javaconfig.bean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-10 14:30
 *
 */
@ComponentScan
public class BeanLifecycle {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(BeanLifecycle.class);
        // 会回调销毁bean的后置处理器，并不会从单例池中移除
        context.getBeanFactory().destroyBean(context.getBean(A.class));

        // 会回调销毁bean的后置处理器，会从Scope中移除bean
        context.getBeanFactory().destroyScopedBean("a"); // 销毁 scope bean

        // 会回调销毁bean的后置处理器，会从单例池中移除
        DefaultListableBeanFactory.class.cast(context.getBeanFactory()).destroySingleton("a"); // 销毁单例bean
        context.getBeanFactory().destroySingletons(); // 销毁全部单例bean
    }
}

@Component
class MyDestructionAwareBeanPostProcessor implements DestructionAwareBeanPostProcessor {

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
        System.out.println("------>" + beanName);
    }
}

@Component
class A {
}

@Component
class B {
}