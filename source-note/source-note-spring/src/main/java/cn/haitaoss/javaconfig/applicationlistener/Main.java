package cn.haitaoss.javaconfig.applicationlistener;

import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 13:36
 */
@ComponentScan
public class Main {
	public static void main(String[] args) {
		new MyAnnotationConfigApplicationContext(Main.class);
	}
}
