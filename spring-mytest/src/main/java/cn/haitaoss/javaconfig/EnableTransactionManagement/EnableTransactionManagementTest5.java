package cn.haitaoss.javaconfig.EnableTransactionManagement;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static cn.haitaoss.javaconfig.EnableTransactionManagement.Config.currentDB;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-20 20:40
 * 嵌套事务的方式解决事务内无法切换数据源问题
 * 原理：如果子事务是新开的，会先将父事务给暂停(就是移除事务资源)，所以子事务获取连接就可以通过DataSource在get一个，而不是从事务资源缓存中取
 */
@EnableTransactionManagement
@EnableAspectJAutoProxy(exposeProxy = true)
@Component
@Import(Config.class)
public class EnableTransactionManagementTest5 {
    @Autowired
    public JdbcTemplate jdbcTemplate;

    @Transactional
    public void test_show_db(Propagation propagation) {
        showDB();
        EnableTransactionManagementTest5 currentProxy = (EnableTransactionManagementTest5) AopContext.currentProxy();
        currentDB.set("db2");
        switch (propagation.value()) {
            case TransactionDefinition.PROPAGATION_NESTED:
                currentProxy.nested();
                break;
            case TransactionDefinition.PROPAGATION_REQUIRES_NEW:
                currentProxy.requires_new();
                break;
            case TransactionDefinition.PROPAGATION_REQUIRED:
                currentProxy.required();
                break;
            case TransactionDefinition.PROPAGATION_NOT_SUPPORTED:
                currentProxy.not_supported();
                break;
        }
        currentDB.remove();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void not_supported() {
        showDB();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void required() {
        showDB();
    }

    @Transactional(propagation = Propagation.NESTED)
    public void nested() {
        showDB();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requires_new() {
        showDB();
    }

    private void showDB() {
        System.out.println("数据库->" + jdbcTemplate.queryForObject("select database()", String.class));
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableTransactionManagementTest5.class);
        EnableTransactionManagementTest5 bean = context.getBean(EnableTransactionManagementTest5.class);
        System.out.println("---->NESTED");
        bean.test_show_db(Propagation.NESTED); // 不可以
        System.out.println("---->REQUIRES_NEW");
        bean.test_show_db(Propagation.REQUIRES_NEW); // 可以
        System.out.println("---->REQUIRED");
        bean.test_show_db(Propagation.REQUIRED); // 不可以
        System.out.println("---->NOT_SUPPORTED");
        bean.test_show_db(Propagation.NOT_SUPPORTED); // 可以
    }
}
