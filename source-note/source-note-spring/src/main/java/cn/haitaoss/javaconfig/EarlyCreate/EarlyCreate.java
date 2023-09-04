package cn.haitaoss.javaconfig.EarlyCreate;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.type.AnnotationMetadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-12 09:53
 * 指定的bean会是最先创建的bean (在 BeanFactoryPostProcesso 、BeanPostProcessor、MessageSource、ApplicationEventMulticaster 之后创建)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(EarlyCreate.EarlyCreateDeferredImportSelector.class)
public @interface EarlyCreate {
    String[] value() default {};

    class EarlyCreateDeferredImportSelector implements DeferredImportSelector {
        @Override
        public Class<? extends Group> getImportGroup() {
            //            return EarlyCreateGroup.class;
            return DeferredImportSelector.super.getImportGroup();
        }

        @Override
        public String[] selectImports(AnnotationMetadata importingClassMetadata) {
            return new String[]{EarlyCreateBeanPostProcessor.class.getName()};
        }

        @Override
        public Predicate<String> getExclusionFilter() {
            return DeferredImportSelector.super.getExclusionFilter();
        }
    }

    class EarlyCreateBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware, ImportAware {
        private BeanFactory beanFactory;

        @Override
        public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
            this.beanFactory = beanFactory;
        }

        @Override
        public void setImportMetadata(AnnotationMetadata importMetadata) {
            Optional.ofNullable(importMetadata.getAnnotationAttributes(EarlyCreate.class.getName()))
                    .map(map -> map.get("value"))
                    .map(item -> (String[]) item)
                    .ifPresent(beanNames -> Stream.of(beanNames)
                            .forEach(beanFactory::getBean));
        }
    }


    // 会有默认的，没有特殊需求可以不设置
    class EarlyCreateGroup implements DeferredImportSelector.Group {
        @Override
        public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {

        }

        @Override
        public Iterable<Entry> selectImports() {
            return null;
        }
    }

}
