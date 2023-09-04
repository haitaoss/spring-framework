package cn.haitaoss.javaconfig.Validator;

import lombok.Data;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.PropertyValue;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-21 10:37
 *
 */
public class BeanWrapperTest {
    /**
     * https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#beans-beans
     *
     * BeanWrapper 包装bean，然后可以获取属性、设置属性、查询属性可读可写等
     * */
    public static void main(String[] args) {
        BeanWrapper beanWrapper = new BeanWrapperImpl(new Person());
        // setting the Person name..
        beanWrapper.setPropertyValue("name", "Some Person Inc.");
        // ... can also be done like this:
        PropertyValue value = new PropertyValue("name", "Some Person Inc.");
        beanWrapper.setPropertyValue(value);

        // ok, let's create the director and tie it to the Person:
        BeanWrapper jim = new BeanWrapperImpl(new Address());
        jim.setPropertyValue("name", "Jim Stravinsky");
        beanWrapper.setPropertyValue("address", jim.getWrappedInstance());

        // retrieving the salary of the managingDirector through the Person
        String location = (String) beanWrapper.getPropertyValue("address.location");

        beanWrapper.isReadableProperty("name");
        beanWrapper.isWritableProperty("name");

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
