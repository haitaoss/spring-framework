package cn.haitaoss.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.annotation.Annotation;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-20 12:10
 */
public class TestMetadataReader {
    public static void main(String[] args) throws IOException {
        SimpleMetadataReaderFactory simpleMetadataReaderFactory = new SimpleMetadataReaderFactory();

        // TODOHAITAO: 2022/8/20 因为是利用 ASM 直接解析的字节码，所以类并不会被加载到 JVM 中
        // 构造一个 MetadataReader
        MetadataReader metadataReader = simpleMetadataReaderFactory.getMetadataReader("cn.haitaoss.service.Test1");

        // 得到一个 ClassMetadata, 并获取了类名
        ClassMetadata classMetadata = metadataReader.getClassMetadata();
        System.out.println(classMetadata.getClassName());

        System.out.println("====");

        // 获取 AnnotationMetadata，并获取类上的注解的信息
        AnnotationMetadata annotationMetadata = metadataReader.getAnnotationMetadata();
        for (MergedAnnotation<Annotation> annotation : annotationMetadata.getAnnotations()) {
            System.out.println(annotation.getType());
        }
    }
}

@Component
@Configuration
class Test1 {
    static {
        System.out.println("Test1 被加载了");
    }
}

