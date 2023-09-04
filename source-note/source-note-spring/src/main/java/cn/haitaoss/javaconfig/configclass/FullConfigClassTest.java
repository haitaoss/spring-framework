package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-28 21:20
 *
 */
@Configuration
@Lazy
public class FullConfigClassTest {
    public static class A {
    }

    public void test() {
        System.out.println("a2()--->" + a2());
        System.out.println("a2()--->" + a2());

        System.out.println("a3()--->" + a3());
        System.out.println("a3()--->" + a3());

        System.out.println("a4()--->" + a4());
        System.out.println("a4()--->" + a4());
    }

    public static void main(String[] args) {
        System.setProperty(org.springframework.cglib.core.DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "/Users/haitao/Desktop/xx");
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(FullConfigClassTest.class);
        //        context.getBean(FullConfigClassTest.class).test();
        System.out.println("====");
        context.getBean("a");
    }

    @Bean
    public A a() {
        System.out.print("a1()--->");
        printHitIntercept();

        System.out.print("a2()--->");
        this.a2();
        System.out.print("a22()--->");
        this.a2();

        System.out.print("a3()--->");
        a3();
        System.out.print("a4()--->");
        a4();
        return new A();
    }

    @Bean
    //    @Scope("prototype")
    public A a2() {
        printHitIntercept();
        return new A();
    }

    public A a3() {
        printHitIntercept();
        return new A();
    }

    @Bean
    public static A a4() {
        printHitIntercept();
        return new A();
    }

    private static void printHitIntercept() {
        System.out.println(Arrays.stream(Thread.currentThread().getStackTrace())
                .map(StackTraceElement::getMethodName)
                .filter(item -> item.contains("intercept"))
                .collect(Collectors.toList()));
    }


}
