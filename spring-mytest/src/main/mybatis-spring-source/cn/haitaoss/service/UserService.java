package cn.haitaoss.service;

import cn.haitaoss.mapper.OrderMapper;
import cn.haitaoss.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:52
 *
 */
@Component
public class UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderMapper orderMapper;

    public void printName() {
        System.out.println(userMapper.getUsername());
        System.out.println(orderMapper.getOrderName());
    }
}
