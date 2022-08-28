package cn.haitaoss.javaconfig.beanfactorypostprocessor;

import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 11:18
 */
@Component
public class TestPreCreate {
    public TestPreCreate() {
        System.out.println("TestPreCreate...");
    }
}
