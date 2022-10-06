package cn.haitaoss.javaconfig.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-14 21:08
 */
/*@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
@Component*/
public class AopTest2 {
    @Aspect
    @Component
    public class AspectDemo {

        @Pointcut("execution(* test*(..))")
        private void pointcut() {
        }

        @Around("pointcut()")
        public void around(/*JoinPoint joinPoint,*/JoinPoint.StaticPart staticPart) {
            System.out.println("AspectDemo.around...");
        }

    }


    @Aspect
    @Component
    @Order(-1) // 排序，让其优先执行
    public class AspectDemo2 {

        @Pointcut("execution(* test(..))")
        private void pointcut() {
        }

        @Before("pointcut()")
        public void before(JoinPoint joinPoint) {
            System.out.println("AspectDemo2.before...");
        }
    }

    @Component
    public class AopDemo {
        public void test() {
            System.out.println("AopDemo.test");
        }
    }

}
