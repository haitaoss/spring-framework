package cn.haitaoss.service;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:52
 */
@Component
/*
    OrderService 不是单例bean，UserService 依赖了 OrderService，spring 在对 UserService 进行属性注入的时候，会通过 OrderService 配置Scope配置的代理模式，创建出一个代理对象
    注入到 UserService 中，只是创建了代理对象 OrderService 并没有创建
    @Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
 */
@Scope("prototype")
public class OrderService {
    public OrderService() {
        // System.out.println("初始化-->" + this);
    }

    public void test() {
        System.out.println("test-->" + this);
    }
}
