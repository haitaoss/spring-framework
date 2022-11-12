package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-09 09:44
 *
 */
@Import(B.class)
public class ConfigTest {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ConfigTest.class);
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));
    }
}

@Component
class B {
    @Component
    public class A {

    }

    @Component
    public static class C {

    }
}