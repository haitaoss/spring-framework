package cn.haitaoss.javaconfig.Qualifier;


import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */

@Component
@Data
public class Main {
    @Autowired
    @Qualifier
    private String msg;
    @Autowired
    @Qualifier("1")
    private List<String> list1;
    @Autowired
    @Qualifier
    private List<String> list2;

    @Bean
    @Qualifier("1")
    public String o1() {
        return new String("o1");
    }

    @Bean
    @Qualifier("1")
    public String o11() {
        return new String("o11");
    }

    @Bean
    @Qualifier
    public String o2() {
        return new String("o2");
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Main.class);
        Main bean = context.getBean(Main.class);
        System.out.println(bean);
    }
}


