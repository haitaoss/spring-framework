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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.beans.factory.parsing.Location;
import org.springframework.beans.factory.parsing.Problem;
import org.springframework.beans.factory.parsing.ProblemReporter;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.DeferredImportSelector.Group;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.NestedIOException;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.DefaultPropertySourceFactory;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.function.Predicate;

/**
 * Parses a {@link Configuration} class definition, populating a collection of
 * {@link ConfigurationClass} objects (parsing a single Configuration class may result in
 * any number of ConfigurationClass objects because one Configuration class may import
 * another using the {@link Import} annotation).
 *
 * <p>This class helps separate the concern of parsing the structure of a Configuration
 * class from the concern of registering BeanDefinition objects based on the content of
 * that model (with the exception of {@code @ComponentScan} annotations which need to be
 * registered immediately).
 *
 * <p>This ASM-based implementation avoids reflection and eager class loading in order to
 * interoperate effectively with lazy class loading in a Spring ApplicationContext.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @see ConfigurationClassBeanDefinitionReader
 * @since 3.0
 */
class ConfigurationClassParser {

    private static final PropertySourceFactory DEFAULT_PROPERTY_SOURCE_FACTORY = new DefaultPropertySourceFactory();

    private static final Predicate<String> DEFAULT_EXCLUSION_FILTER = className -> (
            className.startsWith("java.lang.annotation.") || className.startsWith("org.springframework.stereotype."));

    private static final Comparator<DeferredImportSelectorHolder> DEFERRED_IMPORT_COMPARATOR = (o1, o2) -> AnnotationAwareOrderComparator.INSTANCE.compare(o1.getImportSelector(), o2.getImportSelector());


    private final Log logger = LogFactory.getLog(getClass());

    private final MetadataReaderFactory metadataReaderFactory;

    private final ProblemReporter problemReporter;

    private final Environment environment;

    private final ResourceLoader resourceLoader;

    private final BeanDefinitionRegistry registry;

    private final ComponentScanAnnotationParser componentScanParser;

    private final ConditionEvaluator conditionEvaluator;

    private final Map<ConfigurationClass, ConfigurationClass> configurationClasses = new LinkedHashMap<>();

    private final Map<String, ConfigurationClass> knownSuperclasses = new HashMap<>();

    private final List<String> propertySourceNames = new ArrayList<>();

    private final ImportStack importStack = new ImportStack();

    private final DeferredImportSelectorHandler deferredImportSelectorHandler = new DeferredImportSelectorHandler();

    private final SourceClass objectSourceClass = new SourceClass(Object.class);


    /**
     * Create a new {@link ConfigurationClassParser} instance that will be used
     * to populate the set of configuration classes.
     */
    public ConfigurationClassParser(MetadataReaderFactory metadataReaderFactory, ProblemReporter problemReporter, Environment environment, ResourceLoader resourceLoader, BeanNameGenerator componentScanBeanNameGenerator, BeanDefinitionRegistry registry) {

        this.metadataReaderFactory = metadataReaderFactory;
        this.problemReporter = problemReporter;
        this.environment = environment;
        this.resourceLoader = resourceLoader;
        this.registry = registry;
        this.componentScanParser = new ComponentScanAnnotationParser(environment, resourceLoader, componentScanBeanNameGenerator, registry);
        this.conditionEvaluator = new ConditionEvaluator(registry, environment, resourceLoader);
    }


    public void parse(Set<BeanDefinitionHolder> configCandidates) {
        for (BeanDefinitionHolder holder : configCandidates) {
            /**
             * 加载完所有的配置类里面的信息。
             * 这里会解析的内容是：
             * 1. @Component
             *      1.1 获取所有的成员内部类 {@link ConfigurationClassParser#processMemberClasses(ConfigurationClass, SourceClass, Predicate)}
             *      1.2 判断是否是配置类 {@link ConfigurationClassUtils#isConfigurationCandidate(AnnotationMetadata)}
             *      1.3 递归解析 {@link ConfigurationClassParser#processConfigurationClass(ConfigurationClass, Predicate)}
             *
             * 2. @PropertySources
             *
             * 3. @ComponentScans、@ComponentScan
             *      3.1 扫描指定包的内容，并注册到IOC容器中 {@link ComponentScanAnnotationParser#parse(AnnotationAttributes, String)}
             *      3.2 判断是否是配置类 {@link ConfigurationClassUtils#checkConfigurationClassCandidate(BeanDefinition, MetadataReaderFactory)}
             *      3.3 是配置类，就解析配置类 {@link ConfigurationClassParser#parse(String, String)}
             *
             * 4. @Import(非DeferredImportSelector的类)    --> 递归调用 {@link ConfigurationClassParser#processConfigurationClass(ConfigurationClass, Predicate)}
             *      处理 @Import(selector.class)，最终的结果都是按照配置类来解析 {@link ConfigurationClassParser#processImports(ConfigurationClass, SourceClass, Collection, Predicate, boolean)}
             *      4.1 selector instanceof DeferredImportSelector 。先存起来，这种类型需要延时解析 {@link DeferredImportSelectorHandler#handle(ConfigurationClass, DeferredImportSelector)}
             *      4.2 selector instanceof ImportSelector。递归解析Import {@link ConfigurationClassParser#processImports(ConfigurationClass, SourceClass, Collection, Predicate, boolean)}
             *      4.3 select instanceof ImportBeanDefinitionRegistrar。设置为 configClass 的属性 {@link ConfigurationClass#addImportBeanDefinitionRegistrar(ImportBeanDefinitionRegistrar, AnnotationMetadata)}
             *      4.4 兜底方法,这个就是关键，通过这就能看出 所有@Import导入的类，都会被解析成配置类 {@link ConfigurationClassParser#processConfigurationClass(ConfigurationClass, Predicate)}
             *
             *      可以发现 @Import(DeferredImportSelector.class) 没有立即解析，而是先存起来 带所有配置类解析完之后。在遍历 回调接口方法，递归执行 {@link ConfigurationClassParser#processImports(ConfigurationClass, SourceClass, Collection, Predicate, boolean)}
             *      虽然 @Import(ImportBeanDefinitionRegistrar.class) 也是存起来，但是他是作为 configClass的属性，后面注册BeanDefinition的时候，直接回调方法。不像
             *          @Import(DeferredImportSelector.class) 还会解析回调的结果
             *
             * 6. @ImportResource
             *      设置为 configClass 的属性 {@link ConfigurationClass#addImportedResource(String, Class)}
             *
             * 7. @Bean
             *      设置为 configClass 的属性 {@link ConfigurationClass#addBeanMethod(BeanMethod)}
             *
             * 8. 有父类，且父类含有@Bean方法
             *      8.1 knownSuperclasses.put(superclass, configClass);  记录父类被加载了，避免多个子类是配置类的情况 其父类配置类会被解析多次的问题
             *      8.2 返回父类
             *      8.2 因为是 do...while 执行 {@link ConfigurationClassParser#doProcessConfigurationClass(ConfigurationClass, SourceClass, Predicate)}
             *          所以实现了递归解析配置类的父类
             *      8.3 最终解析的 @Bean 会添加到子类配置类的属性中。比如：
             *          @Configuration
             *          class A extends C {}
             *          @Configuration
             *          class B extends C {}
             *          class C {
             *              @Bean
             *              public A bean(){
             *                  return new A();
             *              }
             *          }
             *
             *          假设 A 比 B 先解析,也就是执行 {@link ConfigurationClassParser#processConfigurationClass(ConfigurationClass, Predicate)}
             *          那么 bean 是属于 A配置类的，不是属于B配置类的
             *
             * */
            BeanDefinition bd = holder.getBeanDefinition();
            try {
                if (bd instanceof AnnotatedBeanDefinition) {
                    parse(((AnnotatedBeanDefinition) bd).getMetadata(), holder.getBeanName());
                } else if (bd instanceof AbstractBeanDefinition && ((AbstractBeanDefinition) bd).hasBeanClass()) {
                    parse(((AbstractBeanDefinition) bd).getBeanClass(), holder.getBeanName());
                } else {
                    parse(bd.getBeanClassName(), holder.getBeanName());
                }
            } catch (BeanDefinitionStoreException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new BeanDefinitionStoreException(
                        "Failed to parse configuration class [" + bd.getBeanClassName() + "]", ex);
            }
        }
        /**
         * 开始解析 @Import(DeferredImportSelector 的实现类)
         * SpringBoot 的自动装配就是通过实现这个接口实现的，目的就是延时加载。因为很多 AutoConfigurationClass 会使用 @ConditionOnMissXx 来判断是否注入bean
         * 所以需要延时加载，才能正确的判断
         * */
        this.deferredImportSelectorHandler.process();
    }

