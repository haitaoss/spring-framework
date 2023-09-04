package cn.haitaoss.javaconfig.Validated;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * 验证约束由两部分组成：：
 *  1. 定义一个注解，使用 @Constraint 标注
 *  2. 定义 ConstraintValidator 接口的实例，用来实现具体约束的校验逻辑
 *
 *  注：为了将声明与实现相关联，每个 @Constraint 注释都引用相应的 ConstraintValidator 实现类，
 *      在校验约束注解时，会使用 ConstraintValidatorFactory 实例化 ConstraintValidator，然后回调 ConstraintValidator#isValid 方法做校验
 * */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MyConstraint.MyConstraintValidator.class)
public @interface MyConstraint {
    // 这三个属性必须写，会校验的
    String message() default "{javax.validation.constraints.NotNull.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class MyConstraintValidator implements ConstraintValidator<MyConstraint, String> {
        public MyConstraintValidator() {
            System.out.println("MyConstraintValidator...");
        }

        @Autowired
        private ApplicationContext applicationContext; // 因为Spring配置了ConstraintValidatorFactory，所以可以依赖注入

        @Override
        public void initialize(MyConstraint myConstraint) {
            System.out.println("initialize...." + myConstraint.message());
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            System.out.println("MyConstraintValidator.isValid--->");
            return value.contains("haitao");
        }
    }

}