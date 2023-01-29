package cn.haitaoss.javaconfig.EnableTransactionManagement;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-20 20:40
 * 事务传播行为
 */
@EnableTransactionManagement
@EnableAspectJAutoProxy(exposeProxy = true)
@Component
@Import(Config.class)
public class EnableTransactionManagementTest6 {
    @Autowired
    public JdbcTemplate jdbcTemplate;

    @Autowired
    public ApplicationEventMulticaster multicaster;


    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void test(Propagation propagation) {
        System.out.println("数据库->" + jdbcTemplate.queryForObject("SELECT database()", String.class));
        jdbcTemplate.execute("TRUNCATE TABLE t1");
        jdbcTemplate.execute("INSERT INTO t1 VALUES('haitao')");

        switch (propagation.value()) {
            case TransactionDefinition.PROPAGATION_NESTED:
                ((EnableTransactionManagementTest6) AopContext.currentProxy()).nested();
                break;
            case TransactionDefinition.PROPAGATION_REQUIRES_NEW:
                ((EnableTransactionManagementTest6) AopContext.currentProxy()).requires_new();
                break;
            case TransactionDefinition.PROPAGATION_REQUIRED:
                ((EnableTransactionManagementTest6) AopContext.currentProxy()).required();
                break;
            case TransactionDefinition.PROPAGATION_NOT_SUPPORTED:
                ((EnableTransactionManagementTest6) AopContext.currentProxy()).not_supported();
                break;
        }
        jdbcTemplate.execute("INSERT INTO t1 VALUES('haitao2')");
        //        throw new RuntimeException("抛出异常");
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NOT_SUPPORTED)
    public void not_supported() {
        //        seeTable();
        jdbcTemplate.execute("INSERT INTO t1 VALUES('not_supported-->haitao')");
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void required() {
        //        seeTable();
        jdbcTemplate.execute("INSERT INTO t1 VALUES('required-->haitao')");
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.NESTED)
    public void nested() {
        //        seeTable();
        jdbcTemplate.execute("INSERT INTO t1 VALUES('NESTED-->haitao')");
    }

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW/*,isolation = Isolation.READ_UNCOMMITTED*/)
    public void requires_new() {
        //        seeTable();
        jdbcTemplate.execute("INSERT INTO t1 VALUES('REQUIRES_NEW-->haitao')");
    }

    @Transactional(propagation = Propagation.NEVER)
    public void test1() {
        jdbcTemplate.execute("INSERT INTO t1 VALUES('NEVER-->haitao')");
        jdbcTemplate.execute("INSERT INTO t1 VALUES('NEVER-->haitao')");
        jdbcTemplate.execute("INSERT INTO t1 VALUES('NEVER-->haitao')");
        seeTable();
    }

    public void seeTable() {
        System.out.println("表记录" + jdbcTemplate.queryForList("SELECT * FROM t1", String.class));
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableTransactionManagementTest6.class);
        EnableTransactionManagementTest6 bean = context.getBean(EnableTransactionManagementTest6.class);
        // bean.test_show_db();// 测试动态数据源
        try {
            //            bean.seeTable();
            //            bean.test(Propagation.NESTED);
            //                        bean.test(Propagation.REQUIRES_NEW);
            //            bean.test(Propagation.REQUIRED);
            //            bean.test(Propagation.NOT_SUPPORTED);
            //            bean.test1();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
        }
        //        bean.seeTable();
    }
}
