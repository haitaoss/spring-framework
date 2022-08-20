package cn.haitaoss;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.lang.Nullable;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:51
 */
@ComponentScan("cn.haitaoss")
@Import(DefaultAdvisorAutoProxyCreator.class)
@Configuration // 让AppConfig是代理对象
@ImportResource("classpath:spring.xml")
public class AppConfig {
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("message"); // 国际化文件的，文件名省略后缀
        return messageSource;
    }

   /* @Bean // TODOHAITAO: 2022/8/14 1. 首先注册一个 BeanPostProcessor类型的bean 到容器中
    public DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        // TODOHAITAO: 2022/8/14 由于这个只是往容器中添加一个bean，所以我们可以使用 @Import的方式实现
        return new DefaultAdvisorAutoProxyCreator();
    }*/

    // @Bean // TODOHAITAO: 2022/8/14 2. 设置增强器（切入点和增强内容）
    public DefaultPointcutAdvisor defaultPointcutAdvisor() {
        NameMatchMethodPointcut nameMatchMethodPointcut = new NameMatchMethodPointcut();
        nameMatchMethodPointcut.addMethodName("test");

        DefaultPointcutAdvisor defaultPointcutAdvisor = new DefaultPointcutAdvisor();
        // defaultPointcutAdvisor.setPointcut(nameMatchMethodPointcut);
        defaultPointcutAdvisor.setPointcut(new Pointcut() {
            @Override
            public ClassFilter getClassFilter() {
                return new ClassFilter() {
                    @Override
                    public boolean matches(Class<?> clazz) {
                        return !clazz.equals(AppConfig.class);
                    }
                };
            }

            @Override
            public MethodMatcher getMethodMatcher() {
                return new MethodMatcher() {
                    @Override
                    public boolean matches(Method method, Class<?> targetClass) {
                        return !targetClass.getSuperclass()
                                .equals(AppConfig.class);
                    }

                    @Override
                    public boolean isRuntime() {
                        return false;
                    }

                    @Override
                    public boolean matches(Method method, Class<?> targetClass, Object... args) {
                        return !targetClass.equals(AppConfig.class);
                    }
                };
            }
        });
        defaultPointcutAdvisor.setAdvice(haitaoAroundAdvice());

        return defaultPointcutAdvisor;
    }


    /*@Bean // TODOHAITAO: 2022/8/14  BeanNameAutoProxyCreator 根据bean名字来判断是否创建代理对象
    public BeanNameAutoProxyCreator beanNameAutoProxyCreator() {
        BeanNameAutoProxyCreator beanNameAutoProxyCreator = new BeanNameAutoProxyCreator();
        beanNameAutoProxyCreator.setBeanNames("userS*"); // 代理那些bean，支持通配符
        beanNameAutoProxyCreator.setInterceptorNames("haitaoAroundAdvice");
        beanNameAutoProxyCreator.setProxyTargetClass(true); // 使用 cglib 代理
        return beanNameAutoProxyCreator;
    }

    @Bean
    public Advice haitaoAroundAdvice() {
        return new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                System.out.println("before...");
                Object result = invocation.proceed();
                System.out.println("after...");
                return result;
            }
        };
    }*/
    @Bean
    public Advice haitaoAroundAdvice() {
        return new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                System.out.println("before...");
                Object result = invocation.proceed();
                System.out.println("after...");
                return result;
            }
        };
    }

/*    @Bean // TODOHAITAO: 2022/8/14 通过 ProxyFactoryBean 创建代理对象
    public ProxyFactoryBean userServiceProxy() {
        UserService userService = new UserService();

        ProxyFactoryBean proxyFactoryBean = new ProxyFactoryBean();
        proxyFactoryBean.setTarget(userService);
        proxyFactoryBean.addAdvice(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                System.out.println("before...");
                Object result = invocation.proceed();
                System.out.println("after...");
                return result;
            }
        });

        return proxyFactoryBean;
    }*/

    /*@Bean
    public CustomEditorConfigurer customEditorConfigurer() {
        CustomEditorConfigurer customEditorConfigurer = new CustomEditorConfigurer();

        Map<Class<?>, Class<? extends PropertyEditor>> propertyEditorMap = new HashMap<>();

        // 发现当前对象是String， 而需要的类型是 Order 就会使用该 PropertyEditor 进行转换（缺点很明显只支持值是String的时候）
        propertyEditorMap.put(Order.class, String2OrderPropertyEditor.class);
        customEditorConfigurer.setCustomEditors(propertyEditorMap);

        return customEditorConfigurer;
    }*/

    /*@Bean
    public ConversionServiceFactoryBean conversionService222() {
        ConversionServiceFactoryBean conversionServiceFactoryBean = new ConversionServiceFactoryBean();
        conversionServiceFactoryBean.setConverters(Collections.singleton(new String2OrderConverter2())); // 可以注册多个

        return conversionServiceFactoryBean;
    }*/
}
