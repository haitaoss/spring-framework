package cn.haitaoss.javaconfig.populateBean;

import lombok.Data;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-07 14:57
 *
 */
@Component
@Data
@Import({MyBeanFactoryPostProcessor.class, MyBeanPostProcessor.class})
public class Test_Dependency {
    @Autowired
    private A a;
    @Autowired
    private B b;
    @Autowired
    private B b2;
    private B b_from_xml;
    @Autowired
    private ApplicationContext applicationContext;

    public static void main(String[] args) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Test_Dependency.class);
        System.out.println("context = " + context);
        System.out.println(context.getBean(Test_Dependency.class));
//        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));
    }
}

class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 依赖，相当于BeanFactory的扩展，不是bean但是依赖注入可以注入这些类型
        beanFactory.registerResolvableDependency(A.class, new A());
        beanFactory.registerResolvableDependency(B.class, new B());
        // 看看啥时候会启用这个属性
        beanFactory.ignoreDependencyType(B.class);
    }
}

class MyBeanPostProcessor implements MergedBeanDefinitionPostProcessor {

    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        if (Test_Dependency.class.isAssignableFrom(beanType)) {
            /**
             * {@link AbstractAutowireCapableBeanFactory#populateBean(String, RootBeanDefinition, BeanWrapper)}
             * */
            // 检查是否是忽略的依赖
            beanDefinition.setDependencyCheck(AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE);
            // 模拟xml设置属性
            MutablePropertyValues propertyValues = beanDefinition.getPropertyValues();
            propertyValues.addPropertyValue("b_from_xml", new B());
            propertyValues.addPropertyValue("applicationContext", new AnnotationConfigApplicationContext());
        }

    }
}

class A {
}

class B {
}