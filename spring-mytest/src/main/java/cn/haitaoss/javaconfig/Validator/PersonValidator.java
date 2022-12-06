package cn.haitaoss.javaconfig.Validator;

import lombok.Data;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

public class PersonValidator implements Validator {
    private final Validator addressValidator;

    public PersonValidator(Validator addressValidator) {
        this.addressValidator = addressValidator;
    }

    /**
     * 这个验证器只能验证 Person 类型的实例
     * This Validator validates only Person instances
     */
    public boolean supports(Class clazz) {
        return Person.class.equals(clazz);
    }

    /**
     * Validation 文档
     * https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#validation
     *
     * JavaDoc ValidationUtils
     * https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/validation/ValidationUtils.html
     * JavaDoc Errors
     * https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/validation/Errors.html
     * JavaDoc MessageCodesResolver
     * https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/validation/MessageCodesResolver.html
     * JavaDoc DefaultMessageCodesResolver
     * https://docs.spring.io/spring-framework/docs/6.0.0/javadoc-api/org/springframework/validation/DefaultMessageCodesResolver.html
     *
     * Validator是个工具，而DataBinder是使用工具的人
     * */
    public void validate(Object obj, Errors errors) {
        /**
         * 工具类，字段 name 是空 就将错误信息 记录到 e 里面
         *
         * 因为 Errors 里面包含了 obj的引用
         * */
        ValidationUtils.rejectIfEmpty(errors, "name", "name.empty"); // 是执行 errors.rejectValue 注册的错误信息
        /**
         * e#reject 和 e#rejectValue 默认是使用 DefaultMessageCodesResolver 解析 errorCode
         *      Resolving Codes to Error Messages
         * */
        errors.reject("error");
        /**
         *
         * 而且还注册包含您传递给拒绝方法的字段名称的消息
         * rejectValue 这样子注册信息，还会额外记录：
         *  1.字段的名称
         *  2.字段的类型
         *
         * the first includes the field name and the second includes the type of the field
         * */
        errors.rejectValue("name", "name has error");
        Person p = (Person) obj;
        if (p.getAge() < 0) {
            // 记录错误信息
            errors.rejectValue("age", "negativevalue");
        } else if (p.getAge() > 110) {
            // 记录错误信息
            errors.rejectValue("age", "too.darn.old");
        }

        // 嵌套校验
        try {
            // 先设置一个路径
            errors.pushNestedPath("address");
            // 使用 addressValidator 验证 customer.getAddress() ，验证的结果存到 errors 中
            ValidationUtils.invokeValidator(this.addressValidator, p.getAddress(), errors);
        } finally {
            errors.popNestedPath();
        }
    }

    @Data
    public static class Person {
        private String name;
        private int age;
        private Address address;
    }

    @Data
    public static class Address {
        private String location;
    }
}