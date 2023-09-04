package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.spi.ValidationProvider;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.Unwrapping;
import jakarta.validation.valueextraction.ValueExtractor;
import lombok.Data;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.spi.scripting.AbstractCachingScriptEvaluatorFactory;
import org.hibernate.validator.spi.scripting.ScriptEvaluationException;
import org.hibernate.validator.spi.scripting.ScriptEvaluator;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-24 14:28
 *
 */
@Data
public class validate_factory {
    public static void main(String[] args) {
        /*// default
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.getValidator();*/

        /*// using a specific provider
        ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class)
                .configure()
                .buildValidatorFactory();
        Validator validator = validatorFactory.getValidator();*/

        /*// 指定 解析器 反正是各种协议 ，不懂这些规范
        ValidatorFactory validatorFactory = Validation.byDefaultProvider()
                .providerResolver(new OsgiServiceDiscoverer())
                .configure()
                .buildValidatorFactory();
        Validator validator = validatorFactory.getValidator();*/


        ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class)
                .configure()
                // 指定脚本评估工厂，就是处理 @ScriptAssert(script = "value > 0", lang = "spring")
                .scriptEvaluatorFactory(new SpringELScriptEvaluatorFactory())
               /* // 就是配置文件开发，把对类的约束信息，配置到xml里面（好鸡肋啊，写注解不是很方便吗）
                .addMapping((InputStream) null)*/
                // 就是匹配错了，就别匹配后面的约束注解了
                .failFast(true)
                .addProperty("hibernate.validator.fail_fast", "true")
                .addProperty("hibernate.validator.show_validated_value_in_trace_logs", "true")
                .buildValidatorFactory();

        Validator validator = validatorFactory.usingContext()
                // 通过查询 TraversableResolver 接口可以访问哪些属性，哪些属性不能访问
                .traversableResolver(new MyTraversableResolver())
                // 解析模板的。可以在方法内对占位符做国际化的解析
                .messageInterpolator(new MyMessageInterpolator())
                // 约束校验器工厂，用来生成 ConstraintValidator 的
                // .constraintValidatorFactory(new MyConstraintValidatorFactory())
                // 参数名字解析器
                // .parameterNameProvider(new MyParameterNameProvider())
                // 值提取器（就是校验注解标注在对象上，但是其校验的是对象的某个属性，这时候就需要使用值提取器来提取对象的属性）
                .addValueExtractor(new UnwrapByDefaultValueExtractor())
                .getValidator();

        validate_factory bean = new validate_factory();
        bean.self = new validate_factory();
        bean.self2 = new validate_factory();
        print_constraint_info(validator.validate(bean));
    }

    private static void print_constraint_info(Set<ConstraintViolation<Object>> violations) {
        System.out.println("===========================");
        for (ConstraintViolation<Object> item : violations) {
            System.out.print(item.getPropertyPath() + "\t");
            // 错误信息
            System.out.print(item.getMessage() + "\t");
            // 约束类型
            System.out.print(item.getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType());
            System.out.println();
        }
    }

    // @Length(min = 2,payload = Unwrapping.Unwrap.class)
    @Length(min = 2)
    public validate_factory self;

    @NotNull(payload = Unwrapping.Skip.class)
    public validate_factory self2;

    public String name;

    @UnwrapByDefault
    public static class UnwrapByDefaultValueExtractor
            implements ValueExtractor<@ExtractedValue(type = String.class) validate_factory> {

        @Override
        public void extractValues(validate_factory originalValue, ValueReceiver receiver) {
            System.out.println("UnwrapByDefaultValueExtractor.extractValues-->");
            receiver.value(null, originalValue.name == null ? null : originalValue.name);
        }
    }

    public static class MyTraversableResolver implements TraversableResolver {

        @Override
        public boolean isReachable(
                Object traversableObject,
                Path.Node traversableProperty,
                Class<?> rootBeanType,
                Path pathToTraversableObject,
                ElementType elementType) {
            System.out.println("isReachable-->" + traversableProperty);
            return true;
        }

        @Override
        public boolean isCascadable(
                Object traversableObject,
                Path.Node traversableProperty,
                Class<?> rootBeanType,
                Path pathToTraversableObject,
                ElementType elementType) {
            System.out.println("isCascadable-->" + traversableProperty);
            return false;
        }
    }

    public static class MyMessageInterpolator implements MessageInterpolator {

        @Override
        public String interpolate(String messageTemplate, Context context) {
            System.out.println("interpolate-->" + messageTemplate);
            interpolate(messageTemplate, context, Locale.CHINA);
            return null;
        }

        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            System.out.println("interpolate-Locale-->" + messageTemplate);
            return null;
        }
    }

    public static class SpringELScriptEvaluatorFactory extends AbstractCachingScriptEvaluatorFactory {

        @Override
        public ScriptEvaluator createNewScriptEvaluator(String languageName) {
            if (!"spring".equalsIgnoreCase(languageName)) {
                throw new IllegalStateException("Only Spring EL is supported");
            }

            return new SpringELScriptEvaluator();
        }

        private static class SpringELScriptEvaluator implements ScriptEvaluator {

            private final ExpressionParser expressionParser = new SpelExpressionParser();

            @Override
            public Object evaluate(String script, Map<String, Object> bindings) throws ScriptEvaluationException {
                try {
                    Expression expression = expressionParser.parseExpression(script);
                    EvaluationContext context = new StandardEvaluationContext(bindings.values().iterator().next());
                    for (Map.Entry<String, Object> binding : bindings.entrySet()) {
                        context.setVariable(binding.getKey(), binding.getValue());
                    }
                    return expression.getValue(context);
                } catch (ParseException | EvaluationException e) {
                    throw new ScriptEvaluationException("Unable to evaluate SpEL script", e);
                }
            }
        }
    }

    public static class MyParameterNameProvider implements ParameterNameProvider {

        @Override
        public List<String> getParameterNames(Constructor<?> constructor) {
            //...
            return null;
        }

        @Override
        public List<String> getParameterNames(Method method) {
            //...
            return null;
        }
    }

    public static class MyConstraintValidatorFactory implements ConstraintValidatorFactory {

        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            //...
            return null;
        }

        @Override
        public void releaseInstance(ConstraintValidator<?, ?> instance) {
            //...
        }
    }

    public static class OsgiServiceDiscoverer implements ValidationProviderResolver {

        @Override
        public List<ValidationProvider<?>> getValidationProviders() {
            //...
            return null;
        }
    }

}
