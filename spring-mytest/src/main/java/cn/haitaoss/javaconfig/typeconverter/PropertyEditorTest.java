package cn.haitaoss.javaconfig.typeconverter;

import lombok.Data;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.CustomEditorConfigurer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.util.HashMap;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-07 14:57
 */
@Component
public class PropertyEditorTest {


    @Bean
    public A a() {
        return new A();
    }

    @Data
    public static class A {
        private String name;

        @Autowired
        public void x(@Value("hello world!!!") A a) {
            System.out.println(a);
        }
    }

    @Bean
    public CustomEditorConfigurer myCustomEditorConfigurer() {
        CustomEditorConfigurer customEditorConfigurer = new CustomEditorConfigurer();

        Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>();
        customEditors.put(A.class, MyPropertyEditor.class);
        customEditorConfigurer.setCustomEditors(customEditors);

        customEditorConfigurer.setPropertyEditorRegistrars(new PropertyEditorRegistrar[]{new PropertyEditorRegistrar() {
            @Override
            public void registerCustomEditors(PropertyEditorRegistry registry) {
                registry.registerCustomEditor(A.class, new MyPropertyEditor());
                registry.registerCustomEditor(A.class, null, new MyPropertyEditor());
                if (registry instanceof SimpleTypeConverter) {
                    SimpleTypeConverter.class.cast(registry).overrideDefaultEditor(A.class, new MyPropertyEditor());
                }
            }
        }});

        return customEditorConfigurer;
    }

    public static class MyPropertyEditor extends PropertyEditorSupport {
        @Override
        public void setValue(Object value) {
            if (value instanceof String) {
                A a = new A();
                a.setName((String) value);
            }
            super.setValue(value);
        }

        @Override
        public void setAsText(String text) throws IllegalArgumentException {
            A a = new A();
            a.setName(text);
            this.setValue(a);
        }

        public static void main(String[] args) {
            /**
             * 使用就是 setValue(Object) setAsText(String) 设置转换值
             * getValue() 就是拿到转换的结果
             * */
            MyPropertyEditor myPropertyEditor = new MyPropertyEditor();
            //        myPropertyEditor.setAsText("hah");
            myPropertyEditor.setValue("hah");
            System.out.println(myPropertyEditor.getValue());
        }
    }

    public static void main(String[] args) throws Exception {
        new AnnotationConfigApplicationContext(PropertyEditorTest.class);
    }

}