    protected final void parse(@Nullable String className, String beanName) throws IOException {
        Assert.notNull(className, "No bean class name for configuration class bean definition");
        MetadataReader reader = this.metadataReaderFactory.getMetadataReader(className);
        processConfigurationClass(new ConfigurationClass(reader, beanName), DEFAULT_EXCLUSION_FILTER);
    }

    protected final void parse(Class<?> clazz, String beanName) throws IOException {
        processConfigurationClass(new ConfigurationClass(clazz, beanName), DEFAULT_EXCLUSION_FILTER);
    }

    protected final void parse(AnnotationMetadata metadata, String beanName) throws IOException {
        processConfigurationClass(new ConfigurationClass(metadata, beanName), DEFAULT_EXCLUSION_FILTER);
    }

    /**
     * Validate each {@link ConfigurationClass} object.
     *
     * @see ConfigurationClass#validate
     */
    public void validate() {
        for (ConfigurationClass configClass : this.configurationClasses.keySet()) {
            configClass.validate(this.problemReporter);
        }
    }

    public Set<ConfigurationClass> getConfigurationClasses() {
        return this.configurationClasses.keySet();
    }


    protected void processConfigurationClass(ConfigurationClass configClass, Predicate<String> filter) throws IOException {
        if (this.conditionEvaluator.shouldSkip(configClass.getMetadata(), ConfigurationPhase.PARSE_CONFIGURATION)) {
            return;
        }

        ConfigurationClass existingClass = this.configurationClasses.get(configClass);
        if (existingClass != null) {
            if (configClass.isImported()) {
                if (existingClass.isImported()) {
                    existingClass.mergeImportedBy(configClass);
                }
                // Otherwise ignore new imported config class; existing non-imported class overrides it.
                return;
            } else {
                // Explicit bean definition found, probably replacing an import.
                // Let's remove the old one and go with the new one.
                this.configurationClasses.remove(configClass);
                this.knownSuperclasses.values()
                        .removeIf(configClass::equals);
            }
        }

        // Recursively process the configuration class and its superclass hierarchy.
        SourceClass sourceClass = asSourceClass(configClass, filter);
        do {
            sourceClass = doProcessConfigurationClass(configClass, sourceClass, filter);
        } while (sourceClass != null);

        this.configurationClasses.put(configClass, configClass);
    }

