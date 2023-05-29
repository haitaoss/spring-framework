package cn.haitaoss.javaconfig.applicationlistener;

import org.springframework.beans.BeansException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 13:36
 */
public class MyAnnotationConfigApplicationContext extends AnnotationConfigApplicationContext {
	public MyAnnotationConfigApplicationContext(Class<?>... componentClasses) {
		super(componentClasses);
	}

	@Override
	protected void onRefresh() throws BeansException {
		publishEvent(new MyApplicationEvent("beanDefinition 全部加载完了，可以自定义bean加载顺序了") {
			@Override
			public Object getSource() {
				return super.getSource();
			}
		});
	}
}
