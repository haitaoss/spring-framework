package cn.haitaoss.javaconfig.Lookup;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-25 17:37
 *
 */
@ComponentScan
public class Test {
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Test.class);
        Class<B> bClass = B.class;
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));
        System.out.println(bClass.cast(context.getBean("bb")).a());
        System.out.println(bClass.cast(context.getBean("SupplierTest")).a());
        System.out.println(bClass.cast(context.getBean("test.B")).a());
    }

    @Bean
    public B bb() {
        // 这样子也会是 @Lookup 失效
        return new B();
    }


    @Component
    public static class B {
        @Lookup
        public A a() {
            System.out.println("@Lookup 失效了");
            return null;
        }

        @Component
        public static class A {

        }
    }
}


@Component
class MyBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // 测试 @Lookup 失效的情况
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(Test.class)
                .getBeanDefinition();
        beanDefinition.setInstanceSupplier(() -> {
            System.out.println("setInstanceSupplier--->");
            return new Test.B();
        });
        registry.registerBeanDefinition("SupplierTest", beanDefinition);
    }
}

