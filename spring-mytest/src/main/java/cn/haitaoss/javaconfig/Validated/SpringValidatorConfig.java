package cn.haitaoss.javaconfig.Validated;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.BeanValidationPostProcessor;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

import javax.validation.Validator;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-20 14:27
 *
 */
@Configuration
public class SpringValidatorConfig {
    @Bean
    public LocalValidatorFactoryBean localValidatorFactoryBean() {
        LocalValidatorFactoryBean localValidatorFactoryBean = new LocalValidatorFactoryBean();
        localValidatorFactoryBean.setValidationMessageSource(messageSource());
        return localValidatorFactoryBean;
    }

    @Bean
    public MethodValidationPostProcessor validationPostProcessor(Validator validation) {
        MethodValidationPostProcessor methodValidationPostProcessor = new MethodValidationPostProcessor();
        methodValidationPostProcessor.setValidator(validation);
        return methodValidationPostProcessor;
    }

    @Bean
    public BeanValidationPostProcessor beanValidationPostProcessor(Validator validation) {
        BeanValidationPostProcessor beanValidationPostProcessor = new BeanValidationPostProcessor();
        beanValidationPostProcessor.setValidator(validation);
        return beanValidationPostProcessor;
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setDefaultEncoding("UTF-8");
        // 不需要指定后缀，在获取指定Locale的信息时，会拼接 _zh_CN.properties 然后通过ClassLoader读取文件
        messageSource.setBasenames("i18n/f1");
        return messageSource;
    }
}