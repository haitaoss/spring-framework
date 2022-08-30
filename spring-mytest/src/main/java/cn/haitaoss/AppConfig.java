package cn.haitaoss;

import cn.haitaoss.javaconfig.service.Person;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;

import java.util.Collections;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:51
 */
@ComponentScan("cn.haitaoss.javaconfig.service")
// @ComponentScan(value = "cn.haitaoss.javaconfig.beanfactorypostprocessor", excludeFilters = {}, includeFilters = {})
// @ComponentScan("cn.haitaoss.javaconfig.factorybean")
// @ComponentScan("cn.haitaoss.javaconfig.dependson")
// @ComponentScan("cn.haitaoss.javaconfig.service")
// @ComponentScan("cn.haitaoss.javaconfig.create")
// @DependsOn({"user"})
// @Import({MyImportSelector.class, MyImportBeanDefinitionRegistrar.class})
public class AppConfig {
    public AppConfig() {
        System.out.println("AppConfig 构造器");
    }

    @Bean
    public FormattingConversionService conversionService() {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new String2PersonConverter());
        return conversionService;
    }
}

class String2PersonConverter implements ConditionalGenericConverter {
    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        return String.class.isAssignableFrom(sourceType.getType())
               && Person.class.isAssignableFrom(targetType.getType());
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton(new ConvertiblePair(String.class, Person.class));
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        Person person = new Person();
        person.setName(source + "--->");
        return person;
    }

    public static void main(String[] args) {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new String2PersonConverter());

        Person person = conversionService.convert("hahahh", Person.class);
        System.out.println(person);
    }
}