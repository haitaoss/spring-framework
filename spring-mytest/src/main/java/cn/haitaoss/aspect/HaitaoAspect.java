package cn.haitaoss.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-01 19:54
 */
@Component
@Aspect
public class HaitaoAspect {
    @Before("execution(public void cn.haitaoss.service.UserService.test()")
    public void before(JoinPoint joinPoint) {
        System.out.println("before...");
    }
}
