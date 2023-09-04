package cn.haitaoss.javaconfig.EnableTransactionManagement;

import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.framework.autoproxy.BeanFactoryAdvisorRetrievalHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.*;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.ProxyTransactionManagementConfiguration;
import org.springframework.transaction.annotation.TransactionManagementConfigurationSelector;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-20 20:40
 *
 */
@EnableTransactionManagement(mode = AdviceMode.PROXY)
public class EnableTransactionManagementTest {
    /**
     * 使用`@EnableTransactionManagement` 会导入`@Import(TransactionManagementConfigurationSelector.class)`
     *
     * 解析配置类在解析@Import的时候，因为 {@link TransactionManagementConfigurationSelector} 的父类 AdviceModeImportSelector 实现了 {@link ImportSelector}
     *     所以会回调这个方法 {@link AdviceModeImportSelector#selectImports(AnnotationMetadata)}
     *      - 拿到注解 `@EnableTransactionManagement(mode = AdviceMode.PROXY)` 的mode属性值，回调子类方法 {@link TransactionManagementConfigurationSelector#selectImports(AdviceMode)}
     *
     *
     * 执行 {@link TransactionManagementConfigurationSelector#selectImports(AdviceMode)}
     *      该方法返回这两个类 AutoProxyRegistrar、ProxyTransactionManagementConfiguration, 也就是会将其添加到 BeanDefinitionMap中。
     *
     * AutoProxyRegistrar {@link AutoProxyRegistrar}
     *      继承 ImportBeanDefinitionRegistrar，所以解析@Import时会回调 {@link AutoProxyRegistrar#registerBeanDefinitions(AnnotationMetadata, BeanDefinitionRegistry)}
     *      该方法会执行 {@link AopConfigUtils#registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry)} 也就是会注册
     *      `InfrastructureAdvisorAutoProxyCreator` 到容器中。而这个类主要是拿到容器中 Advisor 类型的bean，且 Role 是 {@link BeanDefinition#ROLE_INFRASTRUCTURE} 才作为 Advisor，
     *      然后遍历Advisor 判断处理的bean是否符合条件，符合就创建代理对象
     *
     *      {@link BeanFactoryAdvisorRetrievalHelper#findAdvisorBeans()}
     *
     * ProxyTransactionManagementConfiguration {@link ProxyTransactionManagementConfiguration}
     *      继承 AbstractTransactionManagementConfiguration，该类会依赖注入
     *
     **/
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableTransactionManagementTest.class);
    }
}
