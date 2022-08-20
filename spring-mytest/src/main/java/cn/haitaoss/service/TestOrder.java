package cn.haitaoss.service;

import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.List;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-08-20 11:37
 */
public class TestOrder {
    public static void main(String[] args) {

        Demo demo = new Demo();
        Demo demo1 = new Demo();
        Demo demo2 = new Demo();

        demo.setOrder(3);
        demo1.setOrder(1);
        demo2.setOrder(2);

        List<Demo> list = Arrays.asList(demo, demo1, demo2);
        list.sort(new OrderComparator()); // 使用 Spring 提供的 Order 比较器进行排序，实例实现了 Ordered 接口就可以用这个
        // list.sort(new AnnotationAwareOrderComparator()); // 类实现了 Ordered 接口 或者 类标注了 @Order 就可以使用

        System.out.println(list);
    }
}


// @Order(1)
class Demo implements Ordered {
    private int order;

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String toString() {
        return "Demo{" + "order=" + order + '}';
    }
}
