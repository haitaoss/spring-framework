package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.UnwrapByDefault;
import jakarta.validation.valueextraction.Unwrapping;
import jakarta.validation.valueextraction.ValueExtractor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.internal.engine.ValidatorImpl;
import org.hibernate.validator.internal.engine.groups.Group;
import org.hibernate.validator.internal.engine.groups.ValidationOrder;
import org.hibernate.validator.internal.metadata.core.MetaConstraint;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-23 15:39
 * 值提取器
 * https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-valueextraction-builtinvalueextractors
 */
public class validate_value_extractor {
    public static void main(String[] args) {
        /**
         * To extract values from a custom container, one needs to implement a ValueExtractor
         * Implementing a ValueExtractor is not enough, you also need to register it. See Section 7.5, “Registering a ValueExtractor” for more details.
         *
         * Registering a ValueExtractor
         *
         * value extractor (值提取器)，约束注解标注在对象上，但是想对对象的某个值进行校验，这时候就需要
         * value extractor 来提取值
         * */
        // TODOHAITAO: 2022/11/23  很奇怪 不校验 @Min @Max
        Validator validator = Validation.buildDefaultValidatorFactory()
                .usingContext()
                // 扩展自定义的 值提取器
                .addValueExtractor(new UnwrapByDefaultValueExtractor())
                .getValidator();

/**
 * 值不为null，才会使用值提取器进行校验
 * {@link ValidatorImpl#validate(Object, Class[])}
 * {@link ValidatorImpl#validateInContext(org.hibernate.validator.internal.engine.ValidationContext, org.hibernate.validator.internal.engine.ValueContext, ValidationOrder)}
 * {@link ValidatorImpl#validateConstraintsForCurrentGroup(org.hibernate.validator.internal.engine.ValidationContext, org.hibernate.validator.internal.engine.ValueContext)}
 * {@link ValidatorImpl#validateConstraintsForDefaultGroup(org.hibernate.validator.internal.engine.ValidationContext, org.hibernate.validator.internal.engine.ValueContext)}
 * {@link ValidatorImpl#validateConstraintsForSingleDefaultGroupElement(org.hibernate.validator.internal.engine.ValidationContext, org.hibernate.validator.internal.engine.ValueContext, Map, Class, Set, Group)}
 * 遍历约束信息  MetaConstraint<?> metaConstraint : metaConstraints
 *  {@link ValidatorImpl#validateMetaConstraint(org.hibernate.validator.internal.engine.ValidationContext, org.hibernate.validator.internal.engine.ValueContext, MetaConstraint)}
 *  {@link MetaConstraint#validateConstraint(org.hibernate.validator.internal.engine.ValidationContext, org.hibernate.validator.internal.engine.ValueContext)}
 *      if valueExtractionPath != null // 这个是遍历注册的 ValueExtractor 类型匹配了，就不是null
 *          拿到要检验的值valueToValidate，valueToValidate != null 才进行校验
 *       else // regular constraint
 *           doValidateConstraint( validationContext, valueContext );
 *
 * */
        print_constraint_info(validator.validate(new A()));
        print_constraint_info(validator.validate(new A(OptionalInt.of(1), OptionalInt.of(2), OptionalInt.of(3), new A())));
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

    @Data
    @NoArgsConstructor
    public static class A {
        @Min(value = 5, payload = Unwrapping.Unwrap.class)
        private OptionalInt optionalInt1;

        @Min(5)
        private OptionalInt optionalInt2;

        @NotNull(payload = Unwrapping.Skip.class) // 默认是会拆包，这里的意思是不要拆包
        @Min(5)
        private OptionalInt optionalInt3;

        @Length(min = 10)
        private A self;
        public String arg;

        public A(OptionalInt optionalInt1, OptionalInt optionalInt2, OptionalInt optionalInt3, A self) {
            this.optionalInt1 = optionalInt1;
            this.optionalInt2 = optionalInt2;
            this.optionalInt3 = optionalInt3;
            this.self = self;
        }
    }


    @UnwrapByDefault
    public static class UnwrapByDefaultValueExtractor
            implements ValueExtractor<@ExtractedValue(type = String.class) A> {

        @Override
        public void extractValues(A originalValue, ValueReceiver receiver) {
            System.out.println("UnwrapByDefaultValueExtractor.extractValues-->");
            receiver.value(null, originalValue.arg == null ? null : originalValue.arg);
        }
    }
}
