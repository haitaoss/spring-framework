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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.parsing.SourceExtractor;
import org.springframework.beans.factory.support.*;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.StandardMethodMetadata;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.xml.sax.InputSource;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Reads a given fully-populated set of ConfigurationClass instances, registering bean
 * definitions with the given {@link BeanDefinitionRegistry} based on its contents.
 *
 * <p>This class was modeled after the {@link BeanDefinitionReader} hierarchy, but does
 * not implement/extend any of its artifacts as a set of configuration classes is not a
 * {@link Resource}.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Sebastien Deleuze
 * @since 3.0
 * @see ConfigurationClassParser
 */
class ConfigurationClassBeanDefinitionReader {

    private static final Log logger = LogFactory.getLog(ConfigurationClassBeanDefinitionReader.class);

    private static final ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

    /**
     * Boolean flag controlled by a {@code spring.xml.ignore} system property that instructs Spring to
     * ignore XML, i.e. to not initialize the XML-related infrastructure.
     * <p>The default is "false".
     */
    private static final boolean shouldIgnoreXml = SpringProperties.getFlag("spring.xml.ignore");

    private final BeanDefinitionRegistry registry;

    private final SourceExtractor sourceExtractor;

    private final ResourceLoader resourceLoader;

    private final Environment environment;

    private final BeanNameGenerator importBeanNameGenerator;

    private final ImportRegistry importRegistry;

    private final ConditionEvaluator conditionEvaluator;


    /**
     * Create a new {@link ConfigurationClassBeanDefinitionReader} instance
     * that will be used to populate the given {@link BeanDefinitionRegistry}.
     */
    ConfigurationClassBeanDefinitionReader(BeanDefinitionRegistry registry, SourceExtractor sourceExtractor,
                                           ResourceLoader resourceLoader, Environment environment, BeanNameGenerator importBeanNameGenerator,
                                           ImportRegistry importRegistry) {

        this.registry = registry;
        this.sourceExtractor = sourceExtractor;
        this.resourceLoader = resourceLoader;
        this.environment = environment;
        this.importBeanNameGenerator = importBeanNameGenerator;
        this.importRegistry = importRegistry;
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
    }


    /**
     * Read {@code configurationModel}, registering bean definitions
     * with the registry based on its contents.
     */
    public void loadBeanDefinitions(Set<ConfigurationClass> configurationModel) {
        TrackedConditionEvaluator trackedConditionEvaluator = new TrackedConditionEvaluator();
        for (ConfigurationClass configClass : configurationModel) {
            loadBeanDefinitionsForConfigurationClass(configClass, trackedConditionEvaluator);
        }
    }

    /**
     * Read a particular {@link ConfigurationClass}, registering bean definitions
     * for the class itself and all of its {@link Bean} methods.
     */
    private void loadBeanDefinitionsForConfigurationClass(
            ConfigurationClass configClass, TrackedConditionEvaluator trackedConditionEvaluator) {

        // 会进行 @Conditional 校验，应该跳过 就不注册到BeanDefinitionMap了
        if (trackedConditionEvaluator.shouldSkip(configClass)) {
            String beanName = configClass.getBeanName();
            if (StringUtils.hasLength(beanName) && this.registry.containsBeanDefinition(beanName)) {
                this.registry.removeBeanDefinition(beanName);
            }
            this.importRegistry.removeImportingClass(configClass.getMetadata().getClassName());
            return;
        }

        // 是通过 @Import 导入的类,或者是成员内部类是配置类的情况
        if (configClass.isImported()) {
            /**
             * 这些配置类比较特殊，还没有注册到BeanFactory中，所以这一步是将这种配置到注册到BeanFactory
             * */
            registerBeanDefinitionForImportedConfigurationClass(configClass);
        }
        for (BeanMethod beanMethod : configClass.getBeanMethods()) {
            /**
             * TODOHAITAO 【@Bean 第四步】将配置类里面的@Bean方法，解析成beanDefinition，然后注册到 beanDefinitionMap 中
             * */
            loadBeanDefinitionsForBeanMethod(beanMethod);
        }

        /**
         * 加载 @ImportResource
         * 会使用  XmlBeanDefinitionReader 进行读取和解析
         * {@link XmlBeanDefinitionReader#loadBeanDefinitions(Resource)}
         * {@link XmlBeanDefinitionReader#doLoadBeanDefinitions(InputSource, Resource)}
         * */
        loadBeanDefinitionsFromImportedResources(configClass.getImportedResources());

        /**
         *  回调 {@link ImportBeanDefinitionRegistrar#registerBeanDefinitions(AnnotationMetadata, BeanDefinitionRegistry, BeanNameGenerator)}
         *  ioc 容器已经作为入参传入了，你想做啥任你开心
         * */
        loadBeanDefinitionsFromRegistrars(configClass.getImportBeanDefinitionRegistrars());
    }

