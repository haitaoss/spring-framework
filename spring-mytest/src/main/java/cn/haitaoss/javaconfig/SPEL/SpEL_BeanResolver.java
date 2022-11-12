package cn.haitaoss.javaconfig.SPEL;

import org.springframework.expression.AccessException;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.function.Function;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-09 20:06
 *
 */
public class SpEL_BeanResolver {

    public static void main(String[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        ExpressionParser parser = new SpelExpressionParser();
        Function<String, Object> consumer = exp -> parser.parseExpression(exp).getValue(context);

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
        System.out.println(parser.parseExpression("#{@x}",
                new TemplateParserContext()).getValue(context));
    }
}
