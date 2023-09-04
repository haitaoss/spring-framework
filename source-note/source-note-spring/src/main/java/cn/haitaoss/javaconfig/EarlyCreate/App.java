package cn.haitaoss.javaconfig.EarlyCreate;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-12 13:12
 */
@EarlyCreate("app")
public class App {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                App.class);
    }
}
