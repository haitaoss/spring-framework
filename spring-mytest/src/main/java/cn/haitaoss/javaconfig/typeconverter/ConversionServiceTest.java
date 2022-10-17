package cn.haitaoss.javaconfig.typeconverter;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-07 14:57
 */
@Component
public class ConversionServiceTest {


    @Bean
    public A a() {
        return new A();
    }

    @Data
    public static class A {
        private String name;

        @Value("2022-08-11")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        private Date date/* = new Date()*/;

        @Value("101.11")
        @NumberFormat(pattern = "#")
        private Integer money;

        @Value("code,play")
        private String[] jobs;

        @Autowired
        public void x(@Value("hello world!!!") A a) {
            System.out.println(a);
        }
    }

    @Bean// 名字必须是 conversionService 因为在依赖注入的时候是通过这个名字拿的
    public static ConversionService conversionService() {
        // DefaultFormattingConversionService 功能强大： 类型转换 + 格式化
        DefaultFormattingConversionService defaultFormattingConversionService = new DefaultFormattingConversionService();
        defaultFormattingConversionService.addConverter(new Converter<String, A>() {
            @Override
            public A convert(String source) {
                A a = new A();
                a.setName(source);
                return a;
            }
        });

        return defaultFormattingConversionService;
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ConversionServiceTest.class);
        System.out.println(context.getBean(A.class));
    }

}