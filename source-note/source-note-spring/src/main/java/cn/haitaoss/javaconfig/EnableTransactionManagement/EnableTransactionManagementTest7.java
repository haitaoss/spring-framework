package cn.haitaoss.javaconfig.EnableTransactionManagement;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.AbstractTransactionManagementConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-20 20:40
 * 事务事件，在事务完成时会将事件发布到事务监听器
 */
@EnableTransactionManagement
@EnableAspectJAutoProxy(exposeProxy = true)
@Component
@Import(Config.class)
public class EnableTransactionManagementTest7 {
    @Autowired
    public JdbcTemplate jdbcTemplate;

    @Autowired
    public ApplicationEventMulticaster multicaster;

    // @Transactional(propagation = Propagation.NEVER)
    @Transactional(propagation = Propagation.REQUIRED)
    public void test_transaction_event() {
        /**
         * 必须在有事务的情况下发布，才能注册到事务同步资源中 {@link TransactionSynchronizationManager#synchronizations},
         * 然后在事务结束之后，才发布数据到 @TransactionalEventListener
         * */
        multicaster.multicastEvent(new ApplicationEvent("自定义的事务事件") {
            @Override
            public Object getSource() {
                return super.getSource();
            }
        });
        System.out.println("test_transaction_event事务结束了...");
    }

    // @TransactionalEventListener(fallbackExecution = true) // 不在事务下发布的事件也处理
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void receive(ApplicationEvent applicationEvent) {
        System.out.println("接受到事务完成事件-->" + applicationEvent);
    }

    /**
     * 默认的 ListenerFactory bean，之前写的太死了不支持扩展 SynchronizationCallback
     * {@link AbstractTransactionManagementConfiguration#transactionalEventListenerFactory()}
     * */
    //    @Component
    public static class MyTransactionalEventListenerFactory extends TransactionalEventListenerFactory {

        @Override
        public ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method) {
            ApplicationListener<?> applicationListener = super.createApplicationListener(beanName, type, method);
            if (applicationListener instanceof TransactionalApplicationListenerMethodAdapter) {
                TransactionalApplicationListenerMethodAdapter listener = (TransactionalApplicationListenerMethodAdapter) applicationListener;
                // 设置callback
                listener.addCallback(new TransactionalApplicationListener.SynchronizationCallback() {
                    @Override
                    public void preProcessEvent(ApplicationEvent event) {
                        System.out.println("preProcessEvent-->" + event);
                    }

                    @Override
                    public void postProcessEvent(ApplicationEvent event, Throwable ex) {
                        System.out.println("postProcessEvent-->" + event);
                    }
                });
            }
            return applicationListener;
        }
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableTransactionManagementTest7.class);
        EnableTransactionManagementTest7 bean = context.getBean(EnableTransactionManagementTest7.class);

        bean.test_transaction_event();
    }
}
