package cn.haitaoss;


import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */

@Component
public class Test {
    public static void main(String[] args) throws Exception {
        // ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("spring.xml");
        // AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.Test.class);
        //        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        /*ConfigurableEnvironment environment = context.getEnvironment();
        environment.addActiveProfile("uat");
        System.out.println(environment.acceptsProfiles(Profiles.of("!prod")));
        System.out.println(environment.acceptsProfiles(Profiles.of("prod")));
        System.out.println("==============");*/

        test_parent_son_context();
        /**
         * BeanPostProcessor执行过程：
         * 实例化前后置->推断构造器后置->实例化bean->合并BeanDefinition后置->实例化后后置->属性注入后置->初始化前后置->初始化后后置->销毁前后置(hit)
         * */
        /**
         * 已经掌握的注解
         *
         * @Configuration、@ComponentScan、@ComponentScans、@Import、@ImportResource、@Component、@Bean、@Scope、@DependsOn、@Conditional
         *
         * @Lazy、@Autowired、@Resource、@Value、@Qualifier、@Primary、@PostConstruct、@PreDestroy、@Lookup
         *
         * @EnableAspectJAutoProxy、@Aspect 、@Before 、@After、@AfterThrowing、@AfterReturning、@Around、@DeclareParents、@Pointcut
         *
         * @EnableTransactionManagement、@Transactional、@TransactionalEventListener
         *
         * @EnableAsync、@Async
         *
         * @EnableScheduling、@Scheduled
         *
         * @EventListener
         *
         * @EnableCaching @CacheConfig、@Cacheable、@CacheEvict、@CachePut、@Caching
         *
         * @Profile
         *
         * @PropertySource
         *
         * @EnableLoadTimeWeaving
         *
         * 待掌握 aspectj 相关的 没看懂
         * https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop-using-aspectj
         * https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop-aj-ltw
         *
         * @EnableSpringConfigured @Configurable
         * */
    }

    public static void test_parent_son_context() {
        /**
         * 结论，父子容器会记录相同的BeanDefinition，也就是会重复加载。
         * 如果父子容器中都存在同一个BeanDefinition，那么在getBean时，返回的是子容器中的bean
         *
         *
         * 看这里就明白了 {@link AbstractBeanFactory#doGetBean(String, Class, Object[], boolean)}
         *      只有存在父容器 且 当前容器中不存在beanName的BeanDefinition 才会执行 parent.getBean
         * */

        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext(AppConfig.class);

        System.out.println(parent.getBean(AppConfig.class)
                .hashCode()); // 返回的是子容器的

        AnnotationConfigApplicationContext son = new AnnotationConfigApplicationContext(AppConfig.class);
        son.setParent(parent);

        System.out.println(son.getBean(AppConfig.class)
                .hashCode()); // 返回的是子容器的
    }


}


