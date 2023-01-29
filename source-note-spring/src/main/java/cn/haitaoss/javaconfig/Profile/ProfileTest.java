package cn.haitaoss.javaconfig.Profile;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;


/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-15 14:35
 *
 */
@Component
@Profile("haitao|haitao2|(haitao&haitao)") //支持的特殊符号： !、&、|、()
public class ProfileTest {

    @Bean
    @Profile("haitao")
    public static Object object() {
        return new Object();
    }

    public static void main(String[] args) {
        /**
         * 读取顺序是：系统属性 -> 环境变量 -> @PropertySource
         * 注：读到就返回，这也就是为啥 系统属性 大于 环境变量 大于 @PropertySource
         * 为啥是这样看：Spring是如何解析环境变量的就知道了 `context.getEnvironment().getProperty("name")`
         * */
        // 1. 系统环境变量设置激活的Profile：export SPRING_PROFILES_ACTIVE=profile1,
        // 2. JVM系统参数设置激活的Profile： -Dspring.profiles.active="profile1,profile2"
        // 3. Java代码设置激活的Profile
        // System.setProperty("spring.profiles.active", "hait  ao,haitao1,    haitao2"); // 会移除空格 + 支持使用','分割
        // System.setProperty("spring.profiles.default", "haitao");
        System.setProperty("spring.profiles.active", "haitao"); // default 和 active 同时设置，只会使用 active的值进行@Profile的匹配
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ProfileTest.class);
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));

        // 设置激活的 profile
        // context.getEnvironment().setActiveProfiles("development");
    }
}