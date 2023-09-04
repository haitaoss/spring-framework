package cn.haitaoss.javaconfig.EnableLoadTimeWeaving.dynamic;

import org.springframework.stereotype.Component;

@Component
public class DynamicBean {
    private void a() {
        System.out.println("Bean.a...");
    }
    public void a2() {
        System.out.println("Bean.a2...");
    }
}