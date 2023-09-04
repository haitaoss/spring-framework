package cn.haitaoss.javaconfig.MessageSource;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.AbstractMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.text.MessageFormat;
import java.util.Locale;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-12-05 09:02
 * https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#context-functionality-messagesource
 */
public class Test {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setDefaultEncoding("UTF-8");
        // 不需要指定后缀，在获取指定Locale的信息时，会拼接 _zh_CN.properties 然后通过ClassLoader读取文件
        messageSource.setBasenames("i18n/f1");
        return messageSource;
    }

    public static void main(String[] args) {
        // System.out.println(Locale.getDefault());
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Test.class);
        System.out.println(context.getMessage("name", null, Locale.SIMPLIFIED_CHINESE));
        System.out.println(context.getMessage("name", null, Locale.US));
        System.out.println(context.getMessage("name", null, Locale.forLanguageTag("haitao")));
        System.out.println(context.getMessage("name", null, null));
        // 是使用 MessageFormat 来解析占位符，所以 i18n 文件，可以使用 {index} 的方式 引用参数
        System.out.println(context.getMessage("desc", new Object[]{"帅", 0.5}, null));

        /**
         * 使用举例
         * https://docs.oracle.com/javase/8/docs/api/java/text/MessageFormat.html
         * */
        System.out.println(MessageFormat.format("海涛真的{0}。手机还剩{1,number,percent}电", "帅", 0.5));

    }
    /**
     * {@link AbstractMessageSource#getMessage(String, Object[], Locale)}
     * {@link AbstractMessageSource#getMessageInternal(String, Object[], Locale)}
     * {@link ResourceBundleMessageSource#resolveCodeWithoutArguments(String, Locale)}
     * */
}
