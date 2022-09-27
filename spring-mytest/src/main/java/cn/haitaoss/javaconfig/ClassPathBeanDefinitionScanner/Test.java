package cn.haitaoss.javaconfig.ClassPathBeanDefinitionScanner;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Indexed;

import java.io.IOException;
import java.lang.annotation.*;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-27 22:13
 */
@Component
// @ComponentScan(includeFilters = {@ComponentScan.Filter(type = FilterType.CUSTOM, classes = MyAnnotationTypeFilter.class)})
@ComponentScan(includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Haitao.class)})
// @ComponentScan(includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, classes = MyAnnotation2.class)}) // 这个研究一下是杂用的
/**
 * FilterType.ANNOTATION 解析逻辑：{@link ComponentScanAnnotationParser#typeFiltersFor(AnnotationAttributes)}
 * */ public class Test {}

// @Haitao
class AService {}

@Indexed // 这个必须要有，否则无法左右 索引扫描的 类型
class MyAnnotation implements Annotation {
    @Override
    public Class<? extends Annotation> annotationType() {
        return MyAnnotation.class;
    }
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface Haitao {}

class MyAnnotation2 implements Annotation {
    @Override
    public Class<? extends Annotation> annotationType() {
        return Haitao.class;
    }
}

class MyAnnotationTypeFilter extends AnnotationTypeFilter {
    public MyAnnotationTypeFilter() {
        super(MyAnnotation.class);
    }

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        return true;
    }
}

