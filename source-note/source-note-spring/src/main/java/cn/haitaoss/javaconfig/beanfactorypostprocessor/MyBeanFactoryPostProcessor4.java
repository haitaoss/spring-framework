package cn.haitaoss.javaconfig.beanfactorypostprocessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

/**
 * BeanFactoryPostProcessor 会在 BeanFactory 创建完之后执行，
 *
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 11:17
 */
public class MyBeanFactoryPostProcessor4 implements BeanDefinitionRegistryPostProcessor {

    /**
     * 修改 或者 注册 BeanDefinition
     *
     * @throws BeansException
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        System.out.println("MyBeanFactoryPostProcessor4#postProcessBeanDefinitionRegistry");
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        System.out.println("MyBeanFactoryPostProcessor4#postProcessBeanFactory");
    }
}