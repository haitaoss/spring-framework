package cn.haitaoss.javaconfig.populateBean;

import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-07 14:57
 *
 */
@Component
public class TestAutowired {

    /**
     * @Autowired 修饰构造器：
     *  - @Autowired 修饰构造器，在 {@link AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors(Class, String)}会判断是否位候选的构造器。
     *  - @Autowired(required = false)可以使用多个
     *  - @Autowired(required = true) 至多只能有一个
     *
     * @Autowired、@Value 修饰字段和方法：
     *  - 在这里会解析和检验 {@link AutowiredAnnotationBeanPostProcessor#postProcessMergedBeanDefinition(RootBeanDefinition, Class, String)}
     *  - 在这里进行注入 {@link AutowiredAnnotationBeanPostProcessor#postProcessProperties(PropertyValues, Object, String)}
     * */
    public TestAutowired() {
    }

    @Autowired(required = false)
    public TestAutowired(A a) {
        this.a = a;
    }

    @Autowired(required = false)
    public TestAutowired(A a, A a2) {
        this.a = a;
        this.a2 = a2;
    }

    @Bean
    public A a() {
        return new A();
    }

    public class A {
        public A() {
            System.out.println("构造器");
        }
    }

    @Autowired
    public A a;
    @Value("#{a}")
    public A a2;

    //    @Autowired(required = false)
    @Value("#{a}")
    public void x(A a) {
        System.out.println("x--->" + a);
    }

    @Autowired
    public void setA(A a) {
        System.out.println("setA--->" + a);
    }

    @Autowired
    public void setB(A a) {
        System.out.println("setB--->" + a);
    }


   /* @Autowired
    public void xxxx(Object o) {
        System.out.println("xxxx--->" + a);
    }*/

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestAutowired.class);
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));
    }


}