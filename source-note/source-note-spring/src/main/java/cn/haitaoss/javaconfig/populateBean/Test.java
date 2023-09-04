package cn.haitaoss.javaconfig.populateBean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import javax.annotation.Resource;
import java.util.List;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-07 14:57
 *
 */
@Component
public class Test {

    /**
     * 当方法返回值不是null时，参数列表的 @Qualifier("a2") 和 方法上的@Qualifier("a") 必须一致，否则是匹配不到bean的
     * {@link QualifierAnnotationAutowireCandidateResolver#isAutowireCandidate(BeanDefinitionHolder, DependencyDescriptor)}
     * */
    @Autowired(required = false)
    @Lazy
    //    @Qualifier("a")
    public void x(@Qualifier("a2") A a, @Lazy A axx) {
        System.out.println(a);
        System.out.println(axx);
    }

    /**
     * 方法返回值不是null，只会进行参数@Qualifier 的配
     * */
    @Autowired
    @Qualifier("a")
    public Object x2(@Qualifier("a2") A a) {
        System.out.println(a);
        return null;
    }

    @Resource
    @Lazy
    public void x3(@Lazy A a) {
        System.out.println(a);
    }

    @Autowired
    @Qualifier("a")
    public List<A> aList;
    @Resource
    public List<A> aList2;

    @Value("#{a2}")
    public A a2;

    @Bean
    public A a() {
        return new Son1();
    }

    @Bean
    //    @Primary
    public A a2() {
        return new Son2();
    }


    public class A {
    }

    @Priority(-1)
    public class Son1 extends A {
    }

    public class Son2 extends A {
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Test.class);
        System.out.println(context.getBean(Test.class).aList);
        System.out.println(context.getBean(Test.class).aList2);
        System.out.println(context.getBean(Test.class).a2);
    }


}