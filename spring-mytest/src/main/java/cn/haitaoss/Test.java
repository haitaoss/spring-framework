package cn.haitaoss;


import cn.haitaoss.service.LazyService;
import lombok.Data;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.core.DefaultGeneratorStrategy;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
@Data
public class Test {
    public static void main(String[] args) throws Exception {
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "/Users/haitao/Desktop/xx");

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        LazyService bean = context.getBean(LazyService.class);
        bean.test();
        /*System.out.println("--->");
        bean.test();
        System.out.println("========");
        System.out.println(context.getBean(OrderService.class));
        System.out.println(context.getBean(OrderService.class));*/
        /*System.out.println(context.getBean(LookupService.class)
                .test());

        context.getBean(LookupService.class)
                .test1();
        context.getBean(LookupService.class)
                .test1();
        context.getBean(LookupService.class)
                .test2();
        context.getBean(LookupService.class)
                .test2();
        UserService userService = (UserService) context.getBean("userService");
        userService.test();

        System.out.println(context.getBean(UserService.MemberService2.class));
        context.getBean(UserService.MemberService1.class);*/


        //  ASM 工具的简单使用
        /*FileInputStream fis = new FileInputStream("/Users/haitao/Desktop/spring-framework/spring-mytest/build/classes/java/main/cn/haitaoss/AppConfig.class");
        ClassReader classReader = new ClassReader(fis);
        System.out.println(classReader.getClassName());
        System.out.println(classReader.getSuperName());*/

        // test();
        // test2();

       /* D d = new D();
        System.out.println(d);

        d.test();*/
    }

    public static void test() {
        Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class<?>[]{A.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return null;
            }
        });

    }

    public static void test2() {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(C.class);

        C c = new C();
        enhancer.setCallback(new org.springframework.cglib.proxy.InvocationHandler() {
            @Override
            public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
                System.out.println("代理");
                return method.invoke(c, objects);
            }
        });
        C o = (C) enhancer.create();
        o.x();

        try {
            DefaultGeneratorStrategy instance = DefaultGeneratorStrategy.INSTANCE;
            byte[] classBin = instance.generate(enhancer);
            new FileOutputStream(new File("/Users/haitao/Desktop/C.class")).write(classBin);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

interface A {

}

class B implements A {}

class C {
    public void x() {
        System.out.println(this);
        System.out.println("执行了");
    }
}

class D extends C {
    public void test() {
        super.x();
    }
}