/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.web.context.support;

import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.ConfigurableWebApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.*;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Convenience methods for retrieving the root {@link WebApplicationContext} for
 * a given {@link ServletContext}. This is useful for programmatically accessing
 * a Spring application context from within custom web views or MVC actions.
 *
 * <p>Note that there are more convenient ways of accessing the root context for
 * many web frameworks, either part of Spring or available as an external library.
 * This helper class is just the most generic way to access the root context.
 *
 * @author Juergen Hoeller
 * @see org.springframework.web.context.ContextLoader
 * @see org.springframework.web.servlet.FrameworkServlet
 * @see org.springframework.web.servlet.DispatcherServlet
 * @see org.springframework.web.jsf.FacesContextUtils
 * @see org.springframework.web.jsf.el.SpringBeanFacesELResolver
 */
public abstract class WebApplicationContextUtils {

    private static final boolean jsfPresent = ClassUtils.isPresent(
            "javax.faces.context.FacesContext", RequestContextHolder.class.getClassLoader());


    /**
     * Find the root {@code WebApplicationContext} for this web app, typically
     * loaded via {@link org.springframework.web.context.ContextLoaderListener}.
     * <p>Will rethrow an exception that happened on root context startup,
     * to differentiate between a failed context startup and no context at all.
     * @param sc the ServletContext to find the web application context for
     * @return the root WebApplicationContext for this web app
     * @throws IllegalStateException if the root WebApplicationContext could not be found
     * @see org.springframework.web.context.WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE
     */
    public static WebApplicationContext getRequiredWebApplicationContext(
            ServletContext sc) throws IllegalStateException {
        WebApplicationContext wac = getWebApplicationContext(sc);
        if (wac == null) {
            throw new IllegalStateException("No WebApplicationContext found: no ContextLoaderListener registered?");
        }
        return wac;
    }

