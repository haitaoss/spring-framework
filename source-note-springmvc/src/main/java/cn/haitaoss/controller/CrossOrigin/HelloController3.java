package cn.haitaoss.controller.CrossOrigin;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-31 12:16
 */
// @CrossOrigin
@RestController
@RequestMapping("cross")
public class HelloController3 {
    /**
     * CorsFilter
     * {@link OncePerRequestFilter#doFilter(ServletRequest, ServletResponse, FilterChain)}
     * {@link CorsFilter#doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)}
     */
    @CrossOrigin
    @RequestMapping("has_CrossOrigin")
    public String has_CrossOrigin() {
        return "ok";
    }

    @RequestMapping("no_CrossOrigin")
    public String no_CrossOrigin() {
        return "ok";
    }
}
