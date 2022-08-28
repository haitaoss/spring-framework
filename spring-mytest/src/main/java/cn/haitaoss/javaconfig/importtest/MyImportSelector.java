package cn.haitaoss.javaconfig.importtest;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-28 12:37
 */
public class MyImportSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        System.out.println("MyImportSelector#selectImports");
        return new String[]{};
    }
}
