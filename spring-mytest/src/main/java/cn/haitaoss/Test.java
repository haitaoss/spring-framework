package cn.haitaoss;


import cn.haitaoss.converter.String2OrderConverter2;
import cn.haitaoss.converter.String2OrderPropertyEditor;
import cn.haitaoss.service.Order;
import lombok.Data;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.core.convert.support.DefaultConversionService;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 16:53
 */
@Data
public class Test {
    public static void main(String[] args) throws Exception {
        /*AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        context.getBean(UserService.class)
                .say();*/


        // 测试 TypeConverter
        SimpleTypeConverter typeConverter = new SimpleTypeConverter();

        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(new String2OrderConverter2());
        typeConverter.setConversionService(defaultConversionService); // Spring 的 ConversionService

        typeConverter.registerCustomEditor(Order.class, new String2OrderPropertyEditor()); // jdk 的 PropertyEditor

        Order order = typeConverter.convertIfNecessary("123", Order.class); // 会判断那个可以用就用那个（优先使用 ConversionService ）
        System.out.println("order = " + order);

    }
}