package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-23 15:39
 * todo 自定义信息
 */
public class validate_customer_constraint_message {

    public static void main(String[] args) {
        /**
         * 简单的语法：
         *  1. ${} 引用 验证的值、内置对象等等
         *  2. {} 引用注解的属性值,模板替换的值
         *  注：官方文档 https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-message-interpolation
         *
         * @Size(min = 2,max = 14,
         *        message = "The license plate '${validatedValue}' must be between {min} and {max} characters long")
         * */
        // 自定义 信息解析
        /**
         * {@link Configuration#getDefaultMessageInterpolator()}
         * */

        // TODOHAITAO: 2022/11/23 构造完 ValidatorFactory 后 再重新配置
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        Validator validator = validatorFactory.usingContext()
                // 通过查询 TraversableResolver 接口可以访问哪些属性，哪些属性不能访问
                .traversableResolver(new MyTraversableResolver())
                // 解析模板的
                .messageInterpolator(new MyMessageInterpolator())
                .getValidator();

        /*// 信息国际化
        // TODOHAITAO: 2022/11/23 构造 ValidatorFactory 时 配置
        Validator validator = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(
                        new ResourceBundleMessageInterpolator(
                                // new PlatformResourceBundleLocator("MyMessages")
                                new AggregateResourceBundleLocator(
                                        Arrays.asList(
                                                "MyMessages",
                                                "MyOtherMessages"
                                        )
                                )
                        )
                )
                .buildValidatorFactory()
                .getValidator();*/

        Car car = new Car(null, "A", 1, 400.123456, BigDecimal.valueOf(200000));

        print_constraint_info(validator.validateProperty(car, "manufacturer"));
        print_constraint_info(validator.validateProperty(car, "licensePlate"));
        print_constraint_info(validator.validateProperty(car, "seatCount"));
        print_constraint_info(validator.validateProperty(car, "topSpeed"));
        print_constraint_info(validator.validateProperty(car, "price"));

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

    public static class Car {

        @NotNull
        private String manufacturer;

        @Size(
                min = 2,
                max = 14,
                message = "The license plate '${validatedValue}' must be between {min} and {max} characters long"
        )
        private String licensePlate;

        @Min(
                value = 2,
                message = "There must be at least {value} seat${value > 1 ? 's' : ''}"
        )
        private int seatCount;

        @DecimalMax(
                value = "350",
                message = "The top speed ${formatter.format('%1$.2f', validatedValue)} is higher " +
                          "than {value}"
        )
        private double topSpeed;

        @DecimalMax(value = "100000", message = "Price must not be higher than ${value}")
        private BigDecimal price;

        public Car(
                String manufacturer,
                String licensePlate,
                int seatCount,
                double topSpeed,
                BigDecimal price) {
            this.manufacturer = manufacturer;
            this.licensePlate = licensePlate;
            this.seatCount = seatCount;
            this.topSpeed = topSpeed;
            this.price = price;
        }

        //getters and setters ...
    }
}
