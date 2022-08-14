package cn.haitaoss;


import cn.haitaoss.service.UserService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
public class Test {
    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        UserService bean = context.getBean(UserService.class);
        bean.test();
        /*UserService userServiceProxy = (UserService) context.getBean("userServiceProxy");
        userServiceProxy.test();*/


        /*
        // TODOHAITAO: 2022/8/14 通过 ProxyFactory 创建代理对象
        UserService userService = new UserService();

        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(userService);
        proxyFactory.setProxyTargetClass(true); // true：cglib代理；false：jdk代理

        proxyFactory.addAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                System.out.println("before...");
                Object result = invocation.proceed();
                System.out.println("after...");
                return result;
            }
        });
        UserService proxy = (UserService) proxyFactory.getProxy();

        System.out.println(proxy);*/


        /*// 测试 TypeConverter
        SimpleTypeConverter typeConverter = new SimpleTypeConverter();

        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(new String2OrderConverter2());
        typeConverter.setConversionService(defaultConversionService); // Spring 的 ConversionService

        typeConverter.registerCustomEditor(Order.class, new String2OrderPropertyEditor()); // jdk 的 PropertyEditor

        Order order = typeConverter.convertIfNecessary("123", Order.class); // 会判断那个可以用就用那个（优先使用 ConversionService ）
        System.out.println("order = " + order);*/

    }
}