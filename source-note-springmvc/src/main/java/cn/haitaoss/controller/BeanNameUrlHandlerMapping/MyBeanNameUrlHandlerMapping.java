package cn.haitaoss.controller.BeanNameUrlHandlerMapping;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-19 21:25
 */
@Component("/MyBeanNameUrlHandlerMapping")
public class MyBeanNameUrlHandlerMapping implements Controller {

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("MyBeanNameUrlHandlerMapping...");
        ModelAndView index = new ModelAndView("index");
        index.setViewName("redirect:https://www.baidu.com");
        return index;
    }
}
