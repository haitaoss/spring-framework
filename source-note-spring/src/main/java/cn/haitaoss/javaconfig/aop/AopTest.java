package cn.haitaoss.javaconfig.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

import java.util.Arrays;


/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-14 21:08
 */
@EnableAspectJAutoProxy(exposeProxy = true, proxyTargetClass = true)
@Component
public class AopTest {
	public AopTest() {
		System.out.println("AopTest 构造器");
	}

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AopTest.class);
		System.out.println(Arrays.toString(context.getBeanDefinitionNames()));

		context.getBean(AopDemo.class).test2("haitao");
	}

	@Aspect
	@Component
	public class AspectDemo {
		/**
		 * 只有@Around支持 ProceedingJoinPoint、JoinPoint、JoinPoint.StaticPart
		 * 其他的支持 JoinPoint、JoinPoint.StaticPart
		 * try{
		 *
		 * @Around、@Before、@AfterReturning }catch(){
		 * @AfterThrowing }finally{
		 * @After }
		 */
		@Pointcut("execution(* test*(..))")
		private void pointcut() {
		}

		@Around("pointcut()")
		public void around(ProceedingJoinPoint proceedingJoinPoint) {
			try {
				System.out.println("around...");
				proceedingJoinPoint.proceed();
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		}

		@Around("pointcut() && args(msg) ")
		public void around(ProceedingJoinPoint proceedingJoinPoint, String msg) {
			try {
				System.out.println("around..." + msg);
				proceedingJoinPoint.proceed();
			} catch (Throwable e) {
				throw new RuntimeException(e);
			}
		}

		@Before("pointcut()")
		public void before(/*JoinPoint joinPoint,*/JoinPoint.StaticPart staticPart) {
			System.out.println("before...");
		}

		@After("pointcut()")
		public void after(JoinPoint joinPoint) {
			System.out.println("after...");
		}


		@AfterThrowing("pointcut()")
		public void afterThrowing(JoinPoint joinPoint) {
			System.out.println("afterThrowing...");
		}

		@AfterReturning("pointcut()")
		public void afterReturning(JoinPoint joinPoint) {
			System.out.println("afterReturning...");
		}
	}


	@Component
	public class AopDemo {
		public void test() {
			System.out.println("AopDemo.test");
		}

		public void test2(String msg) {
			System.out.println("AopDemo.test....." + msg);
		}
	}

}
