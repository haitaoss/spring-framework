package cn.haitaoss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-27 21:56
 */
@Component
public class UserService {
	private OrderService orderService;


	/*public UserService() {
		System.out.println(0);
	}*/

	@Autowired
	public UserService(OrderService orderService) { // byType --> byName Map<beanName,Bean对象>
		this.orderService = orderService;
		System.out.println(1);
	}

	public UserService(OrderService orderService, OrderService orderService1) {
		this.orderService = orderService;
		System.out.println(2);
	}

	/*@Autowired
	private JdbcTemplate jdbcTemplate;

	@Transactional
	public void test() {
		jdbcTemplate.execute("insert into t1 values (1, 1, 1, 1, '1')");
		throw new RuntimeException();
	}*/
}
