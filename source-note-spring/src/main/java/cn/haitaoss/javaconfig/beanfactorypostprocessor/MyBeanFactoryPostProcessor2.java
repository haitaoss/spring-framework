package cn.haitaoss.javaconfig.beanfactorypostprocessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

/**
 * BeanFactoryPostProcessor 会在 BeanFactory 创建完之后执行，
 *
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 11:17
 */
@Component
public class MyBeanFactoryPostProcessor2 implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
    }

    /**
     * 修改 或者 注册 BeanDefinition
     *
     * @throws BeansException
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        System.out.println("MyBeanFactoryPostProcessor2.postProcessBeanDefinitionRegistry...");
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition()
                .getBeanDefinition();
        beanDefinition.setBeanClass(Test1.class);

        registry.registerBeanDefinition("test1", beanDefinition);
    }
}