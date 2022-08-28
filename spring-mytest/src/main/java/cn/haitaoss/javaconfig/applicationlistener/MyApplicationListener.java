package cn.haitaoss.javaconfig.applicationlistener;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 13:28
 */
@Component
public class MyApplicationListener implements ApplicationListener<MyApplicationEvent>, ApplicationContextAware {
    private ApplicationContext applicationContext;

    public MyApplicationListener() {
        System.out.println("MyApplicationListener....");
    }

    @Override
    public void onApplicationEvent(MyApplicationEvent event) {
        System.out.println("event is : " + event);
        System.out.println("编译 提前加载bean的逻辑");

        applicationContext.getBean("testPreCreate3");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
