package cn.haitaoss.javaconfig.dependson;

import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 17:52
 *
 */
@Component
@DependsOn({"c", "c", "c"})
public class A {}

@Component
@DependsOn("c")
class B {

}

@Component
class C {

}

// ============

@Component
        // @DependsOn({"b1"})
class A1 {}

@Component
@DependsOn({"c1"})
class b1 {

}

@Component
@DependsOn({"a1"})
class C1 {

}

@Component
        // @DependsOn("a2")
class A2 {}