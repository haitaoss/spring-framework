package cn.haitaoss.filter;

import javax.servlet.*;
import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-16 21:02
 *
 */
public class Filter1 implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        System.out.println("直接放行");
        chain.doFilter(request, response);
    }
}
