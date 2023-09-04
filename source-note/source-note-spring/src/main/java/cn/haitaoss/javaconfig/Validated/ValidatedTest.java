package cn.haitaoss.javaconfig.Validated;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.validation.annotation.Validated;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-22 10:47
 *
 */
@Import(SpringValidatorConfig.class)
@Validated
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValidatedTest {
    @NotNull(message = "{name}不能为null")
    @MyConstraint(message = "1")
    @Value("haitao")
    public String name;

    @NotNull(message = "返回值不能为null")
    public Object test_Validated(@Valid @NotNull ValidatedTest test, @MyConstraint(message = "2") String name) {
        System.out.println("test = " + test);
        return null;
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ValidatedTest.class);

        try {
            ValidatedTest bean = context.getBean(ValidatedTest.class);
            // bean.test_Validated(null,null);
            // bean.test_Validated(new ValidatedTest(), "haitao");
            bean.test_Validated(new ValidatedTest("haitao"), "haitao");
        } catch (ConstraintViolationException e) {
            print_constraint_info(e.getConstraintViolations());
        }
    }

    private static void print_constraint_info(Set<ConstraintViolation<?>> violations) {
        System.out.println("===============================");
        for (ConstraintViolation<?> item : violations) {
            // 错误信息
            System.out.println(item.getMessage());
            // 约束类型
            System.out.println(item.getConstraintDescriptor().getAnnotation().annotationType());
        }
    }
}
