package cn.haitaoss.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-30 10:34
 */

@Aspect
@Component
@EnableAspectJAutoProxy
public class Aspect1 {
//	@Before("execution(public void cn.haitaoss.service.UserService.test())")
	@Before("execution(public void cn.haitaoss.service.AService.test())")
	public void before() {
		System.out.println("before...");
	}
}
