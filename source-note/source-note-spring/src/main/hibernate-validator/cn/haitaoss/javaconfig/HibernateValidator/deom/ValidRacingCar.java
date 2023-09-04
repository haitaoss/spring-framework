package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({METHOD, CONSTRUCTOR, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = {ValidRacingCar.Validator.class})
@Documented
public @interface ValidRacingCar {

    String message() default "{org.hibernate.validator.referenceguide.chapter03.validation.ValidRacingCar.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidRacingCar, validate_method.Car> {
        @Override
        public boolean isValid(validate_method.Car car, ConstraintValidatorContext context) {
            System.out.println("ValidRacingCar.Validator.isValid--->");
            return false;
        }
    }
}