    /**
     * Find the root {@code WebApplicationContext} for this web app, typically
     * loaded via {@link org.springframework.web.context.ContextLoaderListener}.
     * <p>Will rethrow an exception that happened on root context startup,
     * to differentiate between a failed context startup and no context at all.
     * @param sc the ServletContext to find the web application context for
     * @return the root WebApplicationContext for this web app, or {@code null} if none
     * @see org.springframework.web.context.WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE
     */
    @Nullable
    public static WebApplicationContext getWebApplicationContext(ServletContext sc) {
        return getWebApplicationContext(sc, WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
    }

    /**
     * Find a custom {@code WebApplicationContext} for this web app.
     * @param sc the ServletContext to find the web application context for
     * @param attrName the name of the ServletContext attribute to look for
     * @return the desired WebApplicationContext for this web app, or {@code null} if none
     */
    @Nullable
    public static WebApplicationContext getWebApplicationContext(ServletContext sc, String attrName) {
        Assert.notNull(sc, "ServletContext must not be null");
        Object attr = sc.getAttribute(attrName);
        if (attr == null) {
            return null;
        }
        if (attr instanceof RuntimeException) {
            throw (RuntimeException) attr;
        }
        if (attr instanceof Error) {
            throw (Error) attr;
        }
        if (attr instanceof Exception) {
            throw new IllegalStateException((Exception) attr);
        }
        if (!(attr instanceof WebApplicationContext)) {
            throw new IllegalStateException("Context attribute is not of type WebApplicationContext: " + attr);
        }
        return (WebApplicationContext) attr;
    }

    /**
     * Find a unique {@code WebApplicationContext} for this web app: either the
     * root web app context (preferred) or a unique {@code WebApplicationContext}
     * among the registered {@code ServletContext} attributes (typically coming
     * from a single {@code DispatcherServlet} in the current web application).
     * <p>Note that {@code DispatcherServlet}'s exposure of its context can be
     * controlled through its {@code publishContext} property, which is {@code true}
     * by default but can be selectively switched to only publish a single context
     * despite multiple {@code DispatcherServlet} registrations in the web app.
     * @param sc the ServletContext to find the web application context for
     * @return the desired WebApplicationContext for this web app, or {@code null} if none
     * @since 4.2
     * @see #getWebApplicationContext(ServletContext)
     * @see ServletContext#getAttributeNames()
     */
    @Nullable
    public static WebApplicationContext findWebApplicationContext(ServletContext sc) {
        WebApplicationContext wac = getWebApplicationContext(sc);
        if (wac == null) {
            Enumeration<String> attrNames = sc.getAttributeNames();
            while (attrNames.hasMoreElements()) {
                String attrName = attrNames.nextElement();
                Object attrValue = sc.getAttribute(attrName);
                if (attrValue instanceof WebApplicationContext) {
                    if (wac != null) {
                        throw new IllegalStateException(
                                "No unique WebApplicationContext found: more than one " + "DispatcherServlet registered with publishContext=true?");
                    }
                    wac = (WebApplicationContext) attrValue;
                }
            }
        }
        return wac;
    }


    /**
     * Register web-specific scopes ("request", "session", "globalSession")
     * with the given BeanFactory, as used by the WebApplicationContext.
     * @param beanFactory the BeanFactory to configure
     */
    public static void registerWebApplicationScopes(ConfigurableListableBeanFactory beanFactory) {
        registerWebApplicationScopes(beanFactory, null);
    }

    /**
     * Register web-specific scopes ("request", "session", "globalSession", "application")
     * with the given BeanFactory, as used by the WebApplicationContext.
     * @param beanFactory the BeanFactory to configure
     * @param sc the ServletContext that we're running within
     */
    public static void registerWebApplicationScopes(ConfigurableListableBeanFactory beanFactory,
                                                    @Nullable ServletContext sc) {

        /**
         * 注册 request、session、application 三个Scope
         *
         * session和request是基于请求的，得有请求才有域对象,而application是基于web应用的，项目启动了就会存在。
         *
         * request和session域的原理是 {@link AbstractRequestAttributesScope#get(String, ObjectFactory)}
         *  1. 从 {@link RequestContextHolder} 获取 RequestAttributes（包装了 request、response）
         *  2. 根据 name 从对应的作用域(request,session)中取出属性，若属性为null，就使用 ObjectFactory 拿到值，在回填到作用域中
         *      request.getAttribute(name)
         *      request.getSession(false).getAttribute(name)
         *
         *  Tips：request和session Scope的实现，是通过将对象存到 对应的作用域中，所以这些对象的生命周期 和 request、session是一致的
         * */
        beanFactory.registerScope(WebApplicationContext.SCOPE_REQUEST, new RequestScope());
        beanFactory.registerScope(WebApplicationContext.SCOPE_SESSION, new SessionScope());
        if (sc != null) {
            /**
             * 这个就更简单了，直接往 servletContext set、remove、get 即可，生命周期和web应用是一致的
             *      servletContext.setAttribute(name, scopedObject);
             * */
            ServletContextScope appScope = new ServletContextScope(sc);
            beanFactory.registerScope(WebApplicationContext.SCOPE_APPLICATION, appScope);
            // 将 Scope 对象 记录到 application 域中
            // Register as ServletContext attribute, for ContextCleanupListener to detect it.
            sc.setAttribute(ServletContextScope.class.getName(), appScope);
        }
        /**
         * 用于依赖注入时，可以注入这四个类型的对象
         *
         * {@link DefaultListableBeanFactory#doResolveDependency(DependencyDescriptor, String, Set, TypeConverter)}
         * {@link DefaultListableBeanFactory#findAutowireCandidates(String, Class, DependencyDescriptor)}
         * {@link AutowireUtils#resolveAutowiringValue(Object, Class)}
         *      因为值(RequestObjectFactory、ResponseObjectFactory、SessionObjectFactory、WebRequestObjectFactory) 是ObjectFactory类型 且 依赖的类型是接口，
         *      所以依赖注入的时候注入的是 代理对象 ，具体的增强逻辑是 {@link AutowireUtils.ObjectFactoryDelegatingInvocationHandler#invoke(Object, Method, Object[])}
         *      `return method.invoke(this.objectFactory.getObject(), args);` 即每次执行方法都是 objectFactory.getObject()
         *
         *      objectFactory.getObject() 方法会调用 {@link RequestContextHolder} 拿到记录在ThreadLocal中的 request、response、Session 从而保证这四个对象是线程安全的，
         *      而 ThreadLocal 的设置是在 DispatcherServlet 收到请求，开始处理之前会设置的，从而能够保证 依赖注入的 这四个对象是 在对应的作用域是唯一的(request域，session域)
         *          {@link org.springframework.web.servlet.FrameworkServlet#service(HttpServletRequest, HttpServletResponse)}
         *          {@link org.springframework.web.servlet.FrameworkServlet#processRequest(HttpServletRequest, HttpServletResponse)}
         *
         * Tips：使用 registerResolvableDependency 添加对象，是为了扩展可用于依赖注入的对象，通过这种方式 getBean() 是拿不到的，只能通过依赖注入获取到
         * */
        beanFactory.registerResolvableDependency(ServletRequest.class, new RequestObjectFactory());
        beanFactory.registerResolvableDependency(ServletResponse.class, new ResponseObjectFactory());
        beanFactory.registerResolvableDependency(HttpSession.class, new SessionObjectFactory());
        beanFactory.registerResolvableDependency(WebRequest.class, new WebRequestObjectFactory());
        if (jsfPresent) {
            FacesDependencyRegistrar.registerFacesDependencies(beanFactory);
        }
    }

    /**
     * Register web-specific environment beans ("contextParameters", "contextAttributes")
     * with the given BeanFactory, as used by the WebApplicationContext.
     * @param bf the BeanFactory to configure
     * @param sc the ServletContext that we're running within
     */
    public static void registerEnvironmentBeans(ConfigurableListableBeanFactory bf, @Nullable ServletContext sc) {
        registerEnvironmentBeans(bf, sc, null);
    }

    /**
     * Register web-specific environment beans ("contextParameters", "contextAttributes")
     * with the given BeanFactory, as used by the WebApplicationContext.
     * @param bf the BeanFactory to configure
     * @param servletContext the ServletContext that we're running within
     * @param servletConfig the ServletConfig
     */
    public static void registerEnvironmentBeans(ConfigurableListableBeanFactory bf,
                                                @Nullable ServletContext servletContext,
                                                @Nullable ServletConfig servletConfig) {

        // 注册单例bean，用于 ServletContext 的依赖注入
        if (servletContext != null && !bf.containsBean(WebApplicationContext.SERVLET_CONTEXT_BEAN_NAME)) {
            bf.registerSingleton(WebApplicationContext.SERVLET_CONTEXT_BEAN_NAME, servletContext);
        }

        // 注册单例bean，用于 ServletConfig 的依赖注入
        if (servletConfig != null && !bf.containsBean(ConfigurableWebApplicationContext.SERVLET_CONFIG_BEAN_NAME)) {
            bf.registerSingleton(ConfigurableWebApplicationContext.SERVLET_CONFIG_BEAN_NAME, servletConfig);
        }

        // 补充，注入 contextParameters 这个bean，用于访问 web容器+DispatcherServlet 的初始化属性
        if (!bf.containsBean(WebApplicationContext.CONTEXT_PARAMETERS_BEAN_NAME)) {
            Map<String, String> parameterMap = new HashMap<>();
            if (servletContext != null) {
                Enumeration<?> paramNameEnum = servletContext.getInitParameterNames();
                while (paramNameEnum.hasMoreElements()) {
                    String paramName = (String) paramNameEnum.nextElement();
                    parameterMap.put(paramName, servletContext.getInitParameter(paramName));
                }
            }
            if (servletConfig != null) {
                Enumeration<?> paramNameEnum = servletConfig.getInitParameterNames();
                while (paramNameEnum.hasMoreElements()) {
                    String paramName = (String) paramNameEnum.nextElement();
                    parameterMap.put(paramName, servletConfig.getInitParameter(paramName));
                }
            }
            bf.registerSingleton(WebApplicationContext.CONTEXT_PARAMETERS_BEAN_NAME,
                    Collections.unmodifiableMap(parameterMap)
            );
        }

        // 补充，注入 contextAttributes 这个bean，用于访问 DispatcherServlet 的初始化属性
        if (!bf.containsBean(WebApplicationContext.CONTEXT_ATTRIBUTES_BEAN_NAME)) {
            Map<String, Object> attributeMap = new HashMap<>();
            if (servletContext != null) {
                Enumeration<?> attrNameEnum = servletContext.getAttributeNames();
                while (attrNameEnum.hasMoreElements()) {
                    String attrName = (String) attrNameEnum.nextElement();
                    attributeMap.put(attrName, servletContext.getAttribute(attrName));
                }
            }
            bf.registerSingleton(WebApplicationContext.CONTEXT_ATTRIBUTES_BEAN_NAME,
                    Collections.unmodifiableMap(attributeMap)
            );
        }
    }

    /**
     * Convenient variant of {@link #initServletPropertySources(MutablePropertySources,
     * ServletContext, ServletConfig)} that always provides {@code null} for the
     * {@link ServletConfig} parameter.
     * @see #initServletPropertySources(MutablePropertySources, ServletContext, ServletConfig)
     */
    public static void initServletPropertySources(MutablePropertySources propertySources,
                                                  ServletContext servletContext) {
        initServletPropertySources(propertySources, servletContext, null);
    }

    /**
     * Replace {@code Servlet}-based {@link StubPropertySource stub property sources} with
     * actual instances populated with the given {@code servletContext} and
     * {@code servletConfig} objects.
     * <p>This method is idempotent with respect to the fact it may be called any number
     * of times but will perform replacement of stub property sources with their
     * corresponding actual property sources once and only once.
     * @param sources the {@link MutablePropertySources} to initialize (must not
     * be {@code null})
     * @param servletContext the current {@link ServletContext} (ignored if {@code null}
     * or if the {@link StandardServletEnvironment#SERVLET_CONTEXT_PROPERTY_SOURCE_NAME
     * servlet context property source} has already been initialized)
     * @param servletConfig the current {@link ServletConfig} (ignored if {@code null}
     * or if the {@link StandardServletEnvironment#SERVLET_CONFIG_PROPERTY_SOURCE_NAME
     * servlet config property source} has already been initialized)
     * @see org.springframework.core.env.PropertySource.StubPropertySource
     * @see org.springframework.core.env.ConfigurableEnvironment#getPropertySources()
     */
    public static void initServletPropertySources(MutablePropertySources sources,
                                                  @Nullable ServletContext servletContext,
                                                  @Nullable ServletConfig servletConfig) {
        // 替换 属性信息。说白了就是可以通过IOC容器的 Environment 对象访问 servletContext 中的属性信息(初始化参数、配置参数)
        Assert.notNull(sources, "'propertySources' must not be null");
        String name = StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME;
        if (servletContext != null && sources.get(name) instanceof StubPropertySource) {
            sources.replace(name, new ServletContextPropertySource(name, servletContext));
        }
        name = StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME;
        if (servletConfig != null && sources.get(name) instanceof StubPropertySource) {
            sources.replace(name, new ServletConfigPropertySource(name, servletConfig));
        }
    }

    /**
     * Return the current RequestAttributes instance as ServletRequestAttributes.
     * @see RequestContextHolder#currentRequestAttributes()
     */
    private static ServletRequestAttributes currentRequestAttributes() {
        RequestAttributes requestAttr = RequestContextHolder.currentRequestAttributes();
        if (!(requestAttr instanceof ServletRequestAttributes)) {
            throw new IllegalStateException("Current request is not a servlet request");
        }
        return (ServletRequestAttributes) requestAttr;
    }


    /**
     * Factory that exposes the current request object on demand.
     */
    @SuppressWarnings("serial")
    private static class RequestObjectFactory implements ObjectFactory<ServletRequest>, Serializable {

        @Override
        public ServletRequest getObject() {
            return currentRequestAttributes().getRequest();
        }

        @Override
        public String toString() {
            return "Current HttpServletRequest";
        }
    }


    /**
     * Factory that exposes the current response object on demand.
     */
    @SuppressWarnings("serial")
    private static class ResponseObjectFactory implements ObjectFactory<ServletResponse>, Serializable {

        @Override
        public ServletResponse getObject() {
            ServletResponse response = currentRequestAttributes().getResponse();
            if (response == null) {
                throw new IllegalStateException(
                        "Current servlet response not available - " + "consider using RequestContextFilter instead of RequestContextListener");
            }
            return response;
        }

        @Override
        public String toString() {
            return "Current HttpServletResponse";
        }
    }


    /**
     * Factory that exposes the current session object on demand.
     */
    @SuppressWarnings("serial")
    private static class SessionObjectFactory implements ObjectFactory<HttpSession>, Serializable {

        @Override
        public HttpSession getObject() {
            return currentRequestAttributes().getRequest()
                    .getSession();
        }

        @Override
        public String toString() {
            return "Current HttpSession";
        }
    }


    /**
     * Factory that exposes the current WebRequest object on demand.
     */
    @SuppressWarnings("serial")
    private static class WebRequestObjectFactory implements ObjectFactory<WebRequest>, Serializable {

        @Override
        public WebRequest getObject() {
            ServletRequestAttributes requestAttr = currentRequestAttributes();
            return new ServletWebRequest(requestAttr.getRequest(), requestAttr.getResponse());
        }

        @Override
        public String toString() {
            return "Current ServletWebRequest";
        }
    }


    /**
     * Inner class to avoid hard-coded JSF dependency.
     */
    private static class FacesDependencyRegistrar {

        public static void registerFacesDependencies(ConfigurableListableBeanFactory beanFactory) {
            beanFactory.registerResolvableDependency(FacesContext.class, new ObjectFactory<FacesContext>() {
                @Override
                public FacesContext getObject() {
                    return FacesContext.getCurrentInstance();
                }

                @Override
                public String toString() {
                    return "Current JSF FacesContext";
                }
            });
            beanFactory.registerResolvableDependency(ExternalContext.class, new ObjectFactory<ExternalContext>() {
                @Override
                public ExternalContext getObject() {
                    return FacesContext.getCurrentInstance()
                            .getExternalContext();
                }

                @Override
                public String toString() {
                    return "Current JSF ExternalContext";
                }
            });
        }
    }

}
