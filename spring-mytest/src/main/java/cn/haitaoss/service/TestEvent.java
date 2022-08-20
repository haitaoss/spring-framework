package cn.haitaoss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-20 11:17
 */
@Component
public class TestEvent {
    @Autowired
    ApplicationEventMulticaster applicationEventMulticaster;

    @Autowired
    ApplicationContext applicationContext;

    public void pushEvent() {
        applicationContext.publishEvent("使用 ApplicationContext 发布事件");

        applicationEventMulticaster.multicastEvent(new PayloadApplicationEvent<String>(this, "使用 ApplicationEventMulticaster 发布事件"));
    }
}

@Component
class MyEventListener implements ApplicationListener<ApplicationEvent> {
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        System.out.println("使用 接口 的方式监听事件");
        if (event instanceof PayloadApplicationEvent) {
            @SuppressWarnings({"unchecked", "rawtypes"}) PayloadApplicationEvent e = (PayloadApplicationEvent<String>) event;
            System.out.println(e.getPayload());
            System.out.println(e.getSource());
        }
    }
}

@Component
class MyEventListener2 {
    @EventListener
    public void a(ApplicationEvent event) {
        System.out.println("使用 注解 的方式监听事件");
    }
}