package cn.haitaoss.service;

import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-14 15:29
 */
@Component
public class UserService {
    // @Value("123")
    private Order order;

    public void say() {
        System.out.println(order);
    }

    public void test() {
        System.out.println("test");
    }
}
