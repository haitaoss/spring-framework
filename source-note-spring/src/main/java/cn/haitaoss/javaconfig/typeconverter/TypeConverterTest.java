package cn.haitaoss.javaconfig.typeconverter;

import lombok.Data;
import org.springframework.beans.SimpleTypeConverter;

import java.beans.PropertyEditorSupport;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-16 16:45
 *
 */
public class TypeConverterTest {
    @Data
    public static class A {
        private String name;
    }

    public static void main(String[] args) {
        SimpleTypeConverter simpleTypeConverter = new SimpleTypeConverter();

        simpleTypeConverter.registerCustomEditor(A.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) throws IllegalArgumentException {
                A a = new A();
                a.setName(text);
                super.setValue(a);
            }
        });
        /*DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(new Converter<String, A>() {
            @Override
            public A convert(String source) {
                A a = new A();
                a.setName(source);
                return a;
            }
        });
        simpleTypeConverter.setConversionService(conversionService);*/
        System.out.println(simpleTypeConverter.convertIfNecessary("123", A.class));
    }
}
