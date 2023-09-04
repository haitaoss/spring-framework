package cn.haitaoss.javaconfig.SpringUtils;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;

import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 11:31
 *
 */
public class Spring_API_Environment {

    public static void main(String[] args) throws IOException {
        ApplicationContext context = new AnnotationConfigApplicationContext(Spring_API_Environment.class);

        Environment environment = context.getEnvironment();
        System.out.println(environment.getProperty("name"));
        System.out.println(environment.getProperty("${xx:default_xx}")); // 不支持的，这个API是用来读取属性的
        System.out.println(environment.resolvePlaceholders("${name:default}"));
        System.out.println(environment.resolvePlaceholders("name")); // 不支持，没有占位符就直接返回
    }
}