    /**
     * Register the {@link Configuration} class itself as a bean definition.
     */
    private void registerBeanDefinitionForImportedConfigurationClass(ConfigurationClass configClass) {
        AnnotationMetadata metadata = configClass.getMetadata();
        AnnotatedGenericBeanDefinition configBeanDef = new AnnotatedGenericBeanDefinition(metadata);

        // 拿到 @Scope 信息
        ScopeMetadata scopeMetadata = scopeMetadataResolver.resolveScopeMetadata(configBeanDef);
        configBeanDef.setScope(scopeMetadata.getScopeName());
        String configBeanName = this.importBeanNameGenerator.generateBeanName(configBeanDef, this.registry);
        // 就是将通用的注解值 设置到 BeanDefinition中
        AnnotationConfigUtils.processCommonDefinitionAnnotations(configBeanDef, metadata);

        BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(configBeanDef, configBeanName);
        /**
         * 看看是否需要做 额外处理，和这里是一样的逻辑
         * {@link ClassPathBeanDefinitionScanner#doScan(String...)}
         * */
        definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
        // 注册
        this.registry.registerBeanDefinition(definitionHolder.getBeanName(), definitionHolder.getBeanDefinition());
        configClass.setBeanName(configBeanName);

        if (logger.isTraceEnabled()) {
            logger.trace("Registered bean definition for imported class '" + configBeanName + "'");
        }
    }

    /**
     * Read the given {@link BeanMethod}, registering bean definitions
     * with the BeanDefinitionRegistry based on its contents.
     */
    @SuppressWarnings("deprecation")  // for RequiredAnnotationBeanPostProcessor.SKIP_REQUIRED_CHECK_ATTRIBUTE
    private void loadBeanDefinitionsForBeanMethod(BeanMethod beanMethod) {
        ConfigurationClass configClass = beanMethod.getConfigurationClass();
        MethodMetadata metadata = beanMethod.getMetadata();
        String methodName = metadata.getMethodName();

        // 会进行 @Conditional 校验，应该跳过，就 return 也就是不会注册到 BeanDefinitionMap了
        // Do we need to mark the bean as skipped by its condition?
        if (this.conditionEvaluator.shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN)) {
            configClass.skippedBeanMethods.add(methodName);
            return;
        }
        if (configClass.skippedBeanMethods.contains(methodName)) {
            return;
        }

        AnnotationAttributes bean = AnnotationConfigUtils.attributesFor(metadata, Bean.class);
        Assert.state(bean != null, "No @Bean annotation attributes");

        // Consider name and any aliases
        List<String> names = new ArrayList<>(Arrays.asList(bean.getStringArray("name")));
        String beanName = (!names.isEmpty() ? names.remove(0) : methodName);

        // Register aliases even when overridden
        for (String alias : names) {
            this.registry.registerAlias(beanName, alias);
        }

