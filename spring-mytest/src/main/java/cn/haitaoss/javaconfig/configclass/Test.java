package cn.haitaoss.javaconfig.configclass;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-10 09:31
 *
 */
public class Test {
    public String msg;

    public void say() {
        System.out.println("Person---->" + msg);
    }
}

@Configuration
@Import({MyImportSelector.class, MyImportBeanDefinitionRegistrar.class})
@ImportResource("classpath:spring.xml")
class Config1 extends baseSupport {
    public Config1() {
        setMsg("Config1");
    }
}

@Configuration
class Config2 extends baseSupport {
    public Config2() {
        setMsg("Config2");
    }
}

class baseSupport {
    private String msg;

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Bean
    public Test test() {
        /**
         * 只会被加载一次，因为针对 配置类的父类中有@Bean注解的方法，有一个缓存机制避免重复加载
         * @see org.springframework.context.annotation.ConfigurationClassParser#doProcessConfigurationClass(org.springframework.context.annotation.ConfigurationClass, org.springframework.context.annotation.ConfigurationClassParser.SourceClass, java.util.function.Predicate)
         * */
        Test test = new Test();
        test.msg = msg;
        return test;
    }
}

class MyImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{"cn.haitaoss.javaconfig.configclass.baseSupport"};
    }
}

class MyImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
        ImportBeanDefinitionRegistrar.super.registerBeanDefinitions(importingClassMetadata, registry, importBeanNameGenerator);
    }
}