package cn.haitaoss.javaconfig.configclass;

import cn.haitaoss.javaconfig.service.OrderService;
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
    public Test() {
        System.out.println("构造器--->Test");
    }

    public String msg;

    public void say() {
        System.out.println("msg---->" + msg);
    }

    @Override
    public String toString() {
        return "Test{" + "msg='" + msg + '\'' + '}';
    }
}

@Configuration
@Import({MyImportSelector.class, MyImportBeanDefinitionRegistrar.class, OrderService.class})
@EnableAutoConfiguration
@ImportResource("classpath:spring.xml")
class Config1 extends baseSupport {
    public Config1() {
        setMsg("Config1");
    }

    @Bean
    public Test test2() {
        Test test = new Test();
        test.msg = "Config1.test2";
        return test;
    }
}

@Configuration
class Config2 extends baseSupport {
    public Config2() {
        setMsg("Config2");
    }

    @Bean
    public Test test2() {
        // 默认是支持重复的beanDefinition的，这个Bean是后加载，所以最终输出的是这个bean的信息
        Test test = new Test();
        test.msg = "Config2.test2";
        return test;
    }
}

class baseSupport {
    private String msg;

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Bean
    @Primary
    public Test test() {
        /**
         * 加载配置类时，如果父类存在 @Bean标注的方法，也会作为配置类进行加载.
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
        return new String[]{"cn.haitaoss.javaconfig.service.UserService"};
    }
}

class MyImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry, BeanNameGenerator importBeanNameGenerator) {
        ImportBeanDefinitionRegistrar.super.registerBeanDefinitions(importingClassMetadata, registry, importBeanNameGenerator);
    }
}



