package cn.haitaoss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-14 09:44
 */
@Component
public class LazyService {
    @Autowired
    @Lazy
    private LazyBean lazyBean; // TODOHAITAO: 2022/8/14 使用 @Lazy，那么这里注入的bean是代理对象，调用代理对象的方法是从容器中获取bean

    public void test() {
        // lazyBean 是原型bean
        System.out.println("start...");
        // System.out.println(lazyBean); // 会从容器中获取 lazyBean ，由于 lazyBean 是 原型bean，所以这里是创建新的
        // System.out.printl
        // n(lazyBean);// 同理
        lazyBean.setMsg("hello wold");
        System.out.println(lazyBean.getMsg()); // 打印结果是null，hahaha
        System.out.println("end...");
    }
}

@Component
@Scope("prototype")
class LazyBean {
    private String msg;

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}