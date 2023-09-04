package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.Bean;

public class Config3 {
    public Config3() {
        System.out.println("构造器--->Config3");
    }

    @Bean
    // @ConditionalOnMissBean("config4Test") 失效，因为 config4Test 是在后面才加载的
    public Test config3Test() {
        Test test = new Test();
        test.msg = "Config3.test2";
        return test;
    }
}
