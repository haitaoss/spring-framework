package cn.haitaoss;


import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {
    public static void main(String[] args) throws Exception {

        // 父子 ApplicationContext
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        AnnotationConfigApplicationContext context1= new AnnotationConfigApplicationContext(AppConfig.class);
        context1.setParent(context1);

        /*// 验证 父子 BeanFactory
        DefaultListableBeanFactory defaultListableBeanFactory = new DefaultListableBeanFactory();
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition()
                .getBeanDefinition();
        beanDefinition.setBeanClass(OrderService.class);
        defaultListableBeanFactory.registerBeanDefinition("orderService", beanDefinition);


        DefaultListableBeanFactory defaultListableBeanFactory1 = new DefaultListableBeanFactory();
        AbstractBeanDefinition beanDefinition1 = BeanDefinitionBuilder.genericBeanDefinition()
                .getBeanDefinition();
        beanDefinition1.setBeanClass(UserService.class);
        defaultListableBeanFactory1.registerBeanDefinition("userService", beanDefinition1);
        defaultListableBeanFactory1.setParentBeanFactory(defaultListableBeanFactory);


        System.out.println(defaultListableBeanFactory1.getBean("orderService"));*/

        /*// 验证父子BeanDefinition
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        System.out.println(context.getBean("orderService"));
        System.out.println(context.getBean("orderService"));*/
    }
}