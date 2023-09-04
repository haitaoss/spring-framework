package cn.haitaoss.javaconfig.PropertySource;

import lombok.Data;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 11:31
 * 模拟配置中心：通过Bean的回调方法
 */
@Component
@Data
public class Config_Center_Test2 {

    @Component
    public static class MyBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

        private ApplicationContext context;

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            // 扩展自定义的 PropertySource
            MutablePropertySources propertySources = ((ConfigurableEnvironment) context.getEnvironment()).getPropertySources();

            try {
                System.out.println("模拟从配置中心读取配置文件");
                // 加载资源文件。改成url就能实现配置中心的效果
                Resource resource = context.getResource("file:///Users/haitao/Desktop/spring-framework/spring-mytest/src/main/resources/properties/My.properties");
                ResourcePropertySource resourcePropertySource = new ResourcePropertySource(resource);
                propertySources.addLast(resourcePropertySource);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {

        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.context = applicationContext;
        }
    }

    @Value("${name}")
    private String configCenterName;

    public static void main(String[] args) throws IOException {
        GenericApplicationContext context = new AnnotationConfigApplicationContext(Config_Center_Test2.class);
        System.out.println("getConfigCenterName() = " + context.getBean(Config_Center_Test2.class).getConfigCenterName());

        // 获取属性
        System.out.println(context.getEnvironment().getProperty("name"));
    }
}