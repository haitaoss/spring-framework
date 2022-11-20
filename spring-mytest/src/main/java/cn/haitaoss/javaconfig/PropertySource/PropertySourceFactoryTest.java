package cn.haitaoss.javaconfig.PropertySource;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 11:31
 * 使用 PropertySourceFactory 解析yml格式的文件
 */
@PropertySource(value = {
        "properties/My3.properties",
        "properties/My4.yaml",
        "properties/My5.yml"},
        factory = PropertySourceFactoryTest.MyPropertySourceFactory.class)
@Component
public class PropertySourceFactoryTest {
    public static class MyPropertySourceFactory extends DefaultPropertySourceFactory {
        @Override
        public org.springframework.core.env.PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
            String filename = resource.getResource().getFilename();
            if (filename.endsWith(".yml") || filename.endsWith(".yaml")) {
                // 解析 yml 的工具类
                YamlPropertiesFactoryBean yamlPropertiesFactoryBean = new YamlPropertiesFactoryBean();
                yamlPropertiesFactoryBean.setResources(resource.getResource());
                Properties yamlPropertiesFactoryBeanObject = yamlPropertiesFactoryBean.getObject();

                // 将 Properties 构造成 ProperSource 对象
                return new PropertiesPropertySource(StringUtils.hasText(name) ? name : filename,
                        yamlPropertiesFactoryBeanObject);
            }
            // 解析 properties 文件
            return super.createPropertySource(name, resource);
        }
    }

    public static void main(String[] args) throws IOException {
        ApplicationContext context = new AnnotationConfigApplicationContext(PropertySourceFactoryTest.class);
        Environment environment = context.getEnvironment();
        System.out.println(environment.getProperty("name"));
        System.out.println(environment.getProperty("yml_name"));
        System.out.println(environment.getProperty("yaml_name"));
    }
}