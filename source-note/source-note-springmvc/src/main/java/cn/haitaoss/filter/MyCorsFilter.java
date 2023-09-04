package cn.haitaoss.filter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import javax.servlet.*;
import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-16 21:02
 */
public class MyCorsFilter implements Filter {
    private final CorsFilter corsFilter;

    public MyCorsFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.applyPermitDefaultValues();

        UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
        configSource.registerCorsConfiguration("/**", corsConfiguration);
        corsFilter = new CorsFilter(configSource);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        System.out.println("MyCorsFilter...处理跨域");
        corsFilter.doFilter(request, response, chain);
    }
    /**
     *
     * CorsFilter 继承 OncePerRequestFilter
     * OncePerRequestFilter 实现 Filter
     *
     * 继承 OncePerRequestFilter 的目的是，这种 OncePerRequestFilter 类型的Filter 如果重复执行，只会执行第一个的逻辑，第二个直接放行，避免重复执行。
     * 原理：Filter每次执行前，需要判断request域中是否存在当前FilterName的属性，存在就直接放行(doFilter)，不执行抽象方法 doFilterInternal 。而执行 doFilterInternal 之前
     *      会将当前FilterName存到request域中，从而保证同一个Filter只能执行一次。
     *
     * CorsFilter 依赖了 CorsProcessor，其作为Filter的逻辑是 使用 CorsProcessor 处理预检请求
     * CorsFilter 还依赖了 CorsConfigurationSource ，CorsConfigurationSource 是用来存储 CorsConfiguration 的，可以根据Request获取匹配的
     * CorsConfiguration
     * */
}
