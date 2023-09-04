package cn.haitaoss.javaconfig.EnableLoadTimeWeaving.dynamic;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DynamicAspect {
    @Around("execution(* cn.haitaoss..*(..))")
    public Object profile(ProceedingJoinPoint pjp) throws Throwable {
        System.out.println("<----dynamic aop before---->");
        Object proceed = pjp.proceed();
        System.out.println("<----dynamic aop after---->");
        return proceed;
    }
}