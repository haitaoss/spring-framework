package cn.haitaoss.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-30 11:45
 */
@Component
public class BService {
	@Autowired
	private AService aService;
}
