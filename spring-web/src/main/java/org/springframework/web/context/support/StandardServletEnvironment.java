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

package org.springframework.web.context.support;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jndi.JndiLocatorDelegate;
import org.springframework.jndi.JndiPropertySource;
import org.springframework.lang.Nullable;
import org.springframework.web.context.ConfigurableWebEnvironment;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

/**
 * {@link Environment} implementation to be used by {@code Servlet}-based web
 * applications. All web-related (servlet-based) {@code ApplicationContext} classes
 * initialize an instance by default.
 *
 * <p>Contributes {@code ServletConfig}, {@code ServletContext}, and JNDI-based
 * {@link PropertySource} instances. See {@link #customizePropertySources} method
 * documentation for details.
 *
 * @author Chris Beams
 * @since 3.1
 * @see StandardEnvironment
 */
public class StandardServletEnvironment extends StandardEnvironment implements ConfigurableWebEnvironment {

    /** Servlet context init parameters property source name: {@value}. */
    public static final String SERVLET_CONTEXT_PROPERTY_SOURCE_NAME = "servletContextInitParams";

    /** Servlet config init parameters property source name: {@value}. */
    public static final String SERVLET_CONFIG_PROPERTY_SOURCE_NAME = "servletConfigInitParams";

    /** JNDI property source name: {@value}. */
    public static final String JNDI_PROPERTY_SOURCE_NAME = "jndiProperties";


    /**
     * Create a new {@code StandardServletEnvironment} instance.
     */
    public StandardServletEnvironment() {
    }

    /**
     * Create a new {@code StandardServletEnvironment} instance with a specific {@link MutablePropertySources} instance.
     * @param propertySources property sources to use
     * @since 5.3.4
     */
    protected StandardServletEnvironment(MutablePropertySources propertySources) {
        super(propertySources);
    }


    /**
     * Customize the set of property sources with those contributed by superclasses as
     * well as those appropriate for standard servlet-based environments:
     * <ul>
     * <li>{@value #SERVLET_CONFIG_PROPERTY_SOURCE_NAME}
     * <li>{@value #SERVLET_CONTEXT_PROPERTY_SOURCE_NAME}
     * <li>{@value #JNDI_PROPERTY_SOURCE_NAME}
     * </ul>
     * <p>Properties present in {@value #SERVLET_CONFIG_PROPERTY_SOURCE_NAME} will
     * take precedence over those in {@value #SERVLET_CONTEXT_PROPERTY_SOURCE_NAME}, and
     * properties found in either of the above take precedence over those found in
     * {@value #JNDI_PROPERTY_SOURCE_NAME}.
     * <p>Properties in any of the above will take precedence over system properties and
     * environment variables contributed by the {@link StandardEnvironment} superclass.
     * <p>The {@code Servlet}-related property sources are added as
     * {@link StubPropertySource stubs} at this stage, and will be
     * {@linkplain #initPropertySources(ServletContext, ServletConfig) fully initialized}
     * once the actual {@link ServletContext} object becomes available.
     * @see StandardEnvironment#customizePropertySources
     * @see org.springframework.core.env.AbstractEnvironment#customizePropertySources
     * @see ServletConfigPropertySource
     * @see ServletContextPropertySource
     * @see org.springframework.jndi.JndiPropertySource
     * @see org.springframework.context.support.AbstractApplicationContext#initPropertySources
     * @see #initPropertySources(ServletContext, ServletConfig)
     */
    @Override
    protected void customizePropertySources(MutablePropertySources propertySources) {
        // 访问当前Servlet的设置的初始化属性（只是占位而已，后面要进行替换的）
        propertySources.addLast(new StubPropertySource(SERVLET_CONFIG_PROPERTY_SOURCE_NAME));
        // 访问web容器设置的初始化属性（只是占位而已，后面要进行替换的）
        propertySources.addLast(new StubPropertySource(SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));
        if (JndiLocatorDelegate.isDefaultJndiEnvironmentAvailable()) {
            propertySources.addLast(new JndiPropertySource(JNDI_PROPERTY_SOURCE_NAME));
        }
        // 系统属性 -> 环境变量
        super.customizePropertySources(propertySources);
    }

    @Override
    public void initPropertySources(@Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig) {
        // 替换一开始占位的 PropertySources，让 Environment 真正可以访问到 web容器中的初始化属性
        WebApplicationContextUtils.initServletPropertySources(getPropertySources(), servletContext, servletConfig);
    }

}
