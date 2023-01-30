package cn.haitaoss.servlet;

import cn.haitaoss.config.RootConfig;
import cn.haitaoss.config.WebServletConfig;
import cn.haitaoss.filter.Filter1;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.Filter;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-15 20:59
 */
public class DispatcherServletInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {
    /**
     * RequestToViewNameTranslator（request变成viewName的翻译器。比如返回了ModelAndView但是没有指定viewName，就是使用这个解析request拿到默认的viewName）:
     *  DefaultRequestToViewNameTranslator
     *
     * FlashMapManager（处理RedirectAttributes中的flash参数的，负责设置、读取session中的值）:
     *  SessionFlashMapManager
     * */
    @Override
    protected Class<?>[] getRootConfigClasses() {
        // 作为父容器的配置类
        return new Class[]{RootConfig.class};
        // return new Class[]{};
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        // 作为子容器的配置类
        return new Class[]{WebServletConfig.class};
    }

    @Override
    protected String[] getServletMappings() {
        // DispatcherServlet 的拦截路径
        return new String[]{"/"};
    }

    @Override
    protected Filter[] getServletFilters() {
        // 是否需要给 DispatcherServlet 注册过滤器
        return new Filter[]{new Filter1()};
    }

    @Override
    protected ApplicationContextInitializer<?>[] getRootApplicationContextInitializers() {
        return new ApplicationContextInitializer[]{ioc -> System.out.println("可以在这里配置IOC容器--->" + ioc)};
    }
}
