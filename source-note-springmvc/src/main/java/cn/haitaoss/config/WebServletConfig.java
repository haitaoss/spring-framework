package cn.haitaoss.config;

import cn.haitaoss.view.MyView;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.format.FormatterRegistrar;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.format.datetime.DateFormatterRegistrar;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.number.NumberFormatAnnotationFormatterFactory;
import org.springframework.format.support.FormattingConversionServiceFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.UrlBasedViewResolver;

import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-15 21:10
 */
@Configuration
@EnableWebMvc
@ComponentScan(value = "cn.haitaoss")
@Import(MethodValidationPostProcessor.class)
public class WebServletConfig implements ApplicationContextAware {
    public static ApplicationContext webApplicationContext;

    @Bean
    public ViewResolver viewResolver() {
        //  试图解析器，这是用来解析jsp的
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver() {
            @Override
            protected boolean canHandle(String viewName, Locale locale) {
                return !viewName.startsWith("HTML:");
            }
        };
        viewResolver.setPrefix("/");
        viewResolver.setSuffix(".jsp");
        return viewResolver;
    }

    @Bean
    public ViewResolver viewResolver2() {
        // 用来响应.html的
        UrlBasedViewResolver viewResolver = new UrlBasedViewResolver() {
            @Override
            public View resolveViewName(String viewName, Locale locale) throws Exception {
                String prefix = "HTML:";
                if (!viewName.startsWith(prefix)) {
                    return null;
                }
                return super.resolveViewName(viewName.substring(prefix.length()), locale);
            }
        };
        viewResolver.setPrefix("/");
        viewResolver.setSuffix(".html");
        viewResolver.setViewClass(MyView.class);
        return viewResolver;
    }

   /* @Bean
    public MethodValidationPostProcessor methodValidationPostProcessor() {
        MethodValidationPostProcessor methodValidationPostProcessor = new MethodValidationPostProcessor();
        return methodValidationPostProcessor;
    }*/
    @Bean("conversionService")
    public static FormattingConversionServiceFactoryBean formattingConversionServiceFactoryBean(
            Environment environment) {
        FormattingConversionServiceFactoryBean formattingConversionServiceFactoryBean = new FormattingConversionServiceFactoryBean();

        // 自定义默认格式
        DateTimeFormatterRegistrar dateTimeFormatterRegistrar = new DateTimeFormatterRegistrar();
        dateTimeFormatterRegistrar.setDateFormatter(DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalDate 类型的格式化
        dateTimeFormatterRegistrar.setDateTimeFormatter(
                DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalTime、OffsetTime 类型的格式化
        dateTimeFormatterRegistrar.setTimeFormatter(
                DateTimeFormatter.ofPattern("yyyyMMdd")); // 用于设置 LocalDateTime、ZonedDateTime、OffsetDateTime 类型的格式化

        DateFormatterRegistrar dateFormatterRegistrar = new DateFormatterRegistrar();
        dateFormatterRegistrar.setFormatter(new DateFormatter("yyyyMMdd")); // 用于设置 Calendar、Date 类型的格式化

        HashSet<FormatterRegistrar> registrars = new HashSet<>();
        registrars.add(dateTimeFormatterRegistrar);
        registrars.add(dateFormatterRegistrar);

        // formatter
        HashSet<Object> formatters = new HashSet<>();
        formatters.add(new NumberFormatAnnotationFormatterFactory());

        // 自定义的 Converter
        HashSet<Converter> converters = new HashSet<>();
        converters.add(new Converter<String, ApplicationContext>() {
            @Override
            public ApplicationContext convert(String source) {
                AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
                context.setId(source);
                return context;
            }
        });
        // 配置
        formattingConversionServiceFactoryBean.setFormatters(formatters);
        formattingConversionServiceFactoryBean.setFormatterRegistrars(registrars);
        formattingConversionServiceFactoryBean.setRegisterDefaultFormatters(false);
        formattingConversionServiceFactoryBean.setEmbeddedValueResolver(environment::resolvePlaceholders);

        formattingConversionServiceFactoryBean.setConverters(converters);
        return formattingConversionServiceFactoryBean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        webApplicationContext = applicationContext;
    }

}
