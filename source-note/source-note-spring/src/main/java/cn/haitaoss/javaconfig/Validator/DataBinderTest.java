package cn.haitaoss.javaconfig.Validator;

import lombok.Data;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.*;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;

import javax.validation.Validation;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-22 10:47
 *
 */
public class DataBinderTest {
    public static void main(String[] args) {
        /**
         * DataBinder，可以用来验证bean的信息，然后里面在引入 hibernate-validator 来实现对象的验证
         * 岂不是妙哉
         * https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#validation-binder
         * */
        Address target = new Address();
        target.setLocation("bj");

        DataBinder binder = new DataBinder(target);

        // 会校验 Validator 是否适配绑定的data
        binder.setValidator(new Validator() {
            @Override
            public boolean supports(Class<?> clazz) {
                return Address.class.isAssignableFrom(clazz);
            }

            @Override
            public void validate(Object target, Errors errors) {

            }
        });
        // 使用 hibernate-validator
        binder.addValidators(new SpringValidatorAdapter(Validation.buildDefaultValidatorFactory().getValidator()));
        // bind to the target object
        MutablePropertyValues propertyValues = new MutablePropertyValues();
        propertyValues.add("location", null);
        binder.bind(propertyValues);

        // 用来解析错误信息中的占位符
        binder.setMessageCodesResolver(new DefaultMessageCodesResolver());
        // 校验
        binder.validate();

        // get BindingResult that includes any validation errors
        BindingResult results = binder.getBindingResult();
        System.out.println("results = " + results);

    }

    @Data
    public static class Address {
        private String location;
    }
}
