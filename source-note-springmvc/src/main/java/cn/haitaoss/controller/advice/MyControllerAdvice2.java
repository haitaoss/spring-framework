package cn.haitaoss.controller.advice;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.HandlerMethod;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-26 14:35
 */
@ControllerAdvice
//@Order(1)
public class MyControllerAdvice2 {

    @ExceptionHandler(value = Exception.class)
    public void globalException(Exception e, HandlerMethod handlerMethode) {

        e.printStackTrace();
        System.out.println("MyControllerAdvice2.globalException..." + handlerMethode.getMethod()
                .getName());
    }

}
