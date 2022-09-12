package cn.haitaoss;

import cn.haitaoss.javaconfig.service.UserService;
import org.springframework.context.annotation.Bean;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:51
 */
// @Order(1)
public class AppConfig2  {

    public AppConfig2() {
        System.out.println("构造器--->AppConfig2");
    }

    @Bean
    public UserService appConfig2UserService() {
        return new UserService();
    }

}
