package cn.haitaoss.javaconfig.SPEL;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-09 17:01
 *
 */
public class MethodSpELTest {
    public static class MyStandardEvaluationContext extends StandardEvaluationContext {
        private Object[] methodArgs;
        private boolean resolved;

        public void setMethodArgs(Object[] methodArgs) {
            this.methodArgs = methodArgs;
        }

        @Override
        public Object lookupVariable(String name) {
            if (!resolved) {
                resolvedMethodArgs();
            }
            return super.lookupVariable(name);
        }

        private void resolvedMethodArgs() {
            for (int i = 0; i < methodArgs.length; i++) {
                setVariable("p" + i, methodArgs[i]);
                setVariable("a" + i, methodArgs[i]);
            }
            resolved = true;
        }
    }

    public static void method(String a, Object object) {
    }

    public static void main(String[] args) throws Exception {
        // 执行方法
        Class<MethodSpELTest> methodSpELTestClass = MethodSpELTest.class;
        Method method = methodSpELTestClass.getMethod("method", String.class, Object.class);

        Object[] methodArgs = {"hello SpEL", new Object()};
        method.invoke(null, methodArgs);

        // 讲方法的入参 传入 构造 StandardEvaluationContext
        ExpressionParser parser = new SpelExpressionParser();
        MyStandardEvaluationContext evaluationContext = new MyStandardEvaluationContext();
        evaluationContext.setMethodArgs(methodArgs);
        Function<String, Object> consumer = exp -> parser.parseExpression(exp).getValue(evaluationContext);

        // 进行表达式解析时 就能用到我们设置的变量了
        System.out.println(consumer.apply("#a0.contains('SpEL')"));
        System.out.println(consumer.apply("#a0.contains('haitao')"));
    }
}
