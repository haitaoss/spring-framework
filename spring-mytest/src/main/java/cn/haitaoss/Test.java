package cn.haitaoss;


import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class, AppConfig2.class);
        /*context.getBean(cn.haitaoss.javaconfig.configclass.Test.class)
                .say();
        ((cn.haitaoss.javaconfig.configclass.Test) context.getBean("test2")).say();

        System.out.println(context.getBean("config3Test"));
        System.out.println(context.getBean("config4Test"));*/



        /*Tomcat tomcat = new Tomcat();
        StandardContext standardContext = new StandardContext();
        tomcat.getHost()
                .addChild(standardContext);
        tomcat.addServlet()*/
    }

}

