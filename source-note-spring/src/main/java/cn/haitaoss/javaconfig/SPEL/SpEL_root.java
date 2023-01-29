package cn.haitaoss.javaconfig.SPEL;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.function.Function;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-09 20:06
 *
 */
public class SpEL_root {
    public String name;

    public static void main(String[] args) {
        // 构造器参数就是根对象
        StandardEvaluationContext context = new StandardEvaluationContext(new SpEL_root()) {
            @Override
            public Object lookupVariable(String name) {
                /**
                 * 访问变量会执行这个
                 * 就是 "#a" 才会执行
                 * 注："#root" 这个比较特殊，不是回调该方法获取的
                 * */
                System.out.print("lookupVariable--->" + name + "===>");
                return super.lookupVariable(name);
            }
        };
        ExpressionParser parser = new SpelExpressionParser();
        Function<String, Object> consumer = exp -> parser.parseExpression(exp).getValue(context);

        /**
         * #root.name 访问根对象的属性
         * 访问root对象的属性，可以简化成 name
         * #root 是访问根对象，因为root是关键字，不会回调 lookupVariable 获取变量
         * */
        System.out.println(consumer.apply("name"));
        System.out.println(consumer.apply("#root"));
        System.out.println(consumer.apply("#root.name"));

    }
}
