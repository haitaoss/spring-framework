package cn.haitaoss.controller.advice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.ui.Model;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-26 14:35
 */
@ControllerAdvice
@Order(1)
public class MyControllerAdvice {
    @Autowired
    private ConversionService conversionService;

    @Autowired(required = false)
    private Validator validator;

    @ExceptionHandler(value = Exception.class)
    //    @ExceptionHandler // 不指定注解值，那么方法的参数必须要有 Throwable 类型的才能被识别
    public /*ModelAndView*/ void globalException(Exception e, HandlerMethod handlerMethode, Model model,
                                                 HttpServletRequest servletRequest,
                                                 HttpServletResponse response) throws ServletException, IOException {
        /**
         * 一个异常至多只能被一个 @ExceptionHandler 处理
         * {@link ExceptionHandlerExceptionResolver#getExceptionHandlerMethod(HandlerMethod, Exception)}
         * */
        /**
         * 如何让异常不要抛出去了
         * {@link DispatcherServlet#processHandlerException(HttpServletRequest, HttpServletResponse, Object, Exception)}
         * */
        e.printStackTrace();
        System.out.println("MyControllerAdvice.globalException..." + handlerMethode.getMethod()
                .getName());

        //        modelAndView.setViewName("exception"); // 返回视图，那么异常就不会抛给客户端了
        //        model.addAttribute("msg", e.getMessage());
        /*servletRequest.getRequestDispatcher("/error.jsp")
                .forward(servletRequest, response);*/
    }


    // 不指定 name，说明是全局的，只要有 @ModelAttribute 的方法被回调了，这个方法就会被回调
    @InitBinder
    public void InitBinder(WebDataBinder webDataBinder, WebRequest webRequest) {
        // 可以扩展内容，比如设置
        //        webDataBinder.setConversionService(conversionService);
        webDataBinder.setValidator(validator);
//        webDataBinder.validate();
        System.out.println("MyControllerAdvice.InitBinder...");
    }

    @InitBinder({"msg"})
    public void InitBinder2(HttpSession session) {
        /**
         * 当@ModelAttribute的方法被执行时，会解析该方法的参数列表，当解析到的参数的name是 msg 时该方法才会被回调
         * {@link InvocableHandlerMethod#invokeForRequest(NativeWebRequest, ModelAndViewContainer, Object...)}
         * {@link InvocableHandlerMethod#getMethodArgumentValues(NativeWebRequest, ModelAndViewContainer, Object...)}
         * {@link ModelAttributeMethodProcessor#resolveArgument(MethodParameter, ModelAndViewContainer, NativeWebRequest, WebDataBinderFactory)}
         * {@link DefaultDataBinderFactory#createBinder(NativeWebRequest, Object, String)}
         * {@link InitBinderDataBinderFactory#initBinder(WebDataBinder, NativeWebRequest)}
         * */
        Iterator<String> stringIterator = session.getAttributeNames()
                .asIterator();
        List<String> data = new ArrayList<>();
        while (stringIterator.hasNext()) {
            data.add(stringIterator.next());
        }
        System.out.println("MyControllerAdvice.InitBinder2..." + data);
    }

    //    @ModelAttribute("extend_attr")
    public String ModelAttribute(String msg, @PathVariable(value = "msg",
            required = false) String name) {
        System.out.println(String.format("MyControllerAdvice.ModelAttribute...msg is : %s, name is : %s", msg, name));
        return "hello ModelAttribute";
    }
}
