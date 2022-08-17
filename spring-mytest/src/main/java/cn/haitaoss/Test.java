package cn.haitaoss;


import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        ConfigurableEnvironment environment = context.getEnvironment();

        System.out.println(environment.getSystemProperties()); // 系统环境变量

        System.out.println(environment.getSystemEnvironment());// jvm 环境变量

        System.out.println(environment.getPropertySources());// 属性文件


        //  不需要关系，变量属于 操作系统、JVM、属性文件，直接可以通过下面的方式获取
        System.out.println(environment.getProperty("test"));
    }
}