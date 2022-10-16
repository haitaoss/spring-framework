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

package org.springframework.context.annotation;

import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Standalone application context, accepting <em>component classes</em> as input &mdash;
 * in particular {@link Configuration @Configuration}-annotated classes, but also plain
 * {@link org.springframework.stereotype.Component @Component} types and JSR-330 compliant
 * classes using {@code javax.inject} annotations.
 *
 * <p>Allows for registering classes one by one using {@link #register(Class...)}
 * as well as for classpath scanning using {@link #scan(String...)}.
 *
 * <p>In case of multiple {@code @Configuration} classes, {@link Bean @Bean} methods
 * defined in later classes will override those defined in earlier classes. This can
 * be leveraged to deliberately override certain bean definitions via an extra
 * {@code @Configuration} class.
 *
 * <p>See {@link Configuration @Configuration}'s javadoc for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 3.0
 * @see #register
 * @see #scan
 * @see AnnotatedBeanDefinitionReader
 * @see ClassPathBeanDefinitionScanner
 * @see org.springframework.context.support.GenericXmlApplicationContext
 */
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

    private final AnnotatedBeanDefinitionReader reader;

    private final ClassPathBeanDefinitionScanner scanner;


    /**
     * Create a new AnnotationConfigApplicationContext that needs to be populated
     * through {@link #register} calls and then manually {@linkplain #refresh refreshed}.
     */
    public AnnotationConfigApplicationContext() {
        StartupStep createAnnotatedBeanDefReader = this.getApplicationStartup().start("spring.context.annotated-bean-reader.create");
        /**
         * 读取器，读取class成BeanDefinition然后注册到BeanFactory中，读取的时候会解析 @Lazy、@Primary、DependsOn、@Role、Description 等信息设置到BeanDefinition中
         *
         * 比如 将入参配置类注册到 BeanFactory 中会用到
         *  {@link AnnotationConfigApplicationContext#register(Class[])}
         *      {@link AnnotatedBeanDefinitionReader#doRegisterBean(Class, String, Class[], Supplier, BeanDefinitionCustomizer[])}
         *
         * 实例化时会执行 `AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);`
         *  {@link AnnotationConfigUtils#registerAnnotationConfigProcessors(BeanDefinitionRegistry, Object)}
         *      1. 给BeanFactory设置依赖比较器 `beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);`
         *          - 依赖类型是集合时，会使用这个排序
         *          - 依赖类型不是集合时，会使用这个拿到@Priority排序值，排序值小的就是要注入的
         *
         *      2. 给BeanFactory设置自动注入候选者解析器 `beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());`
         *          - @Lazy的处理、@Qualifier的处理
         *
         *      3. 注册bean到beanDefinitionMap中：
         *          - ConfigurationClassPostProcessor：用来解析配置类，注册BeanDefinition
         *          - AutowiredAnnotationBeanPostProcessor：处理@Autowired、@Value依赖注入
         *          - CommonAnnotationBeanPostProcessor：处理@Resource依赖注入，@PreDestroy、@PostConstruct方法回调
         *          - PersistenceAnnotationBeanPostProcessor：处理@PersistenceContext、@PersistenceUnit依赖注入的
         *          - EventListenerMethodProcessor：处理方法有@EventListener的bean，使用 EventListenerFactory 将方法构造成 ApplicationListener 注册到事件广播器
         *          - DefaultEventListenerFactory：用来将@EventListener标注的方法解析成ApplicationListener的
         * */
        this.reader = new AnnotatedBeanDefinitionReader(this);
        createAnnotatedBeanDefReader.end();
        /**
         * 扫描器，用来扫描class文件的。
         *
         * 在实例化过程中会解析`META-INF/spring.components`文件，然后在 {@link ClassPathScanningCandidateComponentProvider#findCandidateComponents(String)} 会根据索引文件是否有内容
         * 从而决定是索引扫描还是包扫描
         *
         * `CandidateComponentsIndexLoader.loadIndex(this.resourcePatternResolver.getClassLoader());`
         * */
        this.scanner = new ClassPathBeanDefinitionScanner(this);
    }

    /**
     * Create a new AnnotationConfigApplicationContext with the given DefaultListableBeanFactory.
     * @param beanFactory the DefaultListableBeanFactory instance to use for this context
     */
    public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
        super(beanFactory);
        this.reader = new AnnotatedBeanDefinitionReader(this);
        this.scanner = new ClassPathBeanDefinitionScanner(this);
    }

    /**
     * Create a new AnnotationConfigApplicationContext, deriving bean definitions
     * from the given component classes and automatically refreshing the context.
     *
     * @param componentClasses one or more component classes &mdash; for example,
     *                         {@link Configuration @Configuration} classes
     */
    public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
        // 构造 DefaultListableBeanFactory、AnnotatedBeanDefinitionReader、ClassPathBeanDefinitionScanner
        this();
        register(componentClasses);
        refresh();
    }

    /**
     * Create a new AnnotationConfigApplicationContext, scanning for components
     * in the given packages, registering bean definitions for those components,
     * and automatically refreshing the context.
     * @param basePackages the packages to scan for component classes
     */
    public AnnotationConfigApplicationContext(String... basePackages) {
        this();
        scan(basePackages);
        refresh();
    }


    /**
     * Propagate the given custom {@code Environment} to the underlying
     * {@link AnnotatedBeanDefinitionReader} and {@link ClassPathBeanDefinitionScanner}.
     */
    @Override
    public void setEnvironment(ConfigurableEnvironment environment) {
        super.setEnvironment(environment);
        this.reader.setEnvironment(environment);
        this.scanner.setEnvironment(environment);
    }

    /**
     * Provide a custom {@link BeanNameGenerator} for use with {@link AnnotatedBeanDefinitionReader}
     * and/or {@link ClassPathBeanDefinitionScanner}, if any.
     * <p>Default is {@link AnnotationBeanNameGenerator}.
     * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
     * and/or {@link #scan(String...)}.
     * @see AnnotatedBeanDefinitionReader#setBeanNameGenerator
     * @see ClassPathBeanDefinitionScanner#setBeanNameGenerator
     * @see AnnotationBeanNameGenerator
     * @see FullyQualifiedAnnotationBeanNameGenerator
     */
    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.reader.setBeanNameGenerator(beanNameGenerator);
        this.scanner.setBeanNameGenerator(beanNameGenerator);
        getBeanFactory().registerSingleton(
                AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
    }

    /**
     * Set the {@link ScopeMetadataResolver} to use for registered component classes.
     * <p>The default is an {@link AnnotationScopeMetadataResolver}.
     * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
     * and/or {@link #scan(String...)}.
     */
    public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
        this.reader.setScopeMetadataResolver(scopeMetadataResolver);
        this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
    }


    //---------------------------------------------------------------------
    // Implementation of AnnotationConfigRegistry
    //---------------------------------------------------------------------

    /**
     * Register one or more component classes to be processed.
     * <p>Note that {@link #refresh()} must be called in order for the context
     * to fully process the new classes.
     * @param componentClasses one or more component classes &mdash; for example,
     * {@link Configuration @Configuration} classes
     * @see #scan(String...)
     * @see #refresh()
     */
    @Override
    public void register(Class<?>... componentClasses) {
        Assert.notEmpty(componentClasses, "At least one component class must be specified");
        StartupStep registerComponentClass = this.getApplicationStartup().start("spring.context.component-classes.register")
                .tag("classes", () -> Arrays.toString(componentClasses));
        this.reader.register(componentClasses);
        registerComponentClass.end();
    }

    /**
     * Perform a scan within the specified base packages.
     * <p>Note that {@link #refresh()} must be called in order for the context
     * to fully process the new classes.
     * @param basePackages the packages to scan for component classes
     * @see #register(Class...)
     * @see #refresh()
     */
    @Override
    public void scan(String... basePackages) {
        Assert.notEmpty(basePackages, "At least one base package must be specified");
        StartupStep scanPackages = this.getApplicationStartup().start("spring.context.base-packages.scan")
                .tag("packages", () -> Arrays.toString(basePackages));
        this.scanner.scan(basePackages);
        scanPackages.end();
    }


    //---------------------------------------------------------------------
    // Adapt superclass registerBean calls to AnnotatedBeanDefinitionReader
    //---------------------------------------------------------------------

    @Override
    public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
                                 @Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

        this.reader.registerBean(beanClass, beanName, supplier, customizers);
    }

}
