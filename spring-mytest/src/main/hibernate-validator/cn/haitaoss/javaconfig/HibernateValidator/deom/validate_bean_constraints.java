package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-22 14:20
 *
 */
public class validate_bean_constraints {
    /**
     *
     * https://hibernate.org/validator/documentation/
     * https://hibernate.org/validator/documentation/getting-started/
     * https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/
     *
     * 官方文档代码 https://github.com/hibernate/hibernate-validator/tree/main/documentation/src/test
     *
     * Hibernate-validator 内置的约束(就是支持的约束注解) https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-builtin-constraints
     * */
    public static void main(String[] args) {
        /**
         * https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#chapter-bootstrapping* */

        /**
         * TODOHAITAO: 2022/11/23 声明和验证bean约束
         *
         * bean的验证 或者叫做属性的验证好理解点
         * */
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();

        // Car car = new Car(null, "DD-AB-123", 4);
        Car car = new Car("1", "aa", 4);
        car.setDriver(new Person());

        // 验证 bean 里面所有约束
        // Set<ConstraintViolation<Car>> constraintViolations = validator.validate(car);

        /**
         * @Valid 不被 validateProperty() 或 validateValue() 处理
         * */
        // 验证某个属性的约束
        // Set<ConstraintViolation<Car>> constraintViolations = validator.validateProperty(car, "manufacturer");

        // 验证属性的值
        Set<ConstraintViolation<Car>> constraintViolations = validator.validateValue(Car.class, "manufacturer", null);
        // Set<ConstraintViolation<Car>> constraintViolations = validator.validateValue(Car.class, "driver", null);
        System.out.println("constraintViolations = " + constraintViolations.size());
        // ConstraintViolation 违反约束
        for (ConstraintViolation<Car> item : constraintViolations)
            System.out.println(item.getMessage());
    }

    @Data
    public static class Car {

        @NotNull
        private String manufacturer;

        @NotNull
        @Size(min = 2, max = 14)
        private String licensePlate;

        @Min(2)
        private int seatCount;

        // @NotNull
        @Valid // 会有验证 Person1 里面的属性
        private validate_bean_constraints.Person driver;

        public Car(String manufacturer, String licencePlate, int seatCount) {
            this.manufacturer = manufacturer;
            this.licensePlate = licencePlate;
            this.seatCount = seatCount;
        }

        // getters and setters ...
    }

    @Data
    public static class Person {

        @NotNull(message = "Person1.name 不能为null")
        private String name;

        //...
    }
}


