package cn.haitaoss.javaconfig.Scope;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.stereotype.Component;

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
    @Component
    public static class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
            beanFactory.registerScope("haitao", new HaitaoScope());
        }
    }

    @org.springframework.context.annotation.Scope("haitao")
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