    /**
     * Apply processing and build a complete {@link ConfigurationClass} by reading the
     * annotations, members and methods from the source class. This method can be called
     * multiple times as relevant sources are discovered.
     *
     * @param configClass the configuration class being build
     * @param sourceClass a source class
     * @return the superclass, or {@code null} if none found or previously processed
     */
    @Nullable
    protected final SourceClass doProcessConfigurationClass(ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter) throws IOException {

        // 配置类有 @Component
        if (configClass.getMetadata()
                .isAnnotated(Component.class.getName())) {
            // 处理内部类 是否有配置类
            // Recursively process any member (nested) classes first
            processMemberClasses(configClass, sourceClass, filter);
        }

        // 就是拿到 @PropertySources、@PropertySource 注解
        // Process any @PropertySource annotations
        for (AnnotationAttributes propertySource : AnnotationConfigUtils.attributesForRepeatable(sourceClass.getMetadata(), PropertySources.class, org.springframework.context.annotation.PropertySource.class)) {
            if (this.environment instanceof ConfigurableEnvironment) {
                /**
                 * 就是将注解对应的资源文件 构造成 PropertySource 类型的对象，并将对象存到 {@link AbstractEnvironment#propertySources} 属性中。
                 *
                 * 使用 `context.getEnvironment().getProperty("name")` 就能读到 资源文件 中定义的内容了
                 * */
                processPropertySource(propertySource);
            } else {
                logger.info("Ignoring @PropertySource annotation on [" + sourceClass.getMetadata()
                        .getClassName() + "]. Reason: Environment must implement ConfigurableEnvironment");
            }
        }

        // 会进行扫描，得到的 BeanDefinition会注册到 Spring容器中，并且会检查是不是配置类并进行解析
        // Process any @ComponentScan annotations
        Set<AnnotationAttributes> componentScans = AnnotationConfigUtils.attributesForRepeatable(sourceClass.getMetadata(), ComponentScans.class, ComponentScan.class);
        if (!componentScans.isEmpty()
                && !this.conditionEvaluator.shouldSkip(sourceClass.getMetadata(), ConfigurationPhase.REGISTER_BEAN)) {
            for (AnnotationAttributes componentScan : componentScans) {
                // 这里就会进行扫描，得到的 BeanDefinition会注册到 Spring容器中
                // The config class is annotated with @ComponentScan -> perform the scan immediately
                Set<BeanDefinitionHolder> scannedBeanDefinitions = this.componentScanParser.parse(componentScan, sourceClass.getMetadata()
                        .getClassName());
                // Check the set of scanned definitions for any further config classes and parse recursively if needed
                for (BeanDefinitionHolder holder : scannedBeanDefinitions) {
                    BeanDefinition bdCand = holder.getBeanDefinition()
                            .getOriginatingBeanDefinition();
                    if (bdCand == null) {
                        bdCand = holder.getBeanDefinition();
                    }
                    /**
                     * 扫描到的bean，只要有 @Configuration || @Component || @ComponentScan || @Import || @ImportResource || @Bean 方法
                     * 进进行配置类的解析
                     * */
                    if (ConfigurationClassUtils.checkConfigurationClassCandidate(bdCand, this.metadataReaderFactory)) {
                        // 扫描的结果是配置类，就按照解析配置类的规则解析
                        parse(bdCand.getBeanClassName(), holder.getBeanName());
                    }
                }
            }
        }

        /**
         * 处理 @Import
         *
         * getImports(sourceClass) 就是拿到类上所有的@Import注解(会递归找)
         * */
        // Process any @Import annotations
        processImports(configClass, sourceClass, getImports(sourceClass), filter, true);

        // Process any @ImportResource annotations
        AnnotationAttributes importResource = AnnotationConfigUtils.attributesFor(sourceClass.getMetadata(), ImportResource.class);
        if (importResource != null) {
            String[] resources = importResource.getStringArray("locations");
            Class<? extends BeanDefinitionReader> readerClass = importResource.getClass("reader");
            for (String resource : resources) {
                String resolvedResource = this.environment.resolveRequiredPlaceholders(resource);
                configClass.addImportedResource(resolvedResource, readerClass);
            }
        }

        /**
         * TODOHAITAO 【@Bean 第一步】处理附加的 @Bean 方法，在这里会把@Bean 标记的方法，先存起来
         * */
        // Process individual @Bean methods
        Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(sourceClass);
        for (MethodMetadata methodMetadata : beanMethods) {
            configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
        }
        /**
         * 就是将 sourceClass 的接口递归拿到 @Bean的方法，将方法信息记录到 configClass 中
         * */
        // Process default methods on interfaces
        processInterfaces(configClass, sourceClass);

        /**
         * TODOHAITAO 【@Bean 第二步】针对父类里面有@Bean的方法，会把已经处理过的父类 存到 knownSuperclasses 这个Map中，避免重复处理
         *  所以但我们使用SpringBoot+SpringMVC的时，注册多个 WebMvcConfigurationSupport 子类时候 发现只有一个子类会生效。就是因为这个机制保证了父类里面的@Bean方法只会被注册一次
         * */
        // Process superclass, if any
        if (sourceClass.getMetadata()
                .hasSuperClass()) {
            String superclass = sourceClass.getMetadata()
                    .getSuperClassName();
            if (superclass != null && !superclass.startsWith("java")
                    && !this.knownSuperclasses.containsKey(superclass)) {
                this.knownSuperclasses.put(superclass, configClass);
                // 返回父类 递归解析
                // Superclass found, return its annotation metadata and recurse
                return sourceClass.getSuperClass();
            }
        }

        // No superclass -> processing is complete
        return null;
    }

    /**
     * Register member (nested) classes that happen to be configuration classes themselves.
     */
    private void processMemberClasses(ConfigurationClass configClass, SourceClass sourceClass, Predicate<String> filter) throws IOException {

        Collection<SourceClass> memberClasses = sourceClass.getMemberClasses();
        if (!memberClasses.isEmpty()) {
            List<SourceClass> candidates = new ArrayList<>(memberClasses.size());
            for (SourceClass memberClass : memberClasses) {
                // 内部类 是 配置类
                if (ConfigurationClassUtils.isConfigurationCandidate(memberClass.getMetadata())
                        && !memberClass.getMetadata()
                        .getClassName()
                        .equals(configClass.getMetadata()
                                .getClassName())) {
                    // 记录一下
                    candidates.add(memberClass);
                }
            }
            OrderComparator.sort(candidates);
            for (SourceClass candidate : candidates) {
                if (this.importStack.contains(configClass)) {
                    this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
                } else {
                    this.importStack.push(configClass);
                    try {
                        /**
                         * 解析配置类。
                         * 记录信息：candidate 的 {@link ConfigurationClass#importedBy} 是  configClass
                         * */
                        processConfigurationClass(candidate.asConfigClass(configClass), filter);
                    } finally {
                        this.importStack.pop();
                    }
                }
            }
        }
    }

