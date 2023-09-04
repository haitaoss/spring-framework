package cn.haitaoss.javaconfig.PropertyPlaceholder;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-01 08:51
 *
 */
// @PropertySource("classpath:properties/My.properties")
@Component
@ImportResource("classpath:spring6.xml")
@Data
public class Test {
    @Value("${name}") // 使用的是 BeanFactory#resolveEmbeddedValue 来解析的
    private String name;
    @Autowired
    private Environment environment;
    @Autowired
    private DefaultListableBeanFactory beanFactory;

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Test.class);
        Test bean = context.getBean(Test.class);
        System.out.println("bean = " + bean.getName());

        // 不能获取使用  <context:property-placeholder/> 加载的属性文件的内容
        System.out.println(bean.getEnvironment().getProperty("name"));
        // 可以获取到 <context:property-placeholder/> + Environment 中的信息
        System.out.println(bean.getBeanFactory().resolveEmbeddedValue("${name}"));

    }
}
