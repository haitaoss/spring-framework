package cn.haitaoss.javaconfig.SPEL;

import org.springframework.expression.*;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.function.Function;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-09 20:06
 *
 */
public class SpEL_PropertyAccessor {
    public String name;

    public static void main(String[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext(new SpEL_PropertyAccessor());
        ExpressionParser parser = new SpelExpressionParser();
        Function<String, Object> consumer = exp -> parser.parseExpression(exp).getValue(context);

        /**
         * PropertyAccessor 用来解析属性是怎么取值的
         *
         * 就是 "a" 才会执行
         * */
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
                System.out.print("canRead...." + name + "\t");
                return true;
            }

            @Override
            public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
                System.out.print("read...." + name + "\t");
                return new TypedValue(name);
            }

            @Override
            public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
                return false;
            }

            @Override
            public void write(EvaluationContext context, Object target, String name, Object newValue) throws AccessException {

            }
        });

        System.out.println(consumer.apply("name"));
        System.out.println(consumer.apply("x"));
        System.out.println(consumer.apply("#variable"));
    }
}
