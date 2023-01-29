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
public class SpEL_lookupVariable {
    public static void main(String[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext() {
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
        // 设置变量
        context.setVariable("newName", "Mike Tesla");

        ExpressionParser parser = new SpelExpressionParser();
        Function<String, Object> consumer = exp -> parser.parseExpression(exp).getValue(context);

        /**
         * name变量的值，这样子写就是访问属性。
         *
         * root对象的访问方式：
         *  1. name
         *  2. #name
         *  3. #root.name
         *
         * 普通变量的访问方式：
         *  1. #newName
         * */
        System.out.println(consumer.apply("#name"));
        System.out.println(consumer.apply("#root"));
        System.out.println(consumer.apply("#newName"));

    }
}
