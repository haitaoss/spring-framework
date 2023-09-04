package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.Data;

import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-23 17:26
 * https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#chapter-groups
 */
public class validate_constraint_group {
    public static void main(String[] args) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Driver driver = new Driver(1, null, false);

        // 默认就是 Default
        print_constraint_info(validator.validate(driver));
        print_constraint_info(validator.validate(driver, Default.class));
        // 只校验 DriverChecks
        print_constraint_info(validator.validate(driver, DriverChecks.class));
        // 多个校验组，就按照顺序进行校验
        print_constraint_info(validator.validate(driver, DriverChecks.class, Default.class));
        print_constraint_info(validator.validate(driver, Default.class, DriverChecks.class));
    }

    private static void print_constraint_info(Set<ConstraintViolation<Driver>> violations) {
        System.out.println("===========================");
        for (ConstraintViolation<Driver> item : violations) {
            // 错误信息
            System.out.println(item.getMessage());
            // 约束类型
            System.out.println(item.getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType());
        }
    }

    @Data
    /**
     * 设置默认的 Group，在具体的约束注解上指定了Group可以自定义
     * 默认的Group是Default
     * */
    // @GroupSequence({DriverChecks.class, Driver.class})
    public static class Driver {

        @Min(
                value = 18,
                message = "You have to be 18 to drive a car",
                groups = DriverChecks.class
        )
        public int age;
        @NotNull(message = "name check")
        private String name;

        @AssertTrue(
                message = "You first have to pass the driving test",
                groups = DriverChecks.class
        )
        public boolean hasDrivingLicense;

        public Driver(int age, String name, boolean hasDrivingLicense) {
            this.age = age;
            this.name = name;
            this.hasDrivingLicense = hasDrivingLicense;
        }
    }

    public static interface DriverChecks {
    }
}
