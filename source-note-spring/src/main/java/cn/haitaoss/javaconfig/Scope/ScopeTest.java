package cn.haitaoss.javaconfig.Scope;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-06 16:42
 *
 */
@Component
public class ScopeTest {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ScopeTest.class);
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));
        /**
         * 使用 @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
         * 其实会映射成两个 BeanDefinition：
         *  第一个的beanClass 是 当前类本身，而其beanName是 "scopedTarget. + beanName"
         *  第二个的beanClass 是 ScopedProxyFactoryBean，而其beanName 是 beanName
         *
         * 举例的处理逻辑：
         *  {@link ClassPathBeanDefinitionScanner#doScan(String...)}
         *  {@link ConfigurationClassBeanDefinitionReader#registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass)}
         * */
        System.out.println(context.getBean("scopedTarget.cn.haitaoss.javaconfig.Scope.ScopeTest$Demo"));
        System.out.println(context.getBean("cn.haitaoss.javaconfig.Scope.ScopeTest$Demo"));
    }

    @Component
    public static class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            beanFactory.registerScope("haitao", new HaitaoScope());
        }
    }

    @org.springframework.context.annotation.Scope(value = "haitao", proxyMode = ScopedProxyMode.TARGET_CLASS)
    @Component
    public static class Demo {
    }

    public static class HaitaoScope implements Scope {
        static Map<String, Object> cache = new ConcurrentHashMap<>();

        @Override
        public Object get(String name, ObjectFactory<?> objectFactory) {
            return cache.computeIfAbsent(name, x -> objectFactory.getObject());
        }

        @Override
        public Object remove(String name) {
            return cache.remove(name);
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback) {

        }

        @Override
        public Object resolveContextualObject(String key) {
            return null;
        }

        @Override
        public String getConversationId() {
            return null;
        }

        public static void refresh() {
            cache.clear();
        }
    }
}

