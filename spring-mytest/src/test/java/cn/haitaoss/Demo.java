package cn.haitaoss;

import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-24 12:53
 *
 */
@Data
public class Demo {
    private String name;

    public static void main(String[] args) {
        Demo demo = new Demo();
        System.out.println(demo.getName());
        demo.test2();
    }

    @Test
    public void test1() {
        /**
         * 类名::实例方法
         * --> 匿名内部类
         * class $X{
         *  method(类的实例,...)
         * }
         * */
        Function<Demo, ?> f = Demo::getName;
        BiConsumer<Demo, String> bc = Demo::setName;
    }

    @Test
    public void test2() {
        MyConsumer<String> x = this::test2;
        /*Optional.of("hahah")
                // .ifPresent(x::wrapperConsumer);
                .ifPresent(x::wrapperConsumer2);
*/
        Consumer<String> x2 = x::wrapperConsumer2;// 这个是支持的，返回值不需要而已;
        Consumer<String> x3 = x::wrapperConsumer3;

        Function<String, ?> f = x::wrapperConsumer2;
    }

    public void test2(String s) throws Exception {
        System.out.println(s);
    }

}

interface MyConsumer<T> {
    void accept(T t) throws Exception;

    default void wrapperConsumer(T t) {
        try {
            accept(t);
        } catch (Exception ignored) {

        }

    }

    default Consumer<T> wrapperConsumer2(T t) {
        System.out.println("--->" + t);
        return item -> {
            try {
                accept(t);
            } catch (Exception ignored) {

            }
        };
    }

    default Function<T, T> wrapperConsumer3(T t) {
        System.out.println("--->" + t);
        return item -> t;
    }
}
