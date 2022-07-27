package cn.haitaoss;

import cn.haitaoss.bean.User;
import cn.haitaoss.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-27 21:10
 */
public class Test {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
		User bean = context.getBean(User.class);

		System.out.println("bean = " + bean);

		UserService userService = context.getBean(UserService.class);
		System.out.println("userService = " + userService);
	}
}
