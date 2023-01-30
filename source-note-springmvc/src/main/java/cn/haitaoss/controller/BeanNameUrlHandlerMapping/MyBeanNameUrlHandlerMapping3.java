package cn.haitaoss.controller.BeanNameUrlHandlerMapping;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-19 21:25
 */
@Component("/url/**")
public class MyBeanNameUrlHandlerMapping3 implements Controller, BeanFactoryAware, BeanNameAware {

    private String beanName;
    private BeanFactory beanFactory;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        System.out.println("MyBeanNameUrlHandlerMapping3..." + request.getAttribute(UrlPathHelper.PATH_ATTRIBUTE));
        ModelAndView index = new ModelAndView("index");
        // index.setViewName("redirect:http://www.baidu.com");
        return index;
    }


    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
        DefaultListableBeanFactory.class.cast(beanFactory)
                // .registerAlias("/static/**", beanName); // 原来是这么写，写反了，所以导致后面判断错误
                .registerAlias(beanName, "/static/**"); // 这样才对
    }
}