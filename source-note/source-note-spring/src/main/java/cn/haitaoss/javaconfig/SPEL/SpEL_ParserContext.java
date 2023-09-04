package cn.haitaoss.javaconfig.SPEL;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-09 20:06
 *
 */
public class SpEL_ParserContext {
    public static void main(String[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        ExpressionParser parser = new SpelExpressionParser();

        // 模板解析上下文，就是可以去掉模板字符
        System.out.println(parser.parseExpression("#{#variable}",
                new TemplateParserContext()).getValue(context));
    }
}
