package cn.haitaoss.javaconfig.populateBean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-07 14:57
 *
 */
@Component
public class Test_Qualifier {

    @Autowired
    @Qualifier("x")
    @Lazy
    public void x(@Qualifier("a") A a) {
        System.out.print("x--->");
        try {
            System.out.println(a);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @Autowired
    @Qualifier("x")
    @Lazy
    public Object x2(@Qualifier("a") A a) {
        System.out.println("x2--->" + a);
        return null;
    }

    @Autowired
    @base
    public void x3(A xx) {
    }

    @Qualifier("a")
    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    public static @interface base {
    }

    // @Priority(-1)
    @Component("a")
    public class A {
    }

    public static void main(String[] args) throws Exception {
        new AnnotationConfigApplicationContext(Test_Qualifier.class);
    }
}