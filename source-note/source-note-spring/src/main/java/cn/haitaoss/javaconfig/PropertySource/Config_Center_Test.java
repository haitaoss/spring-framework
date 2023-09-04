package cn.haitaoss.javaconfig.PropertySource;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
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
 * 模拟配置中心：重写 AbstractApplicationContext#initPropertySources 的方式
 */
@Component
@Data
public class Config_Center_Test {
    @Value("${name}")
    private String configCenterName;

    public static void main(String[] args) throws IOException {
        GenericApplicationContext context = new AnnotationConfigApplicationContext(Config_Center_Test.class) {
            @Override
            protected void initPropertySources() {
                // 会初始化environment属性，默认是这个类型的 StandardEnvironment
                ConfigurableEnvironment environment = getEnvironment();

                // 设置必要的属性，在refresh阶段校验 environment 不存在这些属性，就直接报错
                environment.setRequiredProperties("name");

                // 拿到 environment 存储属性的对象
                MutablePropertySources propertySources = environment.getPropertySources();

                try {
                    System.out.println("模拟从配置中心读取配置文件");
                    // 加载资源文件。改成url就能实现配置中心的效果
                    Resource resource = getResource("file:///Users/haitao/Desktop/spring-framework/spring-mytest/src/main/resources/properties/My.properties");
                    ResourcePropertySource resourcePropertySource = new ResourcePropertySource(resource);
                    propertySources.addLast(resourcePropertySource);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        System.out.println("context.getBean(Config_Center_Test.class).getConfigCenterName() = " + context.getBean(Config_Center_Test.class).getConfigCenterName());

        // 获取属性
        System.out.println(context.getEnvironment().getProperty("name"));
    }
}