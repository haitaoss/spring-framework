package cn.haitaoss.converter;

import cn.haitaoss.service.Order;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.Collections;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-14 15:40
 */
public class String2OrderConverter2 implements ConditionalGenericConverter {
    @Override
    public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
        // 可以支持多种类型的转换
        return sourceType.getType()
                       .equals(String.class) && targetType.getType()
                       .equals(Order.class);
    }

    @Override
    public Set<ConvertiblePair> getConvertibleTypes() {
        return Collections.singleton(new ConvertiblePair(String.class, Order.class));
    }

    @Override
    public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
        Order order = new Order();
        order.setDesc((String) source);
        return order;
    }

    public static void main(String[] args) {
        // 测试
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(new String2OrderConverter2());

        Order order = defaultConversionService.convert("123", Order.class);
        System.out.println(order.getDesc());
    }
}
