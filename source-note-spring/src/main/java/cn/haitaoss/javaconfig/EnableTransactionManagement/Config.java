package cn.haitaoss.javaconfig.EnableTransactionManagement;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NamedThreadLocal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.function.Function;

/**
 * 动态数据源+事务管理配置
 */
@Configuration
public class Config {
    public static ThreadLocal<String> currentDB = new NamedThreadLocal<>("动态数据源key");

    //    @Bean
    public TransactionManagementConfigurer transactionManagementConfigurer() {
        return new TransactionManagementConfigurer() {
            @Override
            public TransactionManager annotationDrivenTransactionManager() {
                JdbcTransactionManager jdbcTransactionManager = new JdbcTransactionManager();
                jdbcTransactionManager.setDataSource(dynamicDataSource());
                return jdbcTransactionManager;
            }
        };
    }

    @Bean
    public TransactionManager transactionManager() {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager();
        dataSourceTransactionManager.setDataSource(dynamicDataSource());
        return dataSourceTransactionManager;
    }

    @Bean
    public JdbcTemplate jdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(dynamicDataSource());
        return jdbcTemplate;
    }

    @Bean
    public AbstractRoutingDataSource dynamicDataSource() {
        AbstractRoutingDataSource dataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return currentDB.get();
            }
        };
        HashMap<Object, Object> map = new HashMap<>();
        map.put("db1", dataSource1());
        map.put("db2", dataSource2());
        dataSource.setTargetDataSources(map);
        dataSource.setDefaultTargetDataSource(dataSource1());
        return dataSource;
    }

    @Bean
    public DataSource dataSource1() {
        return genDs.apply("d1");
    }

    @Bean
    public DataSource dataSource2() {
        return genDs.apply("d2");
    }

    public Function<String, DataSource> genDs = dbName -> {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        druidDataSource.setUrl(String.format("jdbc:mysql://localhost:3306/%s?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowMultiQueries=true", dbName));
        druidDataSource.setUsername("root");
        druidDataSource.setPassword("root");
        return druidDataSource;
    };
}