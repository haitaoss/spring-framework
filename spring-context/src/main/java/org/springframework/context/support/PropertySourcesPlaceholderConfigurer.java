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

package org.springframework.context.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.*;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringValueResolver;

import java.io.IOException;
import java.util.Properties;

/**
 * Specialization of {@link PlaceholderConfigurerSupport} that resolves ${...} placeholders
 * within bean definition property values and {@code @Value} annotations against the current
 * Spring {@link Environment} and its set of {@link PropertySources}.
 *
 * <p>This class is designed as a general replacement for {@code PropertyPlaceholderConfigurer}.
 * It is used by default to support the {@code property-placeholder} element in working against
 * the spring-context-3.1 or higher XSD; whereas, spring-context versions &lt;= 3.0 default to
 * {@code PropertyPlaceholderConfigurer} to ensure backward compatibility. See the spring-context
 * XSD documentation for complete details.
 *
 * <p>Any local properties (e.g. those added via {@link #setProperties}, {@link #setLocations}
 * et al.) are added as a {@code PropertySource}. Search precedence of local properties is
 * based on the value of the {@link #setLocalOverride localOverride} property, which is by
 * default {@code false} meaning that local properties are to be searched last, after all
 * environment property sources.
 *
 * <p>See {@link org.springframework.core.env.ConfigurableEnvironment} and related javadocs
 * for details on manipulating environment property sources.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see org.springframework.core.env.ConfigurableEnvironment
 * @see org.springframework.beans.factory.config.PlaceholderConfigurerSupport
 * @see org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
 */
public class PropertySourcesPlaceholderConfigurer extends PlaceholderConfigurerSupport implements EnvironmentAware {

    /**
     * {@value} is the name given to the {@link PropertySource} for the set of
     * {@linkplain #mergeProperties() merged properties} supplied to this configurer.
     */
    public static final String LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME = "localProperties";

    /**
     * {@value} is the name given to the {@link PropertySource} that wraps the
     * {@linkplain #setEnvironment environment} supplied to this configurer.
     */
    public static final String ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME = "environmentProperties";


    @Nullable
    private MutablePropertySources propertySources;

    @Nullable
    private PropertySources appliedPropertySources;

    @Nullable
    private Environment environment;


    /**
     * Customize the set of {@link PropertySources} to be used by this configurer.
     * <p>Setting this property indicates that environment property sources and
     * local properties should be ignored.
     * @see #postProcessBeanFactory
     */
    public void setPropertySources(PropertySources propertySources) {
        this.propertySources = new MutablePropertySources(propertySources);
    }

