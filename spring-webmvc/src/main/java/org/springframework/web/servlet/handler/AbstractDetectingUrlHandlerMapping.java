/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.web.servlet.handler;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;
import org.springframework.util.ObjectUtils;

/**
 * Abstract implementation of the {@link org.springframework.web.servlet.HandlerMapping}
 * interface, detecting URL mappings for handler beans through introspection of all
 * defined beans in the application context.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see #determineUrlsForHandler
 */
public abstract class AbstractDetectingUrlHandlerMapping extends AbstractUrlHandlerMapping {

    private boolean detectHandlersInAncestorContexts = false;


    /**
     * Set whether to detect handler beans in ancestor ApplicationContexts.
     * <p>Default is "false": Only handler beans in the current ApplicationContext
     * will be detected, i.e. only in the context that this HandlerMapping itself
     * is defined in (typically the current DispatcherServlet's context).
     * <p>Switch this flag on to detect handler beans in ancestor contexts
     * (typically the Spring root WebApplicationContext) as well.
     */
    public void setDetectHandlersInAncestorContexts(boolean detectHandlersInAncestorContexts) {
        this.detectHandlersInAncestorContexts = detectHandlersInAncestorContexts;
    }


    /**
     * Calls the {@link #detectHandlers()} method in addition to the
     * superclass's initialization.
     */
    @Override
    public void initApplicationContext() throws ApplicationContextException {
        /**
         * 执行父类方法。
         *
         * 具体逻辑是设置 {@link AbstractHandlerMapping#adaptedInterceptors} 属性，
         * 其中会从容器中拿到 MappedInterceptor 类型的bean设置到 adaptedInterceptors 属性中，也就是说最简单的扩展就是编写 MappedInterceptor 类型的bean
         * */
        super.initApplicationContext();
        /**
         * 发现 handler。
         *
         * 就是找到beanName是 '/' 开头的bean，就记录到 {@link AbstractUrlHandlerMapping#handlerMap} 属性中
         * */
        detectHandlers();
    }

    /**
     * Register all handlers found in the current ApplicationContext.
     * <p>The actual URL determination for a handler is up to the concrete
     * {@link #determineUrlsForHandler(String)} implementation. A bean for
     * which no such URLs could be determined is simply not considered a handler.
     * @throws org.springframework.beans.BeansException if the handler couldn't be registered
     * @see #determineUrlsForHandler(String)
     */
    protected void detectHandlers() throws BeansException {
        // 拿到IOC容器
        ApplicationContext applicationContext = obtainApplicationContext();
        // 拿到所有的beanName
        String[] beanNames = (this.detectHandlersInAncestorContexts ? BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                applicationContext, Object.class) : applicationContext.getBeanNamesForType(Object.class));

        // 遍历
        // Take any bean name that we can determine URLs for.
        for (String beanName : beanNames) {
            /**
             * beanName 是 '/' 开头的 或者 其别名有 '/' 开头的 就返回这些 name + alias
             * {@link BeanNameUrlHandlerMapping#determineUrlsForHandler(String)}
             * */
            String[] urls = determineUrlsForHandler(beanName);
            if (!ObjectUtils.isEmpty(urls)) {
                /**
                 * 注册。
                 * 其实就是将 url + bean对象 记录到属性 {@link AbstractUrlHandlerMapping#handlerMap} 中
                 * */
                // URL paths found: Let's consider it a handler.
                registerHandler(urls, beanName);
            }
        }

        if (mappingsLogger.isDebugEnabled()) {
            mappingsLogger.debug(formatMappingName() + " " + getHandlerMap());
        } else if ((logger.isDebugEnabled() && !getHandlerMap().isEmpty()) || logger.isTraceEnabled()) {
            logger.debug("Detected " + getHandlerMap().size() + " mappings in " + formatMappingName());
        }
    }


    /**
     * Determine the URLs for the given handler bean.
     * @param beanName the name of the candidate bean
     * @return the URLs determined for the bean, or an empty array if none
     */
    protected abstract String[] determineUrlsForHandler(String beanName);

}
