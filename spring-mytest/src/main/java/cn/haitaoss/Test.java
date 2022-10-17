package cn.haitaoss;


import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */

public class Test {


    public static void main(String[] args) throws Exception {
        // ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("spring.xml");
        // AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.Test.class);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        /**
         * BeanPostProcessor执行过程：
         * 实例化前后置->推断构造器后置->实例化bean->合并BeanDefinition后置->实例化后后置->属性注入后置->初始化前后置->初始化后后置->销毁前后置(hit)
         * */
    }
}


