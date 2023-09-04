package cn.haitaoss.scope;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-17 21:40
 *
 */
@Scope(WebApplicationContext.SCOPE_APPLICATION)
@Component
public class ApplicationBean {}
