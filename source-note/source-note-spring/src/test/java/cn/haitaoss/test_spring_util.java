package cn.haitaoss;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-08 10:30
 *
 */
public class test_spring_util {
    @Test
    public void test_AnnotatedElementUtils() throws Exception {
        //        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);

        /**
         * getAllMergedAnnotations
         *      Method--> 只查找当前方法，不会查找其接口、或者父类上的注解
         *      Class--> 当前类上的注解
         *
         * findAllMergedAnnotations
         *      Method--> 查找当前方法 + 其接口方法 + 父类方法
         *      Class-->  查找类 + 类的父类 + 类的接口
         * */
        Class clazz = A.class;
        Method methodA = clazz.getMethod("a");
        Method methodB = clazz.getMethod("b");

        HashSet<Class<? extends Annotation>> classList = new HashSet<>(Arrays.asList(Lazy.class,Value.class,Qualifier.class));

        System.out.println("实现接口方法");
        System.out.println(AnnotatedElementUtils.getAllMergedAnnotations((AnnotatedElement) methodA, classList));
        System.out.println("============");
        System.out.println(AnnotatedElementUtils.findAllMergedAnnotations((AnnotatedElement) methodA, classList));

        System.out.println("重写父类方法");
        System.out.println(AnnotatedElementUtils.getAllMergedAnnotations((AnnotatedElement) methodB, classList));
        System.out.println("============");
        System.out.println(AnnotatedElementUtils.findAllMergedAnnotations((AnnotatedElement) methodB, classList));


        classList = new HashSet<>(Arrays.asList(ComponentScan.class,Configuration.class,Component.class));

        System.out.println("类");
        System.out.println(AnnotatedElementUtils.getAllMergedAnnotations((AnnotatedElement) clazz, classList));
        System.out.println("============");
        System.out.println(AnnotatedElementUtils.findAllMergedAnnotations((AnnotatedElement) clazz, classList));
    }
}

@ComponentScan
interface IA {
    @Lazy
    void a();
}

@Configuration
class AParent {
    @Lazy
    public void b() {

    }
}
@Component
class A extends AParent implements IA {
    @Value("1")
    @Qualifier("2")
    public void a() {

    }

    @Override
    @Value("1")
    @Qualifier("2")
    public void b() {
        super.b();
    }
}