package cn.haitaoss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-13 09:11
 * 验证这个规则：org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider#isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition)
 * 是独立类 && (是抽象类 && Lookup 注解)
 */
@Component
public class LookupService {
    @Lookup("orderService") // Lookup 就是找一个bean 然后返回（这个bean会生成代理对象）
    // @Lookup() // Lookup 就是找一个bean 然后返回（这个bean会生成代理对象）
    public OrderService test() {
        System.out.println("我不会执行的");
        return null;
    }

    @Autowired
    private Demo demo;

    @Lookup("demo")
    public Demo getDemo() {
        return null;
    }

    public void test1() {
        System.out.println(demo);
    }

    public void test2() {
        System.out.println(getDemo());
    }

}

@Component
@Scope("prototype")
class Demo {

}