package cn.haitaoss;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:51
 */
// @ComponentScan("cn.haitaoss.javaconfig.service")
// @ComponentScan(value = "cn.haitaoss.javaconfig.beanfactorypostprocessor", excludeFilters = {}, includeFilters = {})
// @ComponentScan("cn.haitaoss.javaconfig.factorybean")
// @ComponentScan("cn.haitaoss.javaconfig.dependson")
// @ComponentScan("cn.haitaoss.javaconfig.service")
// @ComponentScan("cn.haitaoss.javaconfig.create")
// @DependsOn({"user"})
// @Import({MyImportSelector.class, MyImportBeanDefinitionRegistrar.class})
// @ComponentScan("cn.haitaoss.javaconfig.circular")
public class AppConfig {
    public AppConfig() {
        System.out.println("AppConfig 构造器");
    }

}