package cn.haitaoss.javaconfig.beanfactorypostprocessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 11:17
 */
@Component
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {
    /**
     * 通过这个可以实现 bean创建的优先级
     * 坏处是打破了 bean的生命周期，因为这时候 beanPostProcessor 还没有实例化，所以bean在创建的过程中不会被 beanPostProcessor 处理
     * @see AbstractApplicationContext#refresh()
     *
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        System.out.println("MyBeanFactoryPostProcessor2.postProcessBeanFactory...");
        System.out.println("可以通过这种方式 提前创建bean");
        beanFactory.getBean("testPreCreate");
    }
}