    /**
     * Register default methods on interfaces implemented by the configuration class.
     */
    private void processInterfaces(ConfigurationClass configClass, SourceClass sourceClass) throws IOException {
        // 遍历接口
        for (SourceClass ifc : sourceClass.getInterfaces()) {
            // 接口中有@Bean的方法
            Set<MethodMetadata> beanMethods = retrieveBeanMethodMetadata(ifc);
            for (MethodMetadata methodMetadata : beanMethods) {
                // 不是抽象方法，才记录到 configClass 中
                if (!methodMetadata.isAbstract()) {
                    // A default method or other concrete method on a Java 8+ interface...
                    configClass.addBeanMethod(new BeanMethod(methodMetadata, configClass));
                }
            }
            // 递归处理接口的接口
            processInterfaces(configClass, ifc);
        }
    }

    /**
     * Retrieve the metadata for all <code>@Bean</code> methods.
     */
    private Set<MethodMetadata> retrieveBeanMethodMetadata(SourceClass sourceClass) {
        AnnotationMetadata original = sourceClass.getMetadata();
        Set<MethodMetadata> beanMethods = original.getAnnotatedMethods(Bean.class.getName());
        if (beanMethods.size() > 1 && original instanceof StandardAnnotationMetadata) {
            // Try reading the class file via ASM for deterministic declaration order...
            // Unfortunately, the JVM's standard reflection returns methods in arbitrary
            // order, even between different runs of the same application on the same JVM.
            try {
                AnnotationMetadata asm = this.metadataReaderFactory.getMetadataReader(original.getClassName())
                        .getAnnotationMetadata();
                Set<MethodMetadata> asmMethods = asm.getAnnotatedMethods(Bean.class.getName());
                if (asmMethods.size() >= beanMethods.size()) {
                    Set<MethodMetadata> selectedMethods = new LinkedHashSet<>(asmMethods.size());
                    for (MethodMetadata asmMethod : asmMethods) {
                        for (MethodMetadata beanMethod : beanMethods) {
                            if (beanMethod.getMethodName()
                                    .equals(asmMethod.getMethodName())) {
                                selectedMethods.add(beanMethod);
                                break;
                            }
                        }
                    }
                    if (selectedMethods.size() == beanMethods.size()) {
                        // All reflection-detected methods found in ASM method set -> proceed
                        beanMethods = selectedMethods;
                    }
                }
            } catch (IOException ex) {
                logger.debug("Failed to read class file via ASM for determining @Bean method order", ex);
                // No worries, let's continue with the reflection metadata we started with...
            }
        }
        return beanMethods;
    }


