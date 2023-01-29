package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;
import lombok.Data;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidator;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorInitializationContext;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-23 15:39
 * 自定义约束
 * https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#validator-customconstraints
 */
public class validate_customer_constraint {
    public static void main(String[] args) {

        /**
         * To create a custom constraint, the following three steps are required:
         * 创建自定义约束：
         *  1. 创建约束注解
         *  2. 实现 validator
         *  3. 定义默认错误消息
         * */


        /**
         * HibernateConstraintValidator 此扩展的目的是为 initialize ()方法提供更多上下文信息，因为在当前 ConstraintValidator 约定中，只有注释作为参数传递
         * */

        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        //invalid license plate
        print_constraint_info(validator.validate(new Car("Morris", "dd-ab-123", 4)));
        //valid license plate
        print_constraint_info(validator.validate(new Car("Morris", "DD-AB-123", 4)));
    }

    private static void print_constraint_info(Set<ConstraintViolation<Car>> violations) {
        System.out.println("===========================");
        for (ConstraintViolation<Car> item : violations) {
            // 错误信息
            System.out.println(item.getMessage());
            // 约束类型
            System.out.println(item.getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType());
        }
    }

    @Data
    public static class Car {

        @NotNull
        private String manufacturer;

        @NotNull
        @Size(min = 2, max = 14)
        @CheckCase("upper")
        private String licensePlate;

        @Min(2)
        private int seatCount;

        public Car(String manufacturer, String licencePlate, int seatCount) {
            this.manufacturer = manufacturer;
            this.licensePlate = licencePlate;
            this.seatCount = seatCount;
        }

        //getters and setters ...
    }

    @Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    //    @Constraint(validatedBy = {CheckCase.CheckCaseValidator.class, CheckCase.CheckCaseValidator2.class})
    @Constraint(validatedBy = {CheckCase.CheckCaseValidator.class})
    // @Constraint(validatedBy = {}) // 不指定约束的验证器，那就得设置在 validator 中
    //    @Constraint(validatedBy = {CheckCase.CheckCaseValidator2.class})
    @Documented
    @Repeatable(CheckCase.List.class)
    public @interface CheckCase {

        String message() default "{CheckCase.message}";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        String value() default "upper";

        @Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE})
        @Retention(RUNTIME)
        @Documented
        @interface List {
            CheckCase[] value();
        }

        class CheckCaseValidator implements ConstraintValidator<CheckCase, String> {

            private String caseMode;

            @Override
            public void initialize(CheckCase constraintAnnotation) {
                this.caseMode = constraintAnnotation.value();
            }

            @Override
            public boolean isValid(String object, ConstraintValidatorContext constraintContext) {
                System.out.println("ConstraintValidator.isValid--->");
                if (object == null) {
                    return true;
                }
                boolean isValid;
                if (caseMode.equalsIgnoreCase("UPPER")) {
                    isValid = object.equals(object.toUpperCase());
                } else {
                    isValid = object.equals(object.toLowerCase());
                }
                if (!isValid) {
                    // 禁用用默认错误消息的生成
                    constraintContext.disableDefaultConstraintViolation();
                    // 单独定义自定义错误消息
                    constraintContext.buildConstraintViolationWithTemplate("{constraintvalidatorcontext.CheckCase.message}")
                            .addConstraintViolation();
                    // 就是设置 xx 属性的错误信息
                    constraintContext
                            .buildConstraintViolationWithTemplate("{my.custom.template}")
                            .addPropertyNode("xx").addConstraintViolation();
                }

                return isValid;
            }
        }

        class CheckCaseValidator2 implements HibernateConstraintValidator<CheckCase, String> {


            @Override
            public void initialize(ConstraintDescriptor<CheckCase> constraintDescriptor,
                                   HibernateConstraintValidatorInitializationContext initializationContext) {
                // 拿到注解信息
                System.out.println(constraintDescriptor.getAnnotation());
                // HibernateConstraintValidatorInitializationContext，它提供有用的帮助器和上下文信息，例如时钟提供者或时间验证容差
                System.out.println(initializationContext.getClockProvider().getClock());
            }

            @Override
            public boolean isValid(String value, ConstraintValidatorContext context) {
                System.out.println("HibernateConstraintValidator.isValid--->");
                return false;
            }
        }
    }
}
