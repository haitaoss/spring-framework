package cn.haitaoss.bean;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-27 21:12
 */
@Data
@Component
public class User {
	private String name;
	private Integer age;

	public User() {
	}
}