    /**
     * Process the given <code>@PropertySource</code> annotation metadata.
     *
     * @param propertySource metadata for the <code>@PropertySource</code> annotation found
     * @throws IOException if loading a property source failed
     */
    private void processPropertySource(AnnotationAttributes propertySource) throws IOException {
        // name
        String name = propertySource.getString("name");
        if (!StringUtils.hasLength(name)) {
            name = null;
        }
        // 编码
        String encoding = propertySource.getString("encoding");
        if (!StringUtils.hasLength(encoding)) {
            encoding = null;
        }
        // 要加载的属性文件
        String[] locations = propertySource.getStringArray("value");
        Assert.isTrue(locations.length > 0, "At least one @PropertySource(value) location is required");
        // 忽略资源找不到
        boolean ignoreResourceNotFound = propertySource.getBoolean("ignoreResourceNotFound");

        // 拿到 PropertySourceFactory
        Class<? extends PropertySourceFactory> factoryClass = propertySource.getClass("factory");
        /**
         * 注：默认是 DefaultPropertySourceFactory ，否则就反射实例化得到 PropertySourceFactory 。
         *     DefaultPropertySourceFactory 不支持解析yml格式的内容，所以要想导入 yml 格式的内容需要自定义。
         *
         * {@link YamlPropertiesFactoryBean#getObject()} 这个是Spring提供的，可解析yml内容成Properties对象，有了Properties对象
         * 就可以构造出 PropertySource 对象
         * */
        PropertySourceFactory factory = (factoryClass
                == PropertySourceFactory.class ? DEFAULT_PROPERTY_SOURCE_FACTORY : BeanUtils.instantiateClass(factoryClass));

        // 遍历 locations
        for (String location : locations) {
            try {
                /**
                 * 解析占位符，也就是说可以这么写 @PropertySource("${prop_file}")
                 * */
                String resolvedLocation = this.environment.resolveRequiredPlaceholders(location);
                /**
                 * 获取资源文件，这里会处理 classpath: 前缀
                 * {@link GenericApplicationContext#getResource(String)}
                 * {@link DefaultResourceLoader#getResource(String)}
                 * */
                Resource resource = this.resourceLoader.getResource(resolvedLocation);
                /**
                 * 一个 location 对应一个 PropertySource 然后记录到环境变量中 {@link AbstractEnvironment#propertySources}
                 * {@link DefaultPropertySourceFactory#createPropertySource(String, EncodedResource)}
                 *
                 * 注：
                 *  1. name是null，就会根据 EncodedResource 生成名字，默认就是资源文件的路径
                 *  2. 添加的特点是后添加会放到前面
                 * */
                addPropertySource(factory.createPropertySource(name, new EncodedResource(resource, encoding)));
            } catch (IllegalArgumentException | FileNotFoundException | UnknownHostException | SocketException ex) {
                // Placeholders not resolvable or resource not found when trying to open it
                if (ignoreResourceNotFound) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Properties location [" + location + "] not resolvable: " + ex.getMessage());
                    }
                } else {
                    throw ex;
                }
            }
        }
    }

    private void addPropertySource(PropertySource<?> propertySource) {
        String name = propertySource.getName();
        MutablePropertySources propertySources = ((ConfigurableEnvironment) this.environment).getPropertySources();

        // 已经添加过了
        if (this.propertySourceNames.contains(name)) {
            // 已经添加过了，那就对已有的属性值进行扩展
            // We've already added a version, we need to extend it
            PropertySource<?> existing = propertySources.get(name);
            if (existing != null) {
                // 拿到新加的 PropertySource
                PropertySource<?> newSource = (propertySource instanceof ResourcePropertySource ? ((ResourcePropertySource) propertySource).withResourceName() : propertySource);
                if (existing instanceof CompositePropertySource) {
                    /**
                     * 将新添加的扩展到 现有的里面，newSource 放到集合的前面，从而保证优先使用
                     * */
                    ((CompositePropertySource) existing).addFirstPropertySource(newSource);
                } else {
                    // 将现有的 换成 CompositePropertySource 类型的对象
                    if (existing instanceof ResourcePropertySource) {
                        existing = ((ResourcePropertySource) existing).withResourceName();
                    }
                    CompositePropertySource composite = new CompositePropertySource(name);
                    // newSource 放到集合的前面，从而保证优先使用
                    composite.addPropertySource(newSource);
                    composite.addPropertySource(existing);
                    // 替换掉
                    propertySources.replace(name, composite);
                }
                // 结束
                return;
            }
        }

        if (this.propertySourceNames.isEmpty()) {
            // 添加
            propertySources.addLast(propertySource);
        } else {
            // 拿到最后一个
            String firstProcessed = this.propertySourceNames.get(this.propertySourceNames.size() - 1);
            // 放到最后一个之前。也就是说 新添加的的会放在前面
            propertySources.addBefore(firstProcessed, propertySource);
        }
        // 记录
        this.propertySourceNames.add(name);
    }


    /**
     * Returns {@code @Import} class, considering all meta-annotations.
     */
    private Set<SourceClass> getImports(SourceClass sourceClass) throws IOException {
        Set<SourceClass> imports = new LinkedHashSet<>();
        Set<SourceClass> visited = new LinkedHashSet<>();
        collectImports(sourceClass, imports, visited);
        return imports;
    }

    /**
     * Recursively collect all declared {@code @Import} values. Unlike most
     * meta-annotations it is valid to have several {@code @Import}s declared with
     * different values; the usual process of returning values from the first
     * meta-annotation on a class is not sufficient.
     * <p>For example, it is common for a {@code @Configuration} class to declare direct
     * {@code @Import}s in addition to meta-imports originating from an {@code @Enable}
     * annotation.
     *
     * @param sourceClass the class to search
     * @param imports     the imports collected so far
     * @param visited     used to track visited classes to prevent infinite recursion
     * @throws IOException if there is any problem reading metadata from the named class
     */
    private void collectImports(SourceClass sourceClass, Set<SourceClass> imports, Set<SourceClass> visited) throws IOException {

        // 没有添加过才是 ture
        if (visited.add(sourceClass)) {
            // 拿到所有的注解
            for (SourceClass annotation : sourceClass.getAnnotations()) {
                String annName = annotation.getMetadata()
                        .getClassName();
                // 递归拿到注解中的 @Import
                if (!annName.equals(Import.class.getName())) {
                    collectImports(annotation, imports, visited);
                }
            }
            // 是@Import，就记录其value值存起来
            imports.addAll(sourceClass.getAnnotationAttributes(Import.class.getName(), "value"));
        }
    }

    private void processImports(ConfigurationClass configClass, SourceClass currentSourceClass, Collection<SourceClass> importCandidates, Predicate<String> exclusionFilter, boolean checkForCircularImports) {

        if (importCandidates.isEmpty()) {
            return;
        }

        if (checkForCircularImports && isChainedImportOnStack(configClass)) {
            this.problemReporter.error(new CircularImportProblem(configClass, this.importStack));
        } else {
            this.importStack.push(configClass);
            try {
                // 遍历所有 @Import的值
                for (SourceClass candidate : importCandidates) {
                    if (candidate.isAssignable(ImportSelector.class)) {
                        // Candidate class is an ImportSelector -> delegate to it to determine imports
                        Class<?> candidateClass = candidate.loadClass();
                        ImportSelector selector = ParserStrategyUtils.instantiateClass(candidateClass, ImportSelector.class, this.environment, this.resourceLoader, this.registry);
                        Predicate<String> selectorFilter = selector.getExclusionFilter();
                        if (selectorFilter != null) {
                            exclusionFilter = exclusionFilter.or(selectorFilter);
                        }
                        if (selector instanceof DeferredImportSelector) {
                            this.deferredImportSelectorHandler.handle(configClass, (DeferredImportSelector) selector);
                        } else {
                            String[] importClassNames = selector.selectImports(currentSourceClass.getMetadata());
                            Collection<SourceClass> importSourceClasses = asSourceClasses(importClassNames, exclusionFilter);
                            processImports(configClass, currentSourceClass, importSourceClasses, exclusionFilter, false);
                        }
                    } else if (candidate.isAssignable(ImportBeanDefinitionRegistrar.class)) {
                        // Candidate class is an ImportBeanDefinitionRegistrar ->
                        // delegate to it to register additional bean definitions
                        Class<?> candidateClass = candidate.loadClass();
                        ImportBeanDefinitionRegistrar registrar = ParserStrategyUtils.instantiateClass(candidateClass, ImportBeanDefinitionRegistrar.class, this.environment, this.resourceLoader, this.registry);
                        configClass.addImportBeanDefinitionRegistrar(registrar, currentSourceClass.getMetadata());
                    } else {
                        /**
                         * 兜底，记录导入的类和其对应的元数据
                         * 这个元数据是在 {@link ConfigurationClassPostProcessor.ImportAwareBeanPostProcessor#postProcessBeforeInitialization(Object, String)} 会用到
                         *
                         * 比如：@Import(A.class)，是给A.class作为key，value就是元数据
                         * */
                        // Candidate class not an ImportSelector or ImportBeanDefinitionRegistrar ->
                        // process it as an @Configuration class
                        this.importStack.registerImport(currentSourceClass.getMetadata(), candidate.getMetadata()
                                .getClassName());
                        /**
                         * 解析配置类。
                         * 记录信息：candidate 的 {@link ConfigurationClass#importedBy} 是  configClass
                         * */
                        processConfigurationClass(candidate.asConfigClass(configClass), exclusionFilter);
                    }
                }
            } catch (BeanDefinitionStoreException ex) {
                throw ex;
            } catch (Throwable ex) {
                throw new BeanDefinitionStoreException(
                        "Failed to process import candidates for configuration class [" + configClass.getMetadata()
                                .getClassName() + "]", ex);
            } finally {
                this.importStack.pop();
            }
        }
    }

    private boolean isChainedImportOnStack(ConfigurationClass configClass) {
        if (this.importStack.contains(configClass)) {
            String configClassName = configClass.getMetadata()
                    .getClassName();
            AnnotationMetadata importingClass = this.importStack.getImportingClassFor(configClassName);
            while (importingClass != null) {
                if (configClassName.equals(importingClass.getClassName())) {
                    return true;
                }
                importingClass = this.importStack.getImportingClassFor(importingClass.getClassName());
            }
        }
        return false;
    }

    ImportRegistry getImportRegistry() {
        return this.importStack;
    }


    /**
     * Factory method to obtain a {@link SourceClass} from a {@link ConfigurationClass}.
     */
    private SourceClass asSourceClass(ConfigurationClass configurationClass, Predicate<String> filter) throws IOException {
        AnnotationMetadata metadata = configurationClass.getMetadata();
        if (metadata instanceof StandardAnnotationMetadata) {
            return asSourceClass(((StandardAnnotationMetadata) metadata).getIntrospectedClass(), filter);
        }
        return asSourceClass(metadata.getClassName(), filter);
    }

    /**
     * Factory method to obtain a {@link SourceClass} from a {@link Class}.
     */
    SourceClass asSourceClass(@Nullable Class<?> classType, Predicate<String> filter) throws IOException {
        if (classType == null || filter.test(classType.getName())) {
            return this.objectSourceClass;
        }
        try {
            // Sanity test that we can reflectively read annotations,
            // including Class attributes; if not -> fall back to ASM
            for (Annotation ann : classType.getDeclaredAnnotations()) {
                AnnotationUtils.validateAnnotation(ann);
            }
            return new SourceClass(classType);
        } catch (Throwable ex) {
            // Enforce ASM via class name resolution
            return asSourceClass(classType.getName(), filter);
        }
    }

    /**
     * Factory method to obtain a {@link SourceClass} collection from class names.
     */
    private Collection<SourceClass> asSourceClasses(String[] classNames, Predicate<String> filter) throws IOException {
        List<SourceClass> annotatedClasses = new ArrayList<>(classNames.length);
        for (String className : classNames) {
            annotatedClasses.add(asSourceClass(className, filter));
        }
        return annotatedClasses;
    }

    /**
     * Factory method to obtain a {@link SourceClass} from a class name.
     */
    SourceClass asSourceClass(@Nullable String className, Predicate<String> filter) throws IOException {
        if (className == null || filter.test(className)) {
            return this.objectSourceClass;
        }
        if (className.startsWith("java")) {
            // Never use ASM for core java types
            try {
                return new SourceClass(ClassUtils.forName(className, this.resourceLoader.getClassLoader()));
            } catch (ClassNotFoundException ex) {
                throw new NestedIOException("Failed to load class [" + className + "]", ex);
            }
        }
        return new SourceClass(this.metadataReaderFactory.getMetadataReader(className));
    }


    @SuppressWarnings("serial")
    private static class ImportStack extends ArrayDeque<ConfigurationClass> implements ImportRegistry {

        private final MultiValueMap<String, AnnotationMetadata> imports = new LinkedMultiValueMap<>();

        public void registerImport(AnnotationMetadata importingClass, String importedClass) {
            this.imports.add(importedClass, importingClass);
        }

        @Override
        @Nullable
        public AnnotationMetadata getImportingClassFor(String importedClass) {
            return CollectionUtils.lastElement(this.imports.get(importedClass));
        }

        @Override
        public void removeImportingClass(String importingClass) {
            for (List<AnnotationMetadata> list : this.imports.values()) {
                for (Iterator<AnnotationMetadata> iterator = list.iterator(); iterator.hasNext(); ) {
                    if (iterator.next()
                            .getClassName()
                            .equals(importingClass)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        /**
         * Given a stack containing (in order)
         * <ul>
         * <li>com.acme.Foo</li>
         * <li>com.acme.Bar</li>
         * <li>com.acme.Baz</li>
         * </ul>
         * return "[Foo->Bar->Baz]".
         */
        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner("->", "[", "]");
            for (ConfigurationClass configurationClass : this) {
                joiner.add(configurationClass.getSimpleName());
            }
            return joiner.toString();
        }
    }


    private class DeferredImportSelectorHandler {

        @Nullable
        private List<DeferredImportSelectorHolder> deferredImportSelectors = new ArrayList<>();

        /**
         * Handle the specified {@link DeferredImportSelector}. If deferred import
         * selectors are being collected, this registers this instance to the list. If
         * they are being processed, the {@link DeferredImportSelector} is also processed
         * immediately according to its {@link DeferredImportSelector.Group}.
         *
         * @param configClass    the source configuration class
         * @param importSelector the selector to handle
         */
        public void handle(ConfigurationClass configClass, DeferredImportSelector importSelector) {
            DeferredImportSelectorHolder holder = new DeferredImportSelectorHolder(configClass, importSelector);
            if (this.deferredImportSelectors == null) {
                /**
                 * 解析完所有的配置类后会执行 {@link DeferredImportSelectorHandler#process()} 延时解析 @Import(DeferredImportSelector.class)
                 * 解析的时候 会把这个属性 deferredImportSelectors 置空
                 * */
                DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
                /**
                 * configurationClasses.add() 就是添加到配置类Map中
                 * */
                handler.register(holder);
                /**
                 * 回调 {@link DeferredImportSelector.Group#selectImports()}
                 * 回调结果遍历执行 递归解析 {@link ConfigurationClassParser#processImports(ConfigurationClass, SourceClass, Collection, Predicate, boolean)}
                 * 最终就是解析配置类 {@link ConfigurationClassParser#processConfigurationClass(ConfigurationClass, Predicate)}
                 *
                 * 所以说 使用 @Import(A.class) 导入的类，一定会注册为配置类
                 * */
                handler.processGroupImports();
            } else {
                this.deferredImportSelectors.add(holder);
            }
        }

        public void process() {
            List<DeferredImportSelectorHolder> deferredImports = this.deferredImportSelectors;
            this.deferredImportSelectors = null;
            try {
                if (deferredImports != null) {
                    DeferredImportSelectorGroupingHandler handler = new DeferredImportSelectorGroupingHandler();
                    deferredImports.sort(DEFERRED_IMPORT_COMPARATOR);
                    deferredImports.forEach(handler::register);
                    handler.processGroupImports();
                }
            } finally {
                this.deferredImportSelectors = new ArrayList<>();
            }
        }
    }


    private class DeferredImportSelectorGroupingHandler {

        private final Map<Object, DeferredImportSelectorGrouping> groupings = new LinkedHashMap<>();

        private final Map<AnnotationMetadata, ConfigurationClass> configurationClasses = new HashMap<>();

        public void register(DeferredImportSelectorHolder deferredImport) {
            Class<? extends Group> group = deferredImport.getImportSelector()
                    .getImportGroup();
            DeferredImportSelectorGrouping grouping = this.groupings.computeIfAbsent((group
                    != null ? group : deferredImport), key -> new DeferredImportSelectorGrouping(createGroup(group)));
            grouping.add(deferredImport);
            this.configurationClasses.put(deferredImport.getConfigurationClass()
                    .getMetadata(), deferredImport.getConfigurationClass());
        }

        public void processGroupImports() {
            for (DeferredImportSelectorGrouping grouping : this.groupings.values()) {
                Predicate<String> exclusionFilter = grouping.getCandidateFilter();
                grouping.getImports()
                        .forEach(entry -> {
                            ConfigurationClass configurationClass = this.configurationClasses.get(entry.getMetadata());
                            try {
                                processImports(configurationClass, asSourceClass(configurationClass, exclusionFilter), Collections.singleton(asSourceClass(entry.getImportClassName(), exclusionFilter)), exclusionFilter, false);
                            } catch (BeanDefinitionStoreException ex) {
                                throw ex;
                            } catch (Throwable ex) {
                                throw new BeanDefinitionStoreException(
                                        "Failed to process import candidates for configuration class ["
                                                + configurationClass.getMetadata()
                                                .getClassName() + "]", ex);
                            }
                        });
            }
        }

        private Group createGroup(@Nullable Class<? extends Group> type) {
            Class<? extends Group> effectiveType = (type != null ? type : DefaultDeferredImportSelectorGroup.class);
            return ParserStrategyUtils.instantiateClass(effectiveType, Group.class, ConfigurationClassParser.this.environment, ConfigurationClassParser.this.resourceLoader, ConfigurationClassParser.this.registry);
        }
    }


    private static class DeferredImportSelectorHolder {

        private final ConfigurationClass configurationClass;

        private final DeferredImportSelector importSelector;

        public DeferredImportSelectorHolder(ConfigurationClass configClass, DeferredImportSelector selector) {
            this.configurationClass = configClass;
            this.importSelector = selector;
        }

        public ConfigurationClass getConfigurationClass() {
            return this.configurationClass;
        }

        public DeferredImportSelector getImportSelector() {
            return this.importSelector;
        }
    }


    private static class DeferredImportSelectorGrouping {

        private final DeferredImportSelector.Group group;

        private final List<DeferredImportSelectorHolder> deferredImports = new ArrayList<>();

        DeferredImportSelectorGrouping(Group group) {
            this.group = group;
        }

        public void add(DeferredImportSelectorHolder deferredImport) {
            this.deferredImports.add(deferredImport);
        }

        /**
         * Return the imports defined by the group.
         *
         * @return each import with its associated configuration class
         */
        public Iterable<Group.Entry> getImports() {
            for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
                this.group.process(deferredImport.getConfigurationClass()
                        .getMetadata(), deferredImport.getImportSelector());
            }
            return this.group.selectImports();
        }

        public Predicate<String> getCandidateFilter() {
            Predicate<String> mergedFilter = DEFAULT_EXCLUSION_FILTER;
            for (DeferredImportSelectorHolder deferredImport : this.deferredImports) {
                Predicate<String> selectorFilter = deferredImport.getImportSelector()
                        .getExclusionFilter();
                if (selectorFilter != null) {
                    mergedFilter = mergedFilter.or(selectorFilter);
                }
            }
            return mergedFilter;
        }
    }


    private static class DefaultDeferredImportSelectorGroup implements Group {

        private final List<Entry> imports = new ArrayList<>();

        @Override
        public void process(AnnotationMetadata metadata, DeferredImportSelector selector) {
            for (String importClassName : selector.selectImports(metadata)) {
                this.imports.add(new Entry(metadata, importClassName));
            }
        }

        @Override
        public Iterable<Entry> selectImports() {
            return this.imports;
        }
    }


    /**
     * Simple wrapper that allows annotated source classes to be dealt with
     * in a uniform manner, regardless of how they are loaded.
     */
    private class SourceClass implements Ordered {

        private final Object source;  // Class or MetadataReader

        private final AnnotationMetadata metadata;

        public SourceClass(Object source) {
            this.source = source;
            if (source instanceof Class) {
                this.metadata = AnnotationMetadata.introspect((Class<?>) source);
            } else {
                this.metadata = ((MetadataReader) source).getAnnotationMetadata();
            }
        }

        public final AnnotationMetadata getMetadata() {
            return this.metadata;
        }

        @Override
        public int getOrder() {
            Integer order = ConfigurationClassUtils.getOrder(this.metadata);
            return (order != null ? order : Ordered.LOWEST_PRECEDENCE);
        }

        public Class<?> loadClass() throws ClassNotFoundException {
            if (this.source instanceof Class) {
                return (Class<?>) this.source;
            }
            String className = ((MetadataReader) this.source).getClassMetadata()
                    .getClassName();
            return ClassUtils.forName(className, resourceLoader.getClassLoader());
        }

        public boolean isAssignable(Class<?> clazz) throws IOException {
            if (this.source instanceof Class) {
                return clazz.isAssignableFrom((Class<?>) this.source);
            }
            return new AssignableTypeFilter(clazz).match((MetadataReader) this.source, metadataReaderFactory);
        }

        public ConfigurationClass asConfigClass(ConfigurationClass importedBy) {
            if (this.source instanceof Class) {
                return new ConfigurationClass((Class<?>) this.source, importedBy);
            }
            return new ConfigurationClass((MetadataReader) this.source, importedBy);
        }

        public Collection<SourceClass> getMemberClasses() throws IOException {
            Object sourceToProcess = this.source;
            if (sourceToProcess instanceof Class) {
                Class<?> sourceClass = (Class<?>) sourceToProcess;
                try {
                    Class<?>[] declaredClasses = sourceClass.getDeclaredClasses();
                    List<SourceClass> members = new ArrayList<>(declaredClasses.length);
                    for (Class<?> declaredClass : declaredClasses) {
                        members.add(asSourceClass(declaredClass, DEFAULT_EXCLUSION_FILTER));
                    }
                    return members;
                } catch (NoClassDefFoundError err) {
                    // getDeclaredClasses() failed because of non-resolvable dependencies
                    // -> fall back to ASM below
                    sourceToProcess = metadataReaderFactory.getMetadataReader(sourceClass.getName());
                }
            }

            // ASM-based resolution - safe for non-resolvable classes as well
            MetadataReader sourceReader = (MetadataReader) sourceToProcess;
            String[] memberClassNames = sourceReader.getClassMetadata()
                    .getMemberClassNames();
            List<SourceClass> members = new ArrayList<>(memberClassNames.length);
            for (String memberClassName : memberClassNames) {
                try {
                    members.add(asSourceClass(memberClassName, DEFAULT_EXCLUSION_FILTER));
                } catch (IOException ex) {
                    // Let's skip it if it's not resolvable - we're just looking for candidates
                    if (logger.isDebugEnabled()) {
                        logger.debug("Failed to resolve member class [" + memberClassName
                                + "] - not considering it as a configuration class candidate");
                    }
                }
            }
            return members;
        }

        public SourceClass getSuperClass() throws IOException {
            if (this.source instanceof Class) {
                return asSourceClass(((Class<?>) this.source).getSuperclass(), DEFAULT_EXCLUSION_FILTER);
            }
            return asSourceClass(((MetadataReader) this.source).getClassMetadata()
                    .getSuperClassName(), DEFAULT_EXCLUSION_FILTER);
        }

        public Set<SourceClass> getInterfaces() throws IOException {
            Set<SourceClass> result = new LinkedHashSet<>();
            if (this.source instanceof Class) {
                Class<?> sourceClass = (Class<?>) this.source;
                for (Class<?> ifcClass : sourceClass.getInterfaces()) {
                    result.add(asSourceClass(ifcClass, DEFAULT_EXCLUSION_FILTER));
                }
            } else {
                for (String className : this.metadata.getInterfaceNames()) {
                    result.add(asSourceClass(className, DEFAULT_EXCLUSION_FILTER));
                }
            }
            return result;
        }

        public Set<SourceClass> getAnnotations() {
            Set<SourceClass> result = new LinkedHashSet<>();
            if (this.source instanceof Class) {
                Class<?> sourceClass = (Class<?>) this.source;
                for (Annotation ann : sourceClass.getDeclaredAnnotations()) {
                    Class<?> annType = ann.annotationType();
                    if (!annType.getName()
                            .startsWith("java")) {
                        try {
                            result.add(asSourceClass(annType, DEFAULT_EXCLUSION_FILTER));
                        } catch (Throwable ex) {
                            // An annotation not present on the classpath is being ignored
                            // by the JVM's class loading -> ignore here as well.
                        }
                    }
                }
            } else {
                for (String className : this.metadata.getAnnotationTypes()) {
                    if (!className.startsWith("java")) {
                        try {
                            result.add(getRelated(className));
                        } catch (Throwable ex) {
                            // An annotation not present on the classpath is being ignored
                            // by the JVM's class loading -> ignore here as well.
                        }
                    }
                }
            }
            return result;
        }

        public Collection<SourceClass> getAnnotationAttributes(String annType, String attribute) throws IOException {
            Map<String, Object> annotationAttributes = this.metadata.getAnnotationAttributes(annType, true);
            if (annotationAttributes == null || !annotationAttributes.containsKey(attribute)) {
                return Collections.emptySet();
            }
            String[] classNames = (String[]) annotationAttributes.get(attribute);
            Set<SourceClass> result = new LinkedHashSet<>();
            for (String className : classNames) {
                result.add(getRelated(className));
            }
            return result;
        }

        private SourceClass getRelated(String className) throws IOException {
            if (this.source instanceof Class) {
                try {
                    Class<?> clazz = ClassUtils.forName(className, ((Class<?>) this.source).getClassLoader());
                    return asSourceClass(clazz, DEFAULT_EXCLUSION_FILTER);
                } catch (ClassNotFoundException ex) {
                    // Ignore -> fall back to ASM next, except for core java types.
                    if (className.startsWith("java")) {
                        throw new NestedIOException("Failed to load class [" + className + "]", ex);
                    }
                    return new SourceClass(metadataReaderFactory.getMetadataReader(className));
                }
            }
            return asSourceClass(className, DEFAULT_EXCLUSION_FILTER);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return (this == other || (other instanceof SourceClass && this.metadata.getClassName()
                    .equals(((SourceClass) other).metadata.getClassName())));
        }

        @Override
        public int hashCode() {
            return this.metadata.getClassName()
                    .hashCode();
        }

        @Override
        public String toString() {
            return this.metadata.getClassName();
        }
    }


    /**
     * {@link Problem} registered upon detection of a circular {@link Import}.
     */
    private static class CircularImportProblem extends Problem {

        public CircularImportProblem(ConfigurationClass attemptedImport, Deque<ConfigurationClass> importStack) {
            super(String.format("A circular @Import has been detected: "
                    + "Illegal attempt by @Configuration class '%s' to import class '%s' as '%s' is "
                    + "already present in the current import stack %s", importStack.element()
                    .getSimpleName(), attemptedImport.getSimpleName(), attemptedImport.getSimpleName(), importStack), new Location(importStack.element()
                    .getResource(), attemptedImport.getMetadata()));
        }
    }

}
