package cn.haitaoss.javaconfig.PropertySource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 11:31
 * 基本用法
 */
@PropertySource(
        // 这四种写法没有区别，都是会移除前缀 classpath: ，移除路径开头的 / ，然后使用 ClassLoader 获取资源
        // value = {"classpath:properties/My.properties", "classpath:properties/My2.properties"}
        // value = {"classpath:/properties/My.properties", "classpath:/properties/My2.properties"}
        // value = {"/properties/My.properties", "/properties/My2.properties"}
        value = {"properties/My.properties", "properties/My2.properties", "properties/My3.properties"}

        // 可以写占位符，占位符的值是资源文件的路径
        //        value = "${config_file}"

        // 不支持 classpath* 因为用的资源解析器是解析单个的
        //        value = {"classpath*:properties/My.properties", "classpath*:properties/My2.properties"}
)
//@PropertySources(value = {@PropertySource("")})
@ImportResource("spring4.xml")
@Component
public class PropertySourceTest {
    @Value("${namexxx:default_name}") // 占位符的值，就是通过属性获取的
    public String name;

    public static void main(String[] args) throws IOException {
        ApplicationContext context = new AnnotationConfigApplicationContext(PropertySourceTest.class);
        System.out.println(context.getBean(PropertySourceTest.class).name);
        Environment environment = context.getEnvironment();
        // 获取属性
        System.out.println(environment.getProperty("name"));
        // 解析占位符
        System.out.println(environment.resolvePlaceholders("${name}"));
    }
}