package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.Bean;

public class Config4 {
    public Config4() {
        System.out.println("构造器--->Config4");
    }

    @Bean
    // @ConditionalOnMissBean("config3Test")
    public Test config4Test() {
        Test test = new Test();
        test.msg = "Config4.test2";
        return test;
    }
}