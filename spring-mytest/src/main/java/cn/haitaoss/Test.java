package cn.haitaoss;

import cn.haitaoss.mapper.OrderMapper;
import cn.haitaoss.mapper.UserMapper;
import cn.haitaoss.service.UserService;
import cn.springmybatis.HaitaoFactoryBean;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 *
 */
public class Test {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(AppConfig.class);


        // 动态注册 BeanDefinition
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition()
                .getBeanDefinition();

        beanDefinition.setBeanClass(HaitaoFactoryBean.class);
        beanDefinition.getConstructorArgumentValues()
                .addGenericArgumentValue(UserMapper.class);
        context.registerBeanDefinition("userMapper", beanDefinition);

        AbstractBeanDefinition beanDefinition1 = BeanDefinitionBuilder.genericBeanDefinition()
                .getBeanDefinition();

        beanDefinition1.setBeanClass(HaitaoFactoryBean.class);
        beanDefinition1.getConstructorArgumentValues()
                .addGenericArgumentValue(OrderMapper.class);
        context.registerBeanDefinition("orderMapper", beanDefinition1);


        context.refresh();

        System.out.println(context.getBean("userMapper"));
        System.out.println(context.getBean("orderMapper"));
        System.out.println(context.getBean(UserMapper.class));
        System.out.println(context.getBean(OrderMapper.class));
        System.out.println(context.getBean("&userMapper"));
        System.out.println(context.getBean("&orderMapper"));
        System.out.println(context.getBeansOfType(HaitaoFactoryBean.class));


        UserService userService = (UserService) context.getBean("userService");
        userService.printName();

    }
}