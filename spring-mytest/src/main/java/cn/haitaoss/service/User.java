package cn.haitaoss.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-14 11:27
 */
public class User {
    @Bean
    public UserService userService() {
        return new UserService();
    }
}

@Component
@Import(User.class)
class ImportTest {

}
