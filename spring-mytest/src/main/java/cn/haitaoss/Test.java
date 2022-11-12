package cn.haitaoss;


import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.*;
import org.springframework.cache.interceptor.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.util.StringValueResolver;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */

public class Test {
    public static void main(String[] args) throws Exception {
        // ClassPathXmlApplicationContext classPathXmlApplicationContext = new ClassPathXmlApplicationContext("spring.xml");
        // AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner.Test.class);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);

        /**
         * BeanPostProcessor执行过程：
         * 实例化前后置->推断构造器后置->实例化bean->合并BeanDefinition后置->实例化后后置->属性注入后置->初始化前后置->初始化后后置->销毁前后置(hit)
         *
         * 看看 {@link StringValueResolver#resolveStringValue(String)}
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
         * 待掌握
         * @Profile
         * @PropertySource
         * @EnableLoadTimeWeaving
         * */

        /**
         * {@link EnableCaching}
         * {@link CachingConfigurationSelector}
         *      InfrastructureAdvisorAutoProxyCreator
         *      {@link ProxyCachingConfiguration}
         *          {@link CachingConfigurer}
         *              {@link CacheManager}
         *              {@link CacheResolver}
         *              {@link KeyGenerator}
         *              {@link CacheErrorHandler}
         *
         *          {@link BeanFactoryCacheOperationSourceAdvisor}
         *          {@link AnnotationCacheOperationSource}
         *              {@link SpringCacheAnnotationParser}
         *              {@link CacheOperation}
         *
         *          {@link CacheInterceptor}
         *              {@link CacheAspectSupport.CacheOperationContexts}
         *              {@link CacheAspectSupport.CacheOperationContext}
         *              {@link CacheEvaluationContext}
         *
         *
         * */


    }


}


