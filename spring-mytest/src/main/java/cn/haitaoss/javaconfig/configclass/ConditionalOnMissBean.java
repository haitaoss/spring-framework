package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-12 10:43
 *
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(ConditionMissBean.class)
public @interface ConditionalOnMissBean {
    String value();
}
