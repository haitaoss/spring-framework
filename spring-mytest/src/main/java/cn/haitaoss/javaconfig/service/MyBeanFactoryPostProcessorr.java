package cn.haitaoss.javaconfig.service;

import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 22:20
 *
 */
@Component
public class MyBeanFactoryPostProcessorr implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        registry.registerBeanDefinition("userSupplier", BeanDefinitionBuilder.genericBeanDefinition(User.class, new Supplier<User>() {
                    @Override
                    public User get() {
                        System.out.println("Supplier----> 使用这种方式的好处是，会对bean进行属性注入");
                        // 对比 FactoryBean 个 getObject
                        return new User();
                    }
                })
                .getBeanDefinition());
    }
}

@Data
class User {
    @Value("123")
    private String msg;


    @Override
    public String toString() {
        return "User{" + "msg='" + msg + '\'' + '}';
    }
}

@Component
class MyFactoryBean implements FactoryBean<User> {
    @Override
    public User getObject() throws Exception {
        return new User();
    }

    @Override
    public Class<?> getObjectType() {
        return User.class;
    }
}

