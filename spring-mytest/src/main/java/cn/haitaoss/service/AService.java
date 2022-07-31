package cn.haitaoss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-30 11:45
 */
@Component
public class AService {
	@Autowired
	@Lazy
	private BService bService;

	@Async
	public void test() {
		System.out.println(bService);
	}
}
