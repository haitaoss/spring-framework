package cn.haitaoss.javaconfig.EventListener;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-26 20:06
 */
@ComponentScan
public class Test extends AnnotationConfigApplicationContext {

    public Test() {
    }

    public Test(Class<?> clazz) {
        super(clazz);
    }

    @Override
    protected void onRefresh() throws BeansException {
        //  发布早期事件 测试一下
        publishEvent(new DemoEvent("早期事件"));
    }

    public static void main(String[] args) {
        Test test = new Test(Test.class);
        test.publishEvent(new DemoEvent("context刷新好了"));
        /*
控制台输出结果：
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=早期事件]
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=单例bean实例化事件]
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=单例bean初始化事件]
MyApplicationListener---->cn.haitaoss.javaconfig.EventListener.DemoEvent[source=context刷新好了]
MyEventListener------>cn.haitaoss.javaconfig.EventListener.DemoEvent[source=context刷新好了]

        */
    }


}

@Component
class MyEventListener {
    @EventListener(classes = DemoEvent.class)
    public void a(DemoEvent demoEvent) {
        /**
         * @EventListener 是在刷新bean的时候在解析注册的，所以 早期事件 是不能通过
         * */
        System.out.println("MyEventListener------>" + demoEvent);
    }
}

@Component
class MyApplicationListener implements ApplicationListener<DemoEvent> {
    @Override
    public void onApplicationEvent(DemoEvent event) {
        System.out.println("MyApplicationListener---->" + event);
    }
}

class DemoEvent extends ApplicationEvent {
    private static final long serialVersionUID = 7099057708183571937L;

    public DemoEvent(Object source) {
        super(source);
    }
}


@Component
class SingleObject implements InitializingBean {
    @Autowired
    ApplicationEventMulticaster applicationEventMulticaster;

    public SingleObject(ApplicationEventMulticaster applicationEventMulticaster) {
        applicationEventMulticaster.multicastEvent(new DemoEvent("单例bean实例化事件"));
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        applicationEventMulticaster.multicastEvent(new DemoEvent("单例bean初始化事件"));
    }
}