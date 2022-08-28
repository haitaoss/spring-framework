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
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        Object a = context.getBean("a");
        System.out.println("a = " + a);
        /*
        测试 supplier 构造器函数
        Object userSupplier = context.getBean("userSupplier");
        System.out.println("userSupplier = " + userSupplier);
        System.out.println("========");
        Object myFactoryBean = context.getBean("myFactoryBean");
        System.out.println("myFactoryBean = " + myFactoryBean);*/
        // System.out.println(context.getBean("a"));
        /*
       测试FactoryBean
        System.out.println(context.getBean("&myFactoryBean"));
        System.out.println(context.getBean("&myFactoryBean"));
        System.out.println(context.getBean("&myFactoryBean"));
        System.out.println("=====");
        System.out.println(context.getBean("myFactoryBean"));
        System.out.println(context.getBean("myFactoryBean"));*/

        /*LinkedHashSet<String> set = new LinkedHashSet<>();
        System.out.println(set.add("name"));
        System.out.println(set.add("name"));
        System.out.println(set.add("name"));*/

    }

}

