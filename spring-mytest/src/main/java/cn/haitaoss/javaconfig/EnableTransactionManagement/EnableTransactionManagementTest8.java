package cn.haitaoss.javaconfig.EnableTransactionManagement;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static cn.haitaoss.javaconfig.EnableTransactionManagement.Config.currentDB;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-20 20:40
 * 在事务内切换数据源
 */
@EnableTransactionManagement
@EnableAspectJAutoProxy(exposeProxy = true)
@Component
@Import(Config.class)
public class EnableTransactionManagementTest8 {
    @Autowired
    public JdbcTemplate jdbcTemplate;

    public void test_show_db() {
        EnableTransactionManagementTest8 currentProxy = (EnableTransactionManagementTest8) AopContext.currentProxy();
        System.out.println("开启事务的情况----->");
        currentProxy.transaction_showDb();
        System.out.println("不开启事务的情况----->");
        currentProxy.no_transaction_showDb();
    }

    @Transactional
    public void transaction_showDb() {
        /**
         *  在事务内动态数据源无效的。因为一开事物就会使用DataSource作为key缓存连接在 resources(事务资源) 中
         *  而 JdbcTemplate 依赖了Spring事务，即会执行 {@link DataSourceUtils#getConnection(DataSource)} 获取连接，
         *  会使用 DataSource 作为key从 resources 中查询资源，找到就返回。因为开启了事务所以多次获取是无效的，都是返回resources中缓存的值。
         * */
        showDB();
    }

    public void no_transaction_showDb() {
        /**
         *  由于 JdbcTemplate 依赖了Spring事务，即会执行 {@link DataSourceUtils#getConnection(DataSource)} 获取连接，
         *  会使用 DataSource 作为key从 resources 中查询资源，找到就返回。没开启事务所以从resources中找不到，所以会执行
         *  {@link AbstractRoutingDataSource#getConnection()} 获取连接，又因为不开启事务，所以并不会将连接缓存到 resource、synchronizations 中，
         *  所以每次执行SQL，都会 {@link AbstractRoutingDataSource#getConnection()}，从而每次都是新连接
         * */
        showDB();
    }

    private void showDB() {
        currentDB.set("db1");
        System.out.println("数据库->" + jdbcTemplate.queryForObject("select database()", String.class));
        currentDB.set("db2");
        System.out.println("数据库->" + jdbcTemplate.queryForObject("select database()", String.class));
    }

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(EnableTransactionManagementTest8.class);
        EnableTransactionManagementTest8 bean = context.getBean(EnableTransactionManagementTest8.class);
        bean.test_show_db();// 测试动态数据源
    }
}