        // Has this effectively been overridden before (e.g. via XML)?
        if (isOverriddenByExistingDefinition(beanMethod, beanName)) {
            if (beanName.equals(beanMethod.getConfigurationClass().getBeanName())) {
                throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
                        beanName, "Bean name derived from @Bean method '" + beanMethod.getMetadata().getMethodName() +
                                  "' clashes with bean name for containing configuration class; please make those names unique!");
            }
            return;
        }

        ConfigurationClassBeanDefinition beanDef = new ConfigurationClassBeanDefinition(configClass, metadata, beanName);
        beanDef.setSource(this.sourceExtractor.extractSource(metadata, configClass.getResource()));

        if (metadata.isStatic()) {
            // static @Bean method
            if (configClass.getMetadata() instanceof StandardAnnotationMetadata) {
                beanDef.setBeanClass(((StandardAnnotationMetadata) configClass.getMetadata()).getIntrospectedClass());
            } else {
                beanDef.setBeanClassName(configClass.getMetadata().getClassName());
            }
            beanDef.setUniqueFactoryMethodName(methodName);
        } else {
            /**
             * 如果是实例的@Bean 方法，要记录一下配置类为 该@Bean 的 FactoryBean。
             * 	但创建 @Bean实例时，必须要先创建他的  FactoryBean。
             * 	因此最好不要通过@Bean 来注册 BeanFactoryPostProcessor 和 BeanPostProcessor。因为会打破bean工厂和bean的生命周期
             * */
            // instance @Bean method。
            beanDef.setFactoryBeanName(configClass.getBeanName());
            /**
             * 设置工厂方法名字，在实例化bean的时候，如果BeanDefinition有这个值 会先会实例化其Factory
             * {@link AbstractAutowireCapableBeanFactory#createBeanInstance(String, RootBeanDefinition, Object[])}
             * */
            beanDef.setUniqueFactoryMethodName(methodName);
        }

        if (metadata instanceof StandardMethodMetadata) {
            beanDef.setResolvedFactoryMethod(((StandardMethodMetadata) metadata).getIntrospectedMethod());
        }

        beanDef.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        beanDef.setAttribute(org.springframework.beans.factory.annotation.RequiredAnnotationBeanPostProcessor.
                SKIP_REQUIRED_CHECK_ATTRIBUTE, Boolean.TRUE);

        AnnotationConfigUtils.processCommonDefinitionAnnotations(beanDef, metadata);

        Autowire autowire = bean.getEnum("autowire");
        if (autowire.isAutowire()) {
            beanDef.setAutowireMode(autowire.value());
        }

        boolean autowireCandidate = bean.getBoolean("autowireCandidate");
        if (!autowireCandidate) {
            beanDef.setAutowireCandidate(false);
        }

        String initMethodName = bean.getString("initMethod");
        if (StringUtils.hasText(initMethodName)) {
            beanDef.setInitMethodName(initMethodName);
        }

        String destroyMethodName = bean.getString("destroyMethod");
        beanDef.setDestroyMethodName(destroyMethodName);

        // Consider scoping
        ScopedProxyMode proxyMode = ScopedProxyMode.NO;
        AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(metadata, Scope.class);
        if (attributes != null) {
            beanDef.setScope(attributes.getString("value"));
            proxyMode = attributes.getEnum("proxyMode");
            if (proxyMode == ScopedProxyMode.DEFAULT) {
                proxyMode = ScopedProxyMode.NO;
            }
        }

        // Replace the original bean definition with the target one, if necessary
        BeanDefinition beanDefToRegister = beanDef;
        if (proxyMode != ScopedProxyMode.NO) {
            BeanDefinitionHolder proxyDef = ScopedProxyCreator.createScopedProxy(
                    new BeanDefinitionHolder(beanDef, beanName), this.registry,
                    proxyMode == ScopedProxyMode.TARGET_CLASS);
            beanDefToRegister = new ConfigurationClassBeanDefinition(
                    (RootBeanDefinition) proxyDef.getBeanDefinition(), configClass, metadata, beanName);
        }

        if (logger.isTraceEnabled()) {
            logger.trace(String.format("Registering bean definition for @Bean method %s.%s()",
                    configClass.getMetadata().getClassName(), beanName));
        }
        this.registry.registerBeanDefinition(beanName, beanDefToRegister);
    }

    protected boolean isOverriddenByExistingDefinition(BeanMethod beanMethod, String beanName) {
        if (!this.registry.containsBeanDefinition(beanName)) {
            return false;
        }
        BeanDefinition existingBeanDef = this.registry.getBeanDefinition(beanName);

        // Is the existing bean definition one that was created from a configuration class?
        // -> allow the current bean method to override, since both are at second-pass level.
        // However, if the bean method is an overloaded case on the same configuration class,
        // preserve the existing bean definition.
        if (existingBeanDef instanceof ConfigurationClassBeanDefinition) {
            ConfigurationClassBeanDefinition ccbd = (ConfigurationClassBeanDefinition) existingBeanDef;
            if (ccbd.getMetadata().getClassName().equals(
                    beanMethod.getConfigurationClass().getMetadata().getClassName())) {
                if (ccbd.getFactoryMethodMetadata().getMethodName().equals(ccbd.getFactoryMethodName())) {
                    ccbd.setNonUniqueFactoryMethodName(ccbd.getFactoryMethodMetadata().getMethodName());
                }
                return true;
            } else {
                return false;
            }
        }

        // A bean definition resulting from a component scan can be silently overridden
        // by an @Bean method, as of 4.2...
        if (existingBeanDef instanceof ScannedGenericBeanDefinition) {
            return false;
        }

        // Has the existing bean definition bean marked as a framework-generated bean?
        // -> allow the current bean method to override it, since it is application-level
        if (existingBeanDef.getRole() > BeanDefinition.ROLE_APPLICATION) {
            return false;
        }

        // At this point, it's a top-level override (probably XML), just having been parsed
        // before configuration class processing kicks in...
        if (this.registry instanceof DefaultListableBeanFactory &&
            !((DefaultListableBeanFactory) this.registry).isAllowBeanDefinitionOverriding()) {
            throw new BeanDefinitionStoreException(beanMethod.getConfigurationClass().getResource().getDescription(),
                    beanName, "@Bean definition illegally overridden by existing bean definition: " + existingBeanDef);
        }
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Skipping bean definition for %s: a definition for bean '%s' " +
                                       "already exists. This top-level bean definition is considered as an override.",
                    beanMethod, beanName));
        }
        return true;
    }

    private void loadBeanDefinitionsFromImportedResources(
            Map<String, Class<? extends BeanDefinitionReader>> importedResources) {

        Map<Class<?>, BeanDefinitionReader> readerInstanceCache = new HashMap<>();

        importedResources.forEach((resource, readerClass) -> {
            // Default reader selection necessary?
            if (BeanDefinitionReader.class == readerClass) {
                if (StringUtils.endsWithIgnoreCase(resource, ".groovy")) {
                    // When clearly asking for Groovy, that's what they'll get...
                    readerClass = GroovyBeanDefinitionReader.class;
                } else if (shouldIgnoreXml) {
                    throw new UnsupportedOperationException("XML support disabled");
                } else {
                    // Primarily ".xml" files but for any other extension as well
                    readerClass = XmlBeanDefinitionReader.class;
                }
            }

            BeanDefinitionReader reader = readerInstanceCache.get(readerClass);
            if (reader == null) {
                try {
                    // Instantiate the specified BeanDefinitionReader
                    reader = readerClass.getConstructor(BeanDefinitionRegistry.class).newInstance(this.registry);
                    // Delegate the current ResourceLoader to it if possible
                    if (reader instanceof AbstractBeanDefinitionReader) {
                        AbstractBeanDefinitionReader abdr = ((AbstractBeanDefinitionReader) reader);
                        abdr.setResourceLoader(this.resourceLoader);
                        abdr.setEnvironment(this.environment);
                    }
                    readerInstanceCache.put(readerClass, reader);
                } catch (Throwable ex) {
                    throw new IllegalStateException(
                            "Could not instantiate BeanDefinitionReader class [" + readerClass.getName() + "]");
                }
            }

            // TODO SPR-6310: qualify relative path locations as done in AbstractContextLoader.modifyLocations
            reader.loadBeanDefinitions(resource);
        });
    }

    private void loadBeanDefinitionsFromRegistrars(Map<ImportBeanDefinitionRegistrar, AnnotationMetadata> registrars) {
        registrars.forEach((registrar, metadata) ->
                registrar.registerBeanDefinitions(metadata, this.registry, this.importBeanNameGenerator));
    }


    /**
     * {@link RootBeanDefinition} marker subclass used to signify that a bean definition
     * was created from a configuration class as opposed to any other configuration source.
     * Used in bean overriding cases where it's necessary to determine whether the bean
     * definition was created externally.
     */
    @SuppressWarnings("serial")
    private static class ConfigurationClassBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

        private final AnnotationMetadata annotationMetadata;

        private final MethodMetadata factoryMethodMetadata;

        private final String derivedBeanName;

        public ConfigurationClassBeanDefinition(
                ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String derivedBeanName) {

            this.annotationMetadata = configClass.getMetadata();
            this.factoryMethodMetadata = beanMethodMetadata;
            this.derivedBeanName = derivedBeanName;
            setResource(configClass.getResource());
            setLenientConstructorResolution(false);
        }

        public ConfigurationClassBeanDefinition(RootBeanDefinition original,
                                                ConfigurationClass configClass, MethodMetadata beanMethodMetadata, String derivedBeanName) {
            super(original);
            this.annotationMetadata = configClass.getMetadata();
            this.factoryMethodMetadata = beanMethodMetadata;
            this.derivedBeanName = derivedBeanName;
        }

        private ConfigurationClassBeanDefinition(ConfigurationClassBeanDefinition original) {
            super(original);
            this.annotationMetadata = original.annotationMetadata;
            this.factoryMethodMetadata = original.factoryMethodMetadata;
            this.derivedBeanName = original.derivedBeanName;
        }

        @Override
        public AnnotationMetadata getMetadata() {
            return this.annotationMetadata;
        }

        @Override
        @NonNull
        public MethodMetadata getFactoryMethodMetadata() {
            return this.factoryMethodMetadata;
        }

        @Override
        public boolean isFactoryMethod(Method candidate) {
            return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate) &&
                    BeanAnnotationHelper.determineBeanNameFor(candidate).equals(this.derivedBeanName));
        }

        @Override
        public ConfigurationClassBeanDefinition cloneBeanDefinition() {
            return new ConfigurationClassBeanDefinition(this);
        }
    }


    /**
     * Evaluate {@code @Conditional} annotations, tracking results and taking into
     * account 'imported by'.
     */
    private class TrackedConditionEvaluator {

        private final Map<ConfigurationClass, Boolean> skipped = new HashMap<>();

        public boolean shouldSkip(ConfigurationClass configClass) {
            Boolean skip = this.skipped.get(configClass);
            if (skip == null) {
                if (configClass.isImported()) {
                    boolean allSkipped = true;
                    /**
                     * importedBy 指的是 导入 configClass 的类。
                     *
                     * 举例：configClass 是 B；importedBy 是 [A,C]
                     *  @Import(B.class)
                     *  class A {}
                     *
                     *  @Component
                     *  class C {
                     *      @Component
                     *      public class B {}
                     *  }
                     * */
                    for (ConfigurationClass importedBy : configClass.getImportedBy()) {
                        // 只要有一个不应该跳过，那就是需要判断当前 configClass 是否应该跳过
                        if (!shouldSkip(importedBy)) {
                            allSkipped = false;
                            break;
                        }
                    }
                    // 导入 configClass 的类，都跳过了，configClass 也就没必要判断了 直接就是要跳过
                    if (allSkipped) {
                        // The config classes that imported this one were all skipped, therefore we are skipped...
                        skip = true;
                    }
                }
                // 没有值，说明需要判断一下
                if (skip == null) {
                    // 进行 @Conditional 校验
                    skip = conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN);
                }
                // 记录值
                this.skipped.put(configClass, skip);
            }
            return skip;
        }
    }

}
