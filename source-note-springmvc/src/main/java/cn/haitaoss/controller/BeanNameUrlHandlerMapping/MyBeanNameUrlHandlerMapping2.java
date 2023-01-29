package cn.haitaoss.controller.BeanNameUrlHandlerMapping;

import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-19 21:25
 */
@Component("/url2/**")
public class MyBeanNameUrlHandlerMapping2 implements CorsConfigurationSource, HttpRequestHandler {
    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        return new CorsConfiguration();
    }

    @Override
    public void handleRequest(HttpServletRequest request,
                              HttpServletResponse response) throws ServletException, IOException {
        System.out.println("MyBeanNameUrlHandlerMapping2..." + request.getAttribute(UrlPathHelper.PATH_ATTRIBUTE));
        // 转发
        request.getRequestDispatcher("/index.jsp")
                .forward(request, response);
        System.out.println("已经转发了");
    }
}