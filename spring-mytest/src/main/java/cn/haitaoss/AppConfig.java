package cn.haitaoss;

import cn.haitaoss.converter.String2OrderConverter2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.ConversionServiceFactoryBean;

import java.util.Collections;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:51
 */
@ComponentScan("cn.haitaoss")
public class AppConfig {

    /*@Bean
    public CustomEditorConfigurer customEditorConfigurer() {
        CustomEditorConfigurer customEditorConfigurer = new CustomEditorConfigurer();

        Map<Class<?>, Class<? extends PropertyEditor>> propertyEditorMap = new HashMap<>();

        // 发现当前对象是String， 而需要的类型是 Order 就会使用该 PropertyEditor 进行转换（缺点很明显只支持值是String的时候）
        propertyEditorMap.put(Order.class, String2OrderPropertyEditor.class);
        customEditorConfigurer.setCustomEditors(propertyEditorMap);

        return customEditorConfigurer;
    }*/

    @Bean
    public ConversionServiceFactoryBean conversionService222() {
        ConversionServiceFactoryBean conversionServiceFactoryBean = new ConversionServiceFactoryBean();
        conversionServiceFactoryBean.setConverters(Collections.singleton(new String2OrderConverter2())); // 可以注册多个

        return conversionServiceFactoryBean;
    }
}
