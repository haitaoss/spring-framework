package cn.haitaoss.javaconfig.circular;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-04 21:47
 * spring 为了解决单例bean循环依赖问题，是才用 提请 AOP 的方式 来解决的，
 * 提前AOP是执行
 *      @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference(java.lang.Object, java.lang.String)
 * 然后在bean的生命周期的最后阶段会执行 org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization(java.lang.Object, java.lang.String)
 * 也可能会返回代理对象。所以就可能出现 postProcessAfterInitialization 创建的代理对象和一开始提前AOP注入给其他bean的不一样
 * 所以只能报错了。
 *
 * 解决方式：
 * 1. 将 postProcessAfterInitialization 的代理逻辑放到 SmartInstantiationAwareBeanPostProcessor#getEarlyBeanReference 实现
 * 2. 使用 @Lazy 注解，不要在初始化的时间就从容器中获取bean，而是直接返回一个代理对象
 * 3. 使用 @Lookup
 */
public class Test {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
        System.out.println(context.getBean("AService"));
    }
}

@Configuration
@ComponentScan
class Config {}

@Component
class MyBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return getObject(bean);
    }

    /*@Override
    public Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
        // TODOHAITAO: 2022/9/4 解决方式一
        return getObject(bean);
    }*/

    private Object getObject(Object bean) {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(bean);
        return proxyFactory.getProxy();
    }
}

@Component
class AService {
    @Autowired
    BService bService;

    @Async
    public void test() {

    }
}

@Component
class BService {
    @Autowired
    @Lazy
    // TODOHAITAO: 2022/9/4 解决方式二
    AService aService;

    @Lookup
    // TODOHAITAO: 2022/9/4 解决方式三
    public AService aService() {
        return null;
    }
}
