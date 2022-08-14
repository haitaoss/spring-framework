package cn.haitaoss;


import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Locale;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        // 或者通过 MessageSourceAware 接口把 MessageSource 注入到bean中
        System.out.println(context.getMessage("name", null, new Locale("en")));

    }
}