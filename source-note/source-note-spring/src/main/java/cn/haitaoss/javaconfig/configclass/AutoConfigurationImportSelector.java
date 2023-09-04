package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * 实现 DeferredImportSelector 目的是延时加载，从而尽可能的保证自动装配的bean进行
 * @Conditional 判断时，项目中其他的bean已经加载完了
 */
public class AutoConfigurationImportSelector implements DeferredImportSelector {

    @Override
    public Class<? extends Group> getImportGroup() {
        return AutoConfigurationGroup.class;
    }

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        System.out.println("不会执行的--->cn.haitaoss.javaconfig.configclass.MyDeferredImportSelector.selectImports");
        return new String[0];
    }

    @Override
    public Predicate<String> getExclusionFilter() {
        return null;
    }

    protected Class<?> getSpringFactoriesLoaderFactoryClass() {
        return EnableAutoConfiguration.class;
    }

    private static class AutoConfigurationGroup implements Group {
        private final Map<String, AnnotationMetadata> entries = new LinkedHashMap<>();

        @Override
        public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
            SpringFactoriesLoader.loadFactoryNames(((AutoConfigurationImportSelector) selector).getSpringFactoriesLoaderFactoryClass(), ClassLoader.getSystemClassLoader())
                    .forEach(item -> {
                        entries.put(item, metadata);
                    });
        }

        @Override
        public Iterable<Entry> selectImports() {
            // 这里返回的内容才会作为配置类解析的
            return entries.entrySet()
                    .stream()
                    .map(item -> new Entry(item.getValue(), item.getKey()))
                    .collect(Collectors.toList());
        }
    }
}