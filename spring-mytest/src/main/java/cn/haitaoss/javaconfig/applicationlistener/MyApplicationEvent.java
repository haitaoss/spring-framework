package cn.haitaoss.javaconfig.applicationlistener;

import org.springframework.context.ApplicationEvent;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 13:39
 */
public class MyApplicationEvent extends ApplicationEvent {
    private static final long serialVersionUID = 7099057708183571937L;

    public MyApplicationEvent(Object source) {
        super(source);
    }

}
