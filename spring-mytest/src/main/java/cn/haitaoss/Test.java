package cn.haitaoss;


import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        // Spring 提供便利的资源加载方式（本地文件资源、网络资源）
        Resource resource = context.getResource("/Users/haitao/Desktop/spring-framework/spring-mytest/src/main/java/cn/haitaoss/AppConfig.java");
        System.out.println(resource.contentLength());

        Resource resource1 = context.getResource("https://www.baidu.com");
        System.out.println(resource1.contentLength());
        System.out.println(resource1.getURL());


        Resource resource2 = context.getResource("classpath:spring.xml");
        System.out.println(resource2.contentLength());
        System.out.println(resource2.getURL());
        /*// 或者通过 MessageSourceAware 接口把 MessageSource 注入到bean中
        System.out.println(context.getMessage("name", null, new Locale("en")));*/

    }
}