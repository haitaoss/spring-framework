package cn.haitaoss.javaconfig.populateBean;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-13 11:18
 *
 */
//@Data
public class Test_AUTOWIRE_BY_TYPE {
    private List<Object> beanList;
    private List<Object> beanList2;

    public void setBeanList(List<Object> beanList) {
        System.out.println("setBeanList = " + beanList.size());
    }

    public void setBean(List<Object> beanList) {
        System.out.println("setBean = " + beanList.size());
    }

    public void xx(List<Object> beanList) {
        System.out.println("xx = " + beanList.size());
    }

    public static void main(String[] args) {
        AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition().getBeanDefinition();
        beanDefinition.setBeanClass(Test_AUTOWIRE_BY_TYPE.class);
        // 测试这个的效果，是会遍历属性进行依赖注入吗，不需要写注解？？？
        // TODOHAITAO: 2023/1/13
        // 根据类型依赖注入
        /**
         * 会对 setX() 方法 进行依赖注入 类似于 @Autowired的效果,
         *
         * {@link AbstractAutowireCapableBeanFactory#autowireByType(String, AbstractBeanDefinition, BeanWrapper, MutablePropertyValues)}
         * */
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBeanDefinition("Test_AUTOWIRE_BY_TYPE", beanDefinition);
        context.refresh();


        Test_AUTOWIRE_BY_TYPE bean = context.getBean(Test_AUTOWIRE_BY_TYPE.class);
        System.out.println("beanList2 = " + bean.beanList2.size());
        System.out.println("bean = " + bean.beanList.size());
    }
}
