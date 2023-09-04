package cn.haitaoss.javaconfig.EnableLoadTimeWeaving.ajc;

import org.springframework.stereotype.Component;

@Component
public class AjcBean {
    private void a() {
        System.out.println("Bean.a...");
    }
    public void a2() {
        System.out.println("Bean.a2...");
    }
}