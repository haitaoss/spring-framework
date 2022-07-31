package cn.haitaoss;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-27 21:08
 */
@ComponentScan("cn.haitaoss")
@EnableAsync
public class AppConfig { //  AppConfig代理对象  super.test()
	/*@Bean
	public OrderService orderService1() {
		return new OrderService();
	}

	@Bean
	public OrderService orderService2() {
		return new OrderService();
	}*/

	/*@Bean
	public JdbcTemplate jdbcTemplate() {
		return new JdbcTemplate(dataSource());
	}

	@Bean
	public PlatformTransactionManager transactionManager() {
		DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
		transactionManager.setDataSource(dataSource());
		return transactionManager;
	}

	@Bean
	public DataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
		dataSource.setUrl("jdbc:mysql://127.0.0.1:3306/test?characterEncoding=utf-8&useSSL=false");
		dataSource.setUsername("root");
		dataSource.setPassword("root");

		return dataSource;
	}*/

}
