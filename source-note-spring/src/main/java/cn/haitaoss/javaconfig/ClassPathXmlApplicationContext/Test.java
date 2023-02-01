package cn.haitaoss.javaconfig.ClassPathXmlApplicationContext;

import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-01 15:36
 *
 */
public class Test {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring6.xml");
    }
}
