package cn.haitaoss.javaconfig.EnableLoadTimeWeaving.ajc;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.aspectj.annotation.AbstractAspectJAdvisorFactory;
import org.springframework.stereotype.Component;

/**
 * 其实没必要注册到容器中，因为用作 ajc 编译器的 切面类，那么就不会作为 动态代理方式的aop的 切面类了，
 * 具体代码看这里 {@link AbstractAspectJAdvisorFactory#compiledByAjc(Class)}
 */
@Aspect
@Component
public class AjcAspect {
    @Around("execution(* cn.haitaoss..*(..))")
    public Object profile(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("<----ajc aop before---->");
        Object proceed = pjp.proceed();
        System.out.println("<----ajc aop after---->");
        return proceed;
    }
}