package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-12 10:23
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(AutoConfigurationImportSelector.class)
public @interface EnableAutoConfiguration {}
