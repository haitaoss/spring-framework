package cn.haitaoss.javaconfig.circular;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 19:19
 *
 */
@Component
@Scope("prototype")
public class A {
    @Autowired
    private B b;

    public A() {
        System.out.println("A...");
    }
}

@Component
@Scope("prototype")
class B {
    @Autowired
    private A a;

    public B() {
        System.out.println("B...");
    }
}
