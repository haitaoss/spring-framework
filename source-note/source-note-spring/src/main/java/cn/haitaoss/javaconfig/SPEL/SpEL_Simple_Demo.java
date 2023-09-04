package cn.haitaoss.javaconfig.SPEL;

import lombok.Data;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.function.Function;

@Data
public class SpEL_Simple_Demo {
    private String name;

    public static void main(String[] args) {
        // 构造器参数就是根对象
        StandardEvaluationContext context = new StandardEvaluationContext(new SpEL_Simple_Demo());
        context.setVariable("newName", "Mike Tesla");

        ExpressionParser parser = new SpelExpressionParser();
        Function<String, Object> consumer = exp -> parser.parseExpression(exp).getValue(context);
        // 字符串
        System.out.println(consumer.apply("'a'"));
        // 运算
        System.out.println(consumer.apply("1+1"));
        // 给root对象的属性赋值
        System.out.println(consumer.apply("name = #newName"));
        // 给变量赋值
        System.out.println(consumer.apply("#newName = 'haitao'"));

        System.out.println(consumer.apply("#newName2")); // 没有这个变量也不会报错，就是null而已

    }
}