    /**
     * {@code PropertySources} from the given {@link Environment}
     * will be searched when replacing ${...} placeholders.
     * @see #setPropertySources
     * @see #postProcessBeanFactory
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }


    /**
     * Processing occurs by replacing ${...} placeholders in bean definitions by resolving each
     * against this configurer's set of {@link PropertySources}, which includes:
     * <ul>
     * <li>all {@linkplain org.springframework.core.env.ConfigurableEnvironment#getPropertySources
     * environment property sources}, if an {@code Environment} {@linkplain #setEnvironment is present}
     * <li>{@linkplain #mergeProperties merged local properties}, if {@linkplain #setLocation any}
     * {@linkplain #setLocations have} {@linkplain #setProperties been}
     * {@linkplain #setPropertiesArray specified}
     * <li>any property sources set by calling {@link #setPropertySources}
     * </ul>
     * <p>If {@link #setPropertySources} is called, <strong>environment and local properties will be
     * ignored</strong>. This method is designed to give the user fine-grained control over property
     * sources, and once set, the configurer makes no assumptions about adding additional sources.
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 初始化内部属性 propertySources
        if (this.propertySources == null) {
            this.propertySources = new MutablePropertySources();
            // 将 Environment 装饰成 PropertySource，然后添加到 propertySources 中 用于访问 Environment中的属性
            if (this.environment != null) {
                this.propertySources.addLast(
                        new PropertySource<Environment>(ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME, this.environment) {
                            @Override
                            @Nullable
                            public String getProperty(String key) {
                                return this.source.getProperty(key);
                            }
                        }
                );
            }
            try {
                /**
                 * 读取 <context:property-placeholder location="classpath:data.properties"/> 属性文件的内容 成 Properties对象，
                 * 将Properties对象构造成 PropertiesPropertySource
                 * */
                PropertySource<?> localPropertySource =
                        new PropertiesPropertySource(LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME, mergeProperties());
                /**
                 * 比如，localOverride 的默认值是false
                 * <context:property-placeholder location="classpath:data.properties" local-override="false"/>
                 * */
                if (this.localOverride) {
                    /**
                     * 放到前面，也就是说 访问属性的顺序是： <context:property-placeholder/> ---> Environment
                     *
                     * 其实 Environment 也是一个复合的 PropertySource 其获取属性的顺序是：getSystemProperties -> getSystemEnvironment -> @PropertySource，看
                     *    {@link StandardEnvironment#customizePropertySources(MutablePropertySources)}
                     *
                     * 所以如果 localOverride 为true，最终的属性访问顺序其实是： <context:property-placeholder/> -> getSystemProperties -> getSystemEnvironment -> @PropertySource
                     * */
                    this.propertySources.addFirst(localPropertySource);
                } else {
                    // 放到Environment的后面
                    this.propertySources.addLast(localPropertySource);
                }
            } catch (IOException ex) {
                throw new BeanInitializationException("Could not load properties", ex);
            }
        }

        /**
         * 将 propertySources 构造成 PropertySourcesPropertyResolver
         * 然后为 PropertySourcesPropertyResolver 配置解析的前后缀，默认为 ${}
         * 然后将 PropertySourcesPropertyResolver 设置为 beanFactory 的嵌入值解析器
         *
         * {@link AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)}
         * */
        processProperties(beanFactory, new PropertySourcesPropertyResolver(this.propertySources));
        this.appliedPropertySources = this.propertySources;
    }

    /**
     * Visit each bean definition in the given bean factory and attempt to replace ${...} property
     * placeholders with values from the given properties.
     */
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess,
                                     final ConfigurablePropertyResolver propertyResolver) throws BeansException {

        // 占位符前缀，默认是 ${
        propertyResolver.setPlaceholderPrefix(this.placeholderPrefix);
        // 占位符后缀，默认是 }
        propertyResolver.setPlaceholderSuffix(this.placeholderSuffix);
        // 默认值分割符，默认是 :
        propertyResolver.setValueSeparator(this.valueSeparator);

        // 构造成值解析器
        StringValueResolver valueResolver = strVal -> {
            String resolved = (this.ignoreUnresolvablePlaceholders ?
                    propertyResolver.resolvePlaceholders(strVal) :
                    propertyResolver.resolveRequiredPlaceholders(strVal));
            if (this.trimValues) {
                resolved = resolved.trim();
            }
            return (resolved.equals(this.nullValue) ? null : resolved);
        };

        /**
         * 1. 遍历容器中所有的BeanDefinition(出了当前这个bean本身)，尝试替换某些值的占位符
         * 2. 将 valueResolver 设置到 BeanFactory 中
         * */
        doProcessProperties(beanFactoryToProcess, valueResolver);
    }

    /**
     * Implemented for compatibility with
     * {@link org.springframework.beans.factory.config.PlaceholderConfigurerSupport}.
     * @deprecated in favor of
     * {@link #processProperties(ConfigurableListableBeanFactory, ConfigurablePropertyResolver)}
     * @throws UnsupportedOperationException in this implementation
     */
    @Override
    @Deprecated
    protected void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props) {
        throw new UnsupportedOperationException(
                "Call processProperties(ConfigurableListableBeanFactory, ConfigurablePropertyResolver) instead");
    }

    /**
     * Return the property sources that were actually applied during
     * {@link #postProcessBeanFactory(ConfigurableListableBeanFactory) post-processing}.
     * @return the property sources that were applied
     * @throws IllegalStateException if the property sources have not yet been applied
     * @since 4.0
     */
    public PropertySources getAppliedPropertySources() throws IllegalStateException {
        Assert.state(this.appliedPropertySources != null, "PropertySources have not yet been applied");
        return this.appliedPropertySources;
    }

}
