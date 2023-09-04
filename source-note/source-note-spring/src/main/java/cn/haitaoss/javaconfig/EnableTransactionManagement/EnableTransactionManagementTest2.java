package cn.haitaoss.javaconfig.EnableTransactionManagement;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-20 20:40
 */
@EnableTransactionManagement(mode = AdviceMode.PROXY, order = 1)
@Configuration
@Import(Config.class)
public class EnableTransactionManagementTest2 {

    public void test1() {
        ProxyFactory pf = new ProxyFactory();
        pf.addAdvisor(null);
        pf.getProxy();
    }

    /**
     * 测试事务回滚失败场景
     *
     * 首先对方法的增强都是通过Advisor接口实现的，具体的增强逻辑是 Advisor的Advice。
     *
     * 拿到Advisor：
     * 1. 直接从容器中拿到Advisor类型的bean，添加到集合中
     * 2. 解析@Aspect类 中的AOP注解 生成Advisor，添加到集合中
     *
     * 对集合 List<Advisor> 进行升序排序：可以通过 @Order 指定排序值，值越小的先执行，值相同就不动(默认策略)。
     *
     * 而事务的实现就是往容器中注入Advisor类型的bean，所以其添加到集合中的顺序肯定比AOP的Advisor要早。
     *
     * 当我们不给切面类和事务注解设置排序值，该默认值是Integer的最大值，又因为事务比切面类先添加的，所以在默认情况下
     * 事务的Advice会比切面类先执行，所以方法抛出异常时最先捕获到的是 AOP，如果AOP不把异常往上抛出，则事务Advice就捕获不到异常，从而不会回滚。
     *
     * 但是，当我们给切面类设置排序值，让其优先于事务Advice先执行时，就可以保证 事务Advice是最先接触到异常的从而可以及时回滚。
     */
    /*@Component
    @EnableAspectJAutoProxy*/
    @Aspect
    @Order(-1) // 指定排序值，让AOP先执行，从而保证最先拿到异常的是事务Advice 从而能及时回滚
    public class MyAspect {
        @Around("execution(* *test2(..))")
        public void around(ProceedingJoinPoint proceedingJoinPoint) {
            try {
                proceedingJoinPoint.proceed();
            } catch (Throwable e) {
                System.err.println("MyAspect--->捕获到异常不抛出，所以事务不会回滚，异常信息：" + e.getMessage());
            }
        }
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static DefaultIntroductionAdvisor MyAdvisor() {
        return new DefaultIntroductionAdvisor(new MethodInterceptor() {
            @Nullable
            @Override
            public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
                try {
                    return invocation.proceed();
                } catch (Throwable e) {
                    System.err.println("MyAdvisor--->捕获到异常不抛出，所以事务不会回滚，异常信息：" + e.getMessage());
                    return null;
                }
            }
        }) {
            @Override
            public boolean matches(Class<?> clazz) {
                return EnableTransactionManagementTest2.class.isAssignableFrom(clazz);
            }

            @Override
            public int getOrder() {
                return -1;
            }
        };
    }

    @Autowired
    public JdbcTemplate jdbcTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void test2() {
        System.out.println("数据库->" + jdbcTemplate.queryForObject("select database()", String.class));
        jdbcTemplate.execute("truncate table t1");
        jdbcTemplate.execute("insert into t1 values('haitao')");
        throw new RuntimeException("抛出异常");
    }


    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableTransactionManagementTest2.class);
        context.getBean(EnableTransactionManagementTest2.class).test2();
        System.out.println("表记录" + context.getBean(JdbcTemplate.class).queryForList("select * from t1", String.class));
    }
}
