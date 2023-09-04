package cn.haitaoss.listener;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.ServletRequestHandledEvent;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-18 14:57
 */
@Component
public class RequestEventListener implements ApplicationListener<ServletRequestHandledEvent> {
    @Override
    public void onApplicationEvent(ServletRequestHandledEvent event) {
        System.out.println(System.currentTimeMillis() + "--->" + event);
    }
}
