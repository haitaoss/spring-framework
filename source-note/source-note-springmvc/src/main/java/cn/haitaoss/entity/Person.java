package cn.haitaoss.entity;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-28 15:48
 */
@Data
public class Person {
    @NotNull
    @Max(value = 100)
    private Integer age;

    @NotNull
    private String name;
}
