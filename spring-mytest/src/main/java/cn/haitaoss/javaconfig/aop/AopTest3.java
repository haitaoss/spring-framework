package cn.haitaoss.javaconfig.aop;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.DeclareParents;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-14 21:08
 */
@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
@Component
public class AopTest3 {
    @Aspect
    @Component
    public static class AspectDemo3 {
        @DeclareParents(value = "cn.haitaoss.javaconfig.aop.AopTest3.AopDemo", defaultImpl = MyIntroductionImpl.class)
        private MyIntroduction x;
    }


    @Component
    public static class AopDemo {
        public void test() {
            System.out.println("AopDemo.test");
        }
    }

    public static class MyIntroductionImpl implements MyIntroduction {

        @Override
        public void test1() {
            System.out.println("MyIntroductionImpl.test1");
        }

    }

    public interface MyIntroduction {
        void test1();
    }
}
