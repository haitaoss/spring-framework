package cn.haitaoss.javaconfig.factorybean;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 16:06
 */
@Component
public class MyFactoryBean implements FactoryBean<TestFactoryBean> {
    @Override
    public TestFactoryBean getObject() throws Exception {
        System.out.println("执行了 MyFactoryBean.getObject()");
        return new TestFactoryBean();
    }

    @Override
    public Class<?> getObjectType() {
        return TestFactoryBean.class;
    }
}
