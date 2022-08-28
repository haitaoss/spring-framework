package cn.haitaoss.javaconfig.beanfactorypostprocessor;

import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 11:18
 */
@Component
public class TestPreCreate2 {
    public TestPreCreate2() {
        System.out.println("TestPreCreate2...");
    }
}
