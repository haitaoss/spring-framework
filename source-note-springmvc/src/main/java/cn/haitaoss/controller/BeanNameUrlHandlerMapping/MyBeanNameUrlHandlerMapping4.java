package cn.haitaoss.controller.BeanNameUrlHandlerMapping;

import org.apache.tomcat.util.http.fileupload.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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
@Component("/url4/**")
public class MyBeanNameUrlHandlerMapping4 implements Controller{
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String path = (String) request.getAttribute(UrlPathHelper.PATH_ATTRIBUTE);
        System.out.println("MyBeanNameUrlHandlerMapping4..." + path);
        // 拿到资源文件路径
        path = "/1.pdf";
        // 读文件
        Resource resource = applicationContext.getResource(path);
        // 将文件写到输出流
        IOUtils.copy(resource.getInputStream(), response.getOutputStream());
        // 设置编码
        response.setCharacterEncoding("UTF-8");
        // 相应的内容是文本
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        return null;
    }
}