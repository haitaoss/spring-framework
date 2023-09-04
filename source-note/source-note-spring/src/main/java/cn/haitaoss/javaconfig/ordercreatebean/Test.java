package cn.haitaoss.javaconfig.ordercreatebean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-04 21:03
 * 如何实现bean创建的优先级：
 * 1. 实现 BeanFactoryPostProcessor（缺点：破坏了bean的生命周期）
 * 2. 重写 cn.haitaoss.javaconfig.ordercreatebean.MySmartInstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation(java.lang.Class, java.lang.String)
 *  这个是 BeanPostProcessor 中最先执行的回调方法，其他的BeanPostProcessor 也可以
 * 3. 重写 onRefresh 方法，通过发布并消费早期事件
 *      cn.haitaoss.javaconfig.applicationlistener.MyAnnotationConfigApplicationContext#onRefresh()
 * 4. 使用 @DependsOn("b")
 */
public class Test {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext() {
            @Override
            protected void onRefresh() throws BeansException {
                publishEvent(new MyApplicationEvent("beanDefinition 全部加载完了，可以自定义bean加载顺序了") {
                    @Override
                    public Object getSource() {
                        return super.getSource();
                    }
                });
            }
        };

        context.register(Config.class);
        context.refresh();

        System.out.println("====");
        System.out.println(context.getBean("d"));
        System.out.println(context.getBean("d"));
        System.out.println(context.getBean("d"));
    }
}


@ComponentScan
@DependsOn("b") // 1. 使用 DependsOn 实现最先被创建的bean
class Config {
    public Config() {
        System.out.println("Config create...");
    }

    @Bean
    public C c() {
        return new C();
    }
}

@Component
class A {
    public A() {
        System.out.println("A create...");
    }
}

@Component
class B {
    public B() {
        System.out.print("DependsOn--->");
        System.out.println("B create...");
    }
}

class C {
    public C() {
        System.out.println("C create...");
    }
}

@Component
class D {
    public D() {
        System.out.println("D create...");
    }
}

@Component
class TestPreCreate {
    public TestPreCreate() {
        System.out.println("TestPreCreate...");
    }
}

@Component
class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    public MyBeanFactoryPostProcessor() {
        System.out.println("BeanFactoryPostProcessor....");
    }

    /**
     * 通过这个可以实现 bean创建的优先级
     * 坏处是打破了 bean的生命周期，因为这时候 beanPostProcessor 还没有实例化，所以bean在创建的过程中不会被 beanPostProcessor 处理
     * @see AbstractApplicationContext#refresh()
     *
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        System.out.print("BeanFactoryPostProcessor--->");
        beanFactory.getBean("testPreCreate");
    }
}

@Component
class MyApplicationListener implements ApplicationListener<MyApplicationEvent>, ApplicationContextAware {
    private ApplicationContext applicationContext;

    public MyApplicationListener() {
        System.out.println("MyApplicationListener....");
    }

    @Override
    public void onApplicationEvent(MyApplicationEvent event) {
        System.out.print("ApplicationListener--->");
        applicationContext.getBean("a");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

class MyApplicationEvent extends ApplicationEvent {
    private static final long serialVersionUID = 7099057708183571937L;

    public MyApplicationEvent(Object source) {
        super(source);
    }

}

@Component
class MySmartInstantiationAwareBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor, ApplicationContextAware {
    ApplicationContext applicationContext;

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        String d = "d";

        if (d.equals(beanName)) {
            return null;
        }
        // 提前创建bean
        System.out.print("SmartInstantiationAwareBeanPostProcessor--->");
        applicationContext.getBean(d);
        return null;
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}