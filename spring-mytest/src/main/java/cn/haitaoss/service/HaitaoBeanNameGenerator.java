package cn.haitaoss.service;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-01 20:48
 */
public class HaitaoBeanNameGenerator implements BeanNameGenerator {
    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        return "haitao" + definition.getBeanClassName();
    }
}
