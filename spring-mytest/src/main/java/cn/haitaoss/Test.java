package cn.haitaoss;


import cn.haitaoss.service.LookupService;
import lombok.Data;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
@Data
public class Test {
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        System.out.println(context.getBean(LookupService.class)
                .test());

        context.getBean(LookupService.class).test1();
        context.getBean(LookupService.class).test1();
        context.getBean(LookupService.class).test2();
        context.getBean(LookupService.class).test2();
        /*UserService userService = (UserService) context.getBean("userService");
        userService.test();

        System.out.println(context.getBean(UserService.MemberService2.class));
        context.getBean(UserService.MemberService1.class);*/


        //  ASM 工具的简单使用
        /*FileInputStream fis = new FileInputStream("/Users/haitao/Desktop/spring-framework/spring-mytest/build/classes/java/main/cn/haitaoss/AppConfig.class");
        ClassReader classReader = new ClassReader(fis);
        System.out.println(classReader.getClassName());
        System.out.println(classReader.getSuperName());*/
    }

}