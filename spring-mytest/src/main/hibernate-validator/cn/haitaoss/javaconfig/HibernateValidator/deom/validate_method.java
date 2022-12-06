package cn.haitaoss.javaconfig.HibernateValidator.deom;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.executable.ExecutableValidator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-23 15:32
 *
 */
public class validate_method {
    public static void main(String[] args) throws Exception {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        ExecutableValidator executableValidator = factory.getValidator().forExecutables();

        /**
         *  TODOHAITAO: 2022/11/23 声明和验证方法约束
         * 方法参数的验证、方法返回值的验证
         * 构造器参数的验证、构造器返回值的验证
         *
         * */
        Car object = new Car("Morris");
        Method method = Car.class.getMethod("drive", int.class);
        Object[] parameterValues = {80};

        /*method = Car.class.getMethod("nest_valid", Car.class);
        parameterValues = new Object[]{new Car("")};*/

        // 验证方法参数
        Set<ConstraintViolation<Car>> violations = executableValidator.validateParameters(
                object,
                method,
                parameterValues
        );
        print_constraint_info(violations);

        // 验证方法返回值
        method = Car.class.getMethod("getPassengers");
        Object returnValue = Collections.<Car>emptyList();
        violations = executableValidator.validateReturnValue(
                object,
                method,
                returnValue
        );
        print_constraint_info(violations);

        // 验证构造器参数
        Constructor<Car> constructor = Car.class.getConstructor(String.class);
        parameterValues = new Object[]{null};
        violations = executableValidator.validateConstructorParameters(
                constructor,
                parameterValues
        );
        print_constraint_info(violations);

        // 验证构造器返回值
        constructor = Car.class.getConstructor(String.class, String.class);
        Car createdObject = new Car("Morris", null);
        violations = executableValidator.validateConstructorReturnValue(
                constructor,
                createdObject
        );
        print_constraint_info(violations);
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

            /*Iterator<Path.Node> propertyPath= item.getPropertyPath().iterator();
            Path.MethodNode methodNode = propertyPath.next().as( Path.MethodNode.class );
            System.out.println("methodNode.getName() = " + methodNode.getName());
            System.out.println("methodNode.getParameterTypes() = " + methodNode.getParameterTypes());


            Path.ParameterNode parameterNode = propertyPath.next().as( Path.ParameterNode.class );
            System.out.println("parameterNode.getName() = " + parameterNode.getName());
            System.out.println("parameterNode.getParameterIndex() = " + parameterNode.getParameterIndex());*/
        }
    }

    public static class Car {
        @NotNull
        private String name;

        public Car(@NotNull String manufacturer) {
            //...
        }

        @ValidRacingCar
        public Car(String manufacturer, String team) {
            //...
        }

        public void drive(@Max(75) int speedInMph) {
            //...
        }

        @Size(min = 1)
        public List<Car> getPassengers() {
            //...
            return Collections.emptyList();
        }

        public void nest_valid(@Valid Car car) {
            //...
        }

    }
}