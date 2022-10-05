package cn.haitaoss;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.adapter.DefaultAdvisorAdapterRegistry;
import org.springframework.aop.support.DefaultPointcutAdvisor;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author haitao.chen
 * <p>
 * <p>
 * email haitaoss@aliyun.com
 * date 2022-09-24 12:53
 */
@Data
public class Demo {
    private String name;

    public static void main(String[] args) {
        Demo demo = new Demo();
        System.out.println(demo.getName());
        demo.test2();
        Optional.ofNullable(null).ifPresent(item -> {
        });
    }

    @Test
    public void 测试ProxyFactory() {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(new Demo());
        // 是否优化，这个也是决定是否使用cglib代理的条件之一
        proxyFactory.setOptimize(true);
        // 接口类型，这个也是决定是否使用cglib代理的条件之一，代理接口的时候才需要设置这个
        proxyFactory.setInterfaces();
        // 约束是否使用cglib代理。但是这个没吊用，会有其他参数一起判断的，而且有优化机制 会优先选择cglib代理
        proxyFactory.setProxyTargetClass(true);
        /**
         * addAdvice 会被装饰成 Advisor
         * 这里不能乱写，因为后面解析的时候 要判断是否实现xx接口的
         * 解析逻辑 {@link DefaultAdvisorAdapterRegistry#getInterceptors(Advisor)}
         * */
        proxyFactory.addAdvice(new MethodBeforeAdvice() {
            @Override
            public void before(Method method, Object[] args, Object target) throws Throwable {
                method.invoke(target, args);
            }
        });
        // 设置 Advisor，有点麻烦 还不如直接通过 addAdvice 设置，自动解析成advisor方便
        proxyFactory.addAdvisor(new DefaultPointcutAdvisor(new MethodBeforeAdvice() {
            @Override
            public void before(Method method, Object[] args, Object target) throws Throwable {
                method.invoke(target, args);
            }
        }));
        proxyFactory.getProxy();
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
