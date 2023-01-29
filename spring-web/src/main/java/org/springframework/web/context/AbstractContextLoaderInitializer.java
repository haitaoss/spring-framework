/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.context;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.lang.Nullable;
import org.springframework.web.WebApplicationInitializer;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

/**
 * Convenient base class for {@link WebApplicationInitializer} implementations
 * that register a {@link ContextLoaderListener} in the servlet context.
 *
 * <p>The only method required to be implemented by subclasses is
 * {@link #createRootApplicationContext()}, which gets invoked from
 * {@link #registerContextLoaderListener(ServletContext)}.
 *
 * @author Arjen Poutsma
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.2
 */
public abstract class AbstractContextLoaderInitializer implements WebApplicationInitializer {

    /** Logger available to subclasses. */
    protected final Log logger = LogFactory.getLog(getClass());


    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        // 注册一个 ServletContextListener，注册的是这个 ContextLoaderListener
        registerContextLoaderListener(servletContext);
    }

    /**
     * Register a {@link ContextLoaderListener} against the given servlet context. The
     * {@code ContextLoaderListener} is initialized with the application context returned
     * from the {@link #createRootApplicationContext()} template method.
     * @param servletContext the servlet context to register the listener against
     */
    protected void registerContextLoaderListener(ServletContext servletContext) {
        /**
         * {@link AbstractAnnotationConfigDispatcherServletInitializer#createRootApplicationContext()}
         * 如果存在 {@link AbstractAnnotationConfigDispatcherServletInitializer#getRootConfigClasses()} 属性，就
         * 实例化一个 AnnotationConfigWebApplicationContext （IOC容器），并设置属性作为IOC容器的配置类
         *
         * Tips：这个其实就是我们说的 父容器
         * */
        WebApplicationContext rootAppContext = createRootApplicationContext();
        // 存在父容器
        if (rootAppContext != null) {
            // 构造一个 listener
            ContextLoaderListener listener = new ContextLoaderListener(rootAppContext);
            /**
             * 设置 ContextInitializer,
             * 在监听器的初始化阶段： {@link ContextLoaderListener#contextInitialized(ServletContextEvent)}
             * 会使用 ContextInitializer 来加工 IOC容器，
             * 然后在执行容器的刷新
             * */
            listener.setContextInitializers(getRootApplicationContextInitializers());
            // 往 servletContext 中 注册 listener
            servletContext.addListener(listener);
        } else {
            logger.debug("No ContextLoaderListener registered, as " +
                         "createRootApplicationContext() did not return an application context");
        }
    }

    /**
     * Create the "<strong>root</strong>" application context to be provided to the
     * {@code ContextLoaderListener}.
     * <p>The returned context is delegated to
     * {@link ContextLoaderListener#ContextLoaderListener(WebApplicationContext)} and will
     * be established as the parent context for any {@code DispatcherServlet} application
     * contexts. As such, it typically contains middle-tier services, data sources, etc.
     * @return the root application context, or {@code null} if a root context is not
     * desired
     * @see org.springframework.web.servlet.support.AbstractDispatcherServletInitializer
     */
    @Nullable
    protected abstract WebApplicationContext createRootApplicationContext();

    /**
     * Specify application context initializers to be applied to the root application
     * context that the {@code ContextLoaderListener} is being created with.
     * @since 4.2
     * @see #createRootApplicationContext()
     * @see ContextLoaderListener#setContextInitializers
     */
    @Nullable
    protected ApplicationContextInitializer<?>[] getRootApplicationContextInitializers() {
        return null;
    }

}
