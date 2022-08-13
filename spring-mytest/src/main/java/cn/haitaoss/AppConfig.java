package cn.haitaoss;

import org.springframework.context.annotation.ComponentScan;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:51
 */
@ComponentScan("cn.haitaoss")
// @ComponentScan(value = "cn.haitaoss", nameGenerator = HaitaoBeanNameGenerator.class) // 指定beanName生成规则
// @ComponentScan(value = "cn.haitaoss", excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {UserService.class})}) // 指定排除规则
public class AppConfig {}
