package cn.haitaoss.javaconfig.aop;

import org.aspectj.lang.annotation.Before;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-14 21:08
 *
 */
@EnableAspectJAutoProxy
@Component
public class AopTest {
    public AopTest() {
        System.out.println("AopTest 构造器");
    }

    // @Aspect
    @Component
    static class Advice {
        public Advice() {
            System.out.println("Advice 构造器");
        }

        @Before("")
        public Object before() {
            return null;
        }
    }

  /*  @Bean
    public UserService userService() {
        return new UserService();
    }*/

    @Component
    class Advice2 {

    }
}

class Advice3 {

}