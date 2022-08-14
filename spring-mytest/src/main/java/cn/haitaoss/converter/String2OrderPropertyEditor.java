package cn.haitaoss.converter;

import cn.haitaoss.service.Order;

import java.beans.PropertyEditorSupport;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-14 15:31
 * 这个是 JDK 提供的接口，Spring对这个规范做了实现
 */
public class String2OrderPropertyEditor extends PropertyEditorSupport {
    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        Order order = new Order();
        order.setDesc(text);

        setValue(order);
    }

    public static void main(String[] args) {
        //  测试
        String2OrderPropertyEditor propertyEditor = new String2OrderPropertyEditor();
        propertyEditor.setAsText("123");
        Order order = (Order) propertyEditor.getValue();

        System.out.println(order.getDesc());
    }
}
