package cn.haitaoss;

import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.adapter.DefaultAdvisorAdapterRegistry;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.BeanUtils;
import org.springframework.core.GenericTypeResolver;
import org.springframework.expression.*;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
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

    @Test
    public void test_spel() {
        Demo demo = new Demo();

        ExpressionParser parser = new SpelExpressionParser();

        StandardEvaluationContext context = new StandardEvaluationContext(demo);
        context.setVariable("newName", "Mike Tesla");

        Function<String, Object> consumer = exp -> parser.parseExpression(exp).getValue(context);
        // 字符串
        System.out.println(consumer.apply("'a'"));
        // 运算
        System.out.println(consumer.apply("1+1"));
        /**
         * name 表示 context中的属性名
         * #newName 表示获取变量
         *
         * 意思就是给name赋值
         * */
        System.out.println(consumer.apply("name = #newName"));
        /**
         * name变量的值，这样子写就是访问属性
         * */
        System.out.println(consumer.apply("name"));
        /**
         * 设置BeanResolver,就是 @ 开头的会通过这个解析值
         * */
        context.setBeanResolver(new BeanResolver() {
            @Override
            public Object resolve(EvaluationContext context, String beanName) throws AccessException {
                return "通过BeanResolver解析的值-->" + beanName;
            }
        });
        // 会使用BeanResolver 解析
        System.out.println(consumer.apply("@a"));
        // 模板解析上下文，就是可以去掉模板字符
        System.out.println(parser.parseExpression("#{@x}", new TemplateParserContext()).getValue(context));
        // PropertyAccessor 用来解析属性是怎么取值的
        context.addPropertyAccessor(new PropertyAccessor() {
            @Override
            public Class<?>[] getSpecificTargetClasses() {
                //                return new Class[0];
                /**
                 * 返回 null，表示都满足
                 * 不会null，就会匹配 EvaluationContext 类型，匹配了才会使用这个 PropertyAccessor
                 * {@link PropertyOrFieldReference#readProperty(TypedValue, EvaluationContext, String)}
                 *  {@link PropertyOrFieldReference#getPropertyAccessorsToTry(Object, List)}
                 * */
                return null;
            }

            @Override
            public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
                System.out.println("canRead...." + name);
                return true;
            }

            @Override
            public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
                System.out.println("read...." + name);
                return null;
            }

            @Override
            public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
                return false;
            }

            @Override
            public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {

            }
        });
        System.out.println(consumer.apply("testPropertyAccessor"));
    }

    public static void main(String[] args) {
        Demo demo = new Demo();
        System.out.println(demo.getName());
        demo.test2();
        Optional.ofNullable(null).ifPresent(item -> {
        });
    }

    @Test
    public void test_compoentType() {
        Object[] objects = {};
        System.out.println(objects.getClass().getComponentType());

        String[] strings = {};
        System.out.println(strings.getClass().getComponentType());


        System.out.println(Array.newInstance(String.class, 0).getClass());
    }

    @Test
    public void test构造器工具类() {
        Constructor<Demo> primaryConstructor = BeanUtils.findPrimaryConstructor(Demo.class);
        System.out.println("primaryConstructor = " + primaryConstructor);
    }

    interface interfaceA<T> {
    }

    @Test
    public void test泛型接口工具() {
        {
            class A<T> {
            }
            class B extends A<String> implements interfaceA<Integer> {
            }

            Class<?> t1 = GenericTypeResolver.resolveTypeArgument(B.class, A.class);
            System.out.println(t1);

            Class<?> t2 = GenericTypeResolver.resolveTypeArgument(B.class, interfaceA.class);
            System.out.println(t2);
        }
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
