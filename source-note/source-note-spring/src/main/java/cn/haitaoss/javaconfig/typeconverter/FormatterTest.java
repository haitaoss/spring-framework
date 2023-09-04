package cn.haitaoss.javaconfig.typeconverter;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.format.datetime.DateFormatterRegistrar;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.number.NumberFormatAnnotationFormatterFactory;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;

import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;

@Data
public class FormatterTest {
    @NumberFormat(style = NumberFormat.Style.PERCENT)
    @Value("56%")
    private double d;

    @DateTimeFormat(pattern = "yyyyMM")
    @Value("202211")
    private Date date;

    @DateTimeFormat(pattern = "yyyyMM")
    @Value("202211")
    private Calendar cal;

    @DateTimeFormat(pattern = "yyyyMM")
    @Value("202211")
    private Long l;

    @Value("20221205")
    private Date defaultDate;

    //@Bean("conversionService")
    public static FormattingConversionService conversionService() {
        /**
         * 实例化DefaultFormattingConversionService，构造器参数传 false
         *
         * 默认是会注册默认的 其实就是执行下面的代码，但是我们想自定义格式化样式，所以传 false，然后写注册代码
         * */
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService(false);

        // 支持 @NumberFormat 的格式化
        conversionService.addFormatterForFieldAnnotation(new NumberFormatAnnotationFormatterFactory());

        /**
         * 配置全局的格式化格式
         * */
        // Register JSR-310 date conversion with a specific global format
        DateTimeFormatterRegistrar dateTimeFormatterRegistrar = new DateTimeFormatterRegistrar();
        dateTimeFormatterRegistrar.setDateFormatter(DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalDate 类型的格式化
        dateTimeFormatterRegistrar.setDateTimeFormatter(DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalTime、OffsetTime 类型的格式化
        dateTimeFormatterRegistrar.setTimeFormatter(DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalDateTime、ZonedDateTime、OffsetDateTime 类型的格式化
        // 将内置的格式化器 注册到 conversionService 中
        dateTimeFormatterRegistrar.registerFormatters(conversionService);

        /**
         * 配置全局的格式化格式
         * */
        // Register date conversion with a specific global format
        DateFormatterRegistrar dateFormatterRegistrar = new DateFormatterRegistrar();
        dateFormatterRegistrar.setFormatter(new DateFormatter("yyyyMMdd")); // 用于设置 Calendar、Date 类型的格式化
        // 将内置的格式化器 注册到 conversionService 中
        dateFormatterRegistrar.registerFormatters(conversionService);

        return conversionService;
    }

    @Bean("conversionService")
    public static FormattingConversionServiceFactoryBean formattingConversionServiceFactoryBean(Environment environment) {
        FormattingConversionServiceFactoryBean formattingConversionServiceFactoryBean = new FormattingConversionServiceFactoryBean();

        // 自定义默认格式
        DateTimeFormatterRegistrar dateTimeFormatterRegistrar = new DateTimeFormatterRegistrar();
        dateTimeFormatterRegistrar.setDateFormatter(DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalDate 类型的格式化
        dateTimeFormatterRegistrar.setDateTimeFormatter(DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalTime、OffsetTime 类型的格式化
        dateTimeFormatterRegistrar.setTimeFormatter(DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalDateTime、ZonedDateTime、OffsetDateTime 类型的格式化

        DateFormatterRegistrar dateFormatterRegistrar = new DateFormatterRegistrar();
        dateFormatterRegistrar.setFormatter(new DateFormatter("yyyyMMdd")); // 用于设置 Calendar、Date 类型的格式化

        HashSet<FormatterRegistrar> registrars = new HashSet<>();
        registrars.add(dateTimeFormatterRegistrar);
        registrars.add(dateFormatterRegistrar);

        HashSet<Object> formatters = new HashSet<>();
        formatters.add(new NumberFormatAnnotationFormatterFactory());

        // 配置
        formattingConversionServiceFactoryBean.setFormatters(formatters);
        formattingConversionServiceFactoryBean.setFormatterRegistrars(registrars);
        formattingConversionServiceFactoryBean.setRegisterDefaultFormatters(false);
        formattingConversionServiceFactoryBean.setEmbeddedValueResolver(environment::resolvePlaceholders);
        return formattingConversionServiceFactoryBean;
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(FormatterTest.class);
        System.out.println(context.getBean(FormatterTest.class));

        /*ConversionService conversionService = context.getBeanFactory().getConversionService();
        conversionService.convert(null, null);*/
    }
}

