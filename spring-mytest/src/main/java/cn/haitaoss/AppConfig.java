package cn.haitaoss;

import cn.haitaoss.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:51
 */
@ComponentScan("cn.haitaoss")
// @ComponentScan(value = "cn.haitaoss", nameGenerator = HaitaoBeanNameGenerator.class) // 指定beanName生成规则
// @ComponentScan(value = "cn.haitaoss", excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {UserService.class})}) // 指定排除规则
@Configuration
// @Import(HaitaoImportSelector.class)
public class AppConfig {
    @Bean
    public UserService userService() {
        test();
        // orderService();
        return new UserService();
    }

    public void test() {
        System.out.println("test");
    }

   /* @Bean
    public OrderService orderService() {
        return new OrderService();
    }*/
}
