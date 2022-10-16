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

package org.springframework.beans.factory.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.*;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessor}
 * implementation that autowires annotated fields, setter methods, and arbitrary
 * config methods. Such members to be injected are detected through annotations:
 * by default, Spring's {@link Autowired @Autowired} and {@link Value @Value}
 * annotations.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Inject @Inject} annotation,
 * if available, as a direct alternative to Spring's own {@code @Autowired}.
 *
 * <h3>Autowired Constructors</h3>
 * <p>Only one constructor of any given bean class may declare this annotation with
 * the 'required' attribute set to {@code true}, indicating <i>the</i> constructor
 * to autowire when used as a Spring bean. Furthermore, if the 'required' attribute
 * is set to {@code true}, only a single constructor may be annotated with
 * {@code @Autowired}. If multiple <i>non-required</i> constructors declare the
 * annotation, they will be considered as candidates for autowiring. The constructor
 * with the greatest number of dependencies that can be satisfied by matching beans
 * in the Spring container will be chosen. If none of the candidates can be satisfied,
 * then a primary/default constructor (if present) will be used. If a class only
 * declares a single constructor to begin with, it will always be used, even if not
 * annotated. An annotated constructor does not have to be public.
 *
 * <h3>Autowired Fields</h3>
 * <p>Fields are injected right after construction of a bean, before any
 * config methods are invoked. Such a config field does not have to be public.
 *
 * <h3>Autowired Methods</h3>
 * <p>Config methods may have an arbitrary name and any number of arguments; each of
 * those arguments will be autowired with a matching bean in the Spring container.
 * Bean property setter methods are effectively just a special case of such a
 * general config method. Config methods do not have to be public.
 *
 * <h3>Annotation Config vs. XML Config</h3>
 * <p>A default {@code AutowiredAnnotationBeanPostProcessor} will be registered
 * by the "context:annotation-config" and "context:component-scan" XML tags.
 * Remove or turn off the default annotation configuration there if you intend
 * to specify a custom {@code AutowiredAnnotationBeanPostProcessor} bean definition.
 *
 * <p><b>NOTE:</b> Annotation injection will be performed <i>before</i> XML injection;
 * thus the latter configuration will override the former for properties wired through
 * both approaches.
 *
 * <h3>{@literal @}Lookup Methods</h3>
 * <p>In addition to regular injection points as discussed above, this post-processor
 * also handles Spring's {@link Lookup @Lookup} annotation which identifies lookup
 * methods to be replaced by the container at runtime. This is essentially a type-safe
 * version of {@code getBean(Class, args)} and {@code getBean(String, args)}.
 * See {@link Lookup @Lookup's javadoc} for details.
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 2.5
 * @see #setAutowiredAnnotationType
 * @see Autowired
 * @see Value
 */
public class AutowiredAnnotationBeanPostProcessor implements SmartInstantiationAwareBeanPostProcessor,
        MergedBeanDefinitionPostProcessor, PriorityOrdered, BeanFactoryAware {

    protected final Log logger = LogFactory.getLog(getClass());

    private final Set<Class<? extends Annotation>> autowiredAnnotationTypes = new LinkedHashSet<>(4);

    private String requiredParameterName = "required";

    private boolean requiredParameterValue = true;

    private int order = Ordered.LOWEST_PRECEDENCE - 2;

    @Nullable
    private ConfigurableListableBeanFactory beanFactory;

    /**
     * 记录这个beanName 已经处理过了Lookup注解的检查了
     */
    private final Set<String> lookupMethodsChecked = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

    private final Map<Class<?>, Constructor<?>[]> candidateConstructorsCache = new ConcurrentHashMap<>(256);

    private final Map<String, InjectionMetadata> injectionMetadataCache = new ConcurrentHashMap<>(256);


    /**
     * Create a new {@code AutowiredAnnotationBeanPostProcessor} for Spring's
     * standard {@link Autowired @Autowired} and {@link Value @Value} annotations.
     * <p>Also supports JSR-330's {@link javax.inject.Inject @Inject} annotation,
     * if available.
     */
    @SuppressWarnings("unchecked")
    public AutowiredAnnotationBeanPostProcessor() {
        this.autowiredAnnotationTypes.add(Autowired.class);
        this.autowiredAnnotationTypes.add(Value.class);
        try {
            this.autowiredAnnotationTypes.add((Class<? extends Annotation>)
                    ClassUtils.forName("javax.inject.Inject", AutowiredAnnotationBeanPostProcessor.class.getClassLoader()));
            logger.trace("JSR-330 'javax.inject.Inject' annotation found and supported for autowiring");
        } catch (ClassNotFoundException ex) {
            // JSR-330 API not available - simply skip.
        }
    }


    /**
     * Set the 'autowired' annotation type, to be used on constructors, fields,
     * setter methods, and arbitrary config methods.
     * <p>The default autowired annotation types are the Spring-provided
     * {@link Autowired @Autowired} and {@link Value @Value} annotations as well
     * as JSR-330's {@link javax.inject.Inject @Inject} annotation, if available.
     * <p>This setter property exists so that developers can provide their own
     * (non-Spring-specific) annotation type to indicate that a member is supposed
     * to be autowired.
     */
    public void setAutowiredAnnotationType(Class<? extends Annotation> autowiredAnnotationType) {
        Assert.notNull(autowiredAnnotationType, "'autowiredAnnotationType' must not be null");
        this.autowiredAnnotationTypes.clear();
        this.autowiredAnnotationTypes.add(autowiredAnnotationType);
    }

    /**
     * Set the 'autowired' annotation types, to be used on constructors, fields,
     * setter methods, and arbitrary config methods.
     * <p>The default autowired annotation types are the Spring-provided
     * {@link Autowired @Autowired} and {@link Value @Value} annotations as well
     * as JSR-330's {@link javax.inject.Inject @Inject} annotation, if available.
     * <p>This setter property exists so that developers can provide their own
     * (non-Spring-specific) annotation types to indicate that a member is supposed
     * to be autowired.
     */
    public void setAutowiredAnnotationTypes(Set<Class<? extends Annotation>> autowiredAnnotationTypes) {
        Assert.notEmpty(autowiredAnnotationTypes, "'autowiredAnnotationTypes' must not be empty");
        this.autowiredAnnotationTypes.clear();
        this.autowiredAnnotationTypes.addAll(autowiredAnnotationTypes);
    }

    /**
     * Set the name of an attribute of the annotation that specifies whether it is required.
     * @see #setRequiredParameterValue(boolean)
     */
    public void setRequiredParameterName(String requiredParameterName) {
        this.requiredParameterName = requiredParameterName;
    }

    /**
     * Set the boolean value that marks a dependency as required.
     * <p>For example if using 'required=true' (the default), this value should be
     * {@code true}; but if using 'optional=false', this value should be {@code false}.
     * @see #setRequiredParameterName(String)
     */
    public void setRequiredParameterValue(boolean requiredParameterValue) {
        this.requiredParameterValue = requiredParameterValue;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "AutowiredAnnotationBeanPostProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
        }
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }


    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
        InjectionMetadata metadata = findAutowiringMetadata(beanName, beanType, null);
        metadata.checkConfigMembers(beanDefinition);
    }

    @Override
    public void resetBeanDefinition(String beanName) {
        this.lookupMethodsChecked.remove(beanName);
        this.injectionMetadataCache.remove(beanName);
    }

    @Override
    @Nullable
    public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, final String beanName)
            throws BeanCreationException {

        // 没有校验过是否有Lookup方法
        // Let's check for lookup methods here...
        if (!this.lookupMethodsChecked.contains(beanName)) {
            /**
             * 是候选类，可以简单理解。不是java包下的类，不是Ordered类 就是候选类
             * */
            if (AnnotationUtils.isCandidateClass(beanClass, Lookup.class)) {
                try {
                    Class<?> targetClass = beanClass;
                    do {
                        // 遍历类的所有方法
                        ReflectionUtils.doWithLocalMethods(targetClass, method -> {
                            Lookup lookup = method.getAnnotation(Lookup.class);
                            // 方法上有 @Lookup 注解
                            if (lookup != null) {
                                Assert.state(this.beanFactory != null, "No BeanFactory available");
                                // 装饰成 LookupOverride 对象
                                LookupOverride override = new LookupOverride(method, lookup.value());
                                try {
                                    // 拿到这个beanName 的 BeanDefinition
                                    RootBeanDefinition mbd = (RootBeanDefinition)
                                            this.beanFactory.getMergedBeanDefinition(beanName);
                                    /**
                                     * 记录到 BeanDefinition 中
                                     *
                                     * 会在bean实例化的时候，判断是否有 {@link AbstractBeanDefinition#methodOverrides} 属性，属性不为空，
                                     * 就会创建代理对象。
                                     * {@link AbstractAutowireCapableBeanFactory#instantiateBean(String, RootBeanDefinition)}
                                     * {@link SimpleInstantiationStrategy#instantiate(RootBeanDefinition, String, BeanFactory)}
                                     * {@link CglibSubclassingInstantiationStrategy#instantiateWithMethodInjection(RootBeanDefinition, String, BeanFactory)}
                                     * {@link CglibSubclassingInstantiationStrategy#instantiateWithMethodInjection(RootBeanDefinition, String, BeanFactory, Constructor, Object...)}
                                     *  `return new CglibSubclassCreator(bd, owner).instantiate(ctor, args);`
                                     *  {@link CglibSubclassingInstantiationStrategy.CglibSubclassCreator#instantiate(Constructor, Object...)}
                                     *      {@link CglibSubclassingInstantiationStrategy.CglibSubclassCreator#createEnhancedSubclass(RootBeanDefinition)}
                                     *
                                     *      instance = BeanUtils.instantiateClass(subclass);
                                     *      instance = enhancedSubclassConstructor.newInstance(args);
                                     * */
                                    mbd.getMethodOverrides().addOverride(override);
                                } catch (NoSuchBeanDefinitionException ex) {
                                    throw new BeanCreationException(beanName,
                                            "Cannot apply @Lookup to beans without corresponding bean definition");
                                }
                            }
                        });
                        // 递归解析父类的方法
                        targetClass = targetClass.getSuperclass();
                    }
                    while (targetClass != null && targetClass != Object.class);

                } catch (IllegalStateException ex) {
                    throw new BeanCreationException(beanName, "Lookup method resolution failed", ex);
                }
            }
            // 记录这个beanName 已经处理过了
            this.lookupMethodsChecked.add(beanName);
        }

        // Quick check on the concurrent map first, with minimal locking.
        Constructor<?>[] candidateConstructors = this.candidateConstructorsCache.get(beanClass);
        if (candidateConstructors == null) {
            // Fully synchronized resolution now...
            synchronized (this.candidateConstructorsCache) {
                candidateConstructors = this.candidateConstructorsCache.get(beanClass);
                if (candidateConstructors == null) {
                    Constructor<?>[] rawCandidates;
                    try {
                        // 拿到构造器
                        rawCandidates = beanClass.getDeclaredConstructors();
                    } catch (Throwable ex) {
                        throw new BeanCreationException(beanName,
                                "Resolution of declared constructors on bean Class [" + beanClass.getName() +
                                        "] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
                    }
                    // 记录无参构造器和有@Autowired注解的构造器
                    List<Constructor<?>> candidates = new ArrayList<>(rawCandidates.length);
                    // 记录  @Autowired(required = true) 的构造器
                    Constructor<?> requiredConstructor = null;
                    // 记录 无参构造器
                    Constructor<?> defaultConstructor = null;
                    // 这个是解析 kotlin 的，java是没有的
                    Constructor<?> primaryConstructor = BeanUtils.findPrimaryConstructor(beanClass);
                    int nonSyntheticConstructors = 0;
                    for (Constructor<?> candidate : rawCandidates) {
                        /**
                         * 不是合成的,由编译器生成的class文件 才是合成的，
                         * 反正我们自己写的类 都不是合成的
                         * */
                        if (!candidate.isSynthetic()) {
                            nonSyntheticConstructors++;
                        } else if (primaryConstructor != null) {
                            continue;
                        }

                        /**
                         * 查找是否有 @Autowired、@Value、@javax.inject.Inject
                         * 找到就返回 注解信息
                         * */
                        MergedAnnotation<?> ann = findAutowiredAnnotation(candidate);
                        if (ann == null) {
                            /**
                             * beanClass 是cglib创建的类，那就返回实际的被代理对象的类型，
                             * 否则返回的还是 beanClass 咯
                             * */
                            Class<?> userClass = ClassUtils.getUserClass(beanClass);
                            // 不等于 说明 beanClass 是 cglib创建的类
                            if (userClass != beanClass) {
                                try {
                                    // 拿到被代理对象的构造器
                                    Constructor<?> superCtor =
                                            userClass.getDeclaredConstructor(candidate.getParameterTypes());
                                    //  查到注解
                                    ann = findAutowiredAnnotation(superCtor);
                                } catch (NoSuchMethodException ex) {
                                    // Simply proceed, no equivalent superclass constructor found...
                                }
                            }
                        }
                        // 构造器上有 @Autowired、@Value、@javax.inject.Inject
                        if (ann != null) {
                            /**
                             * 这里就是限制  @Autowired 构造器的使用
                             *
                             * 如果有 @Autowired(required = true)，那么只允许有一个 @Autowired 标注的构造器
                             * 只有 @Autowired(required = false)，才允许有多个
                             * */
                            if (requiredConstructor != null) {
                                throw new BeanCreationException(beanName,
                                        "Invalid autowire-marked constructor: " + candidate +
                                                ". Found constructor with 'required' Autowired annotation already: " +
                                                requiredConstructor);
                            }
                            /**
                             * @Autowired(required = true) 才是 required
                             * 或者是@Value，但是@Value不支持标注在构造器上 所以根本就不可能是@Value
                             * */
                            boolean required = determineRequiredStatus(ann);
                            if (required) {
                                if (!candidates.isEmpty()) {
                                    throw new BeanCreationException(beanName,
                                            "Invalid autowire-marked constructors: " + candidates +
                                                    ". Found constructor with 'required' Autowired annotation: " +
                                                    candidate);
                                }
                                // requiredConstructor
                                requiredConstructor = candidate;
                            }
                            // 添加到候选构造器集合中
                            candidates.add(candidate);
                        } else if (candidate.getParameterCount() == 0) {
                            // 记录无参构造器
                            defaultConstructor = candidate;
                        }
                    }

                    // 有使用@Autowired标注的构造器
                    if (!candidates.isEmpty()) {
                        /**
                         * 没有 required构造器 且有 无参构造器，才会将默认构造器 添加到候选列表中
                         *
                         * 也就是说如果有@Autowired修饰的构造器，那就不会使用无参构造器了，除非你在无参构造器上标注了@Autowired
                         * */
                        // Add default constructor to list of optional constructors, as fallback.
                        if (requiredConstructor == null) {
                            if (defaultConstructor != null) {
                                // 将默认构造器 添加到候选列表中
                                candidates.add(defaultConstructor);
                            } else if (candidates.size() == 1 && logger.isInfoEnabled()) {
                                logger.info("Inconsistent constructor declaration on bean with name '" + beanName +
                                        "': single autowire-marked constructor flagged as optional - " +
                                        "this constructor is effectively required since there is no " +
                                        "default constructor to fall back to: " + candidates.get(0));
                            }
                        }
                        // 设置为最终的候选构造器
                        candidateConstructors = candidates.toArray(new Constructor<?>[0]);
                    } else if (rawCandidates.length == 1 && rawCandidates[0].getParameterCount() > 0) {
                        /**
                         * 虽然没有@Autowired标注的构造器，但是只有一个
                         * */
                        candidateConstructors = new Constructor<?>[]{rawCandidates[0]};
                    } else if (nonSyntheticConstructors == 2 && primaryConstructor != null &&
                            defaultConstructor != null && !primaryConstructor.equals(defaultConstructor)) {
                        candidateConstructors = new Constructor<?>[]{primaryConstructor, defaultConstructor};
                    } else if (nonSyntheticConstructors == 1 && primaryConstructor != null) {
                        candidateConstructors = new Constructor<?>[]{primaryConstructor};
                    } else {
                        /**
                         * 啥都没得。
                         *
                         * 就是有多个构造器，且都没有使用@Autowired标注
                         * */
                        candidateConstructors = new Constructor<?>[0];
                    }
                    // 缓存最终的 候选构造器
                    this.candidateConstructorsCache.put(beanClass, candidateConstructors);
                }
            }
        }
        // 有候选的构造器就返回，否则就返回null
        return (candidateConstructors.length > 0 ? candidateConstructors : null);
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
        /**
         * 拿到要注入member信息，有缓存机制。
         * 在 {@link AutowiredAnnotationBeanPostProcessor#postProcessMergedBeanDefinition(RootBeanDefinition, Class, String)} 就解析过了。
         * */
        InjectionMetadata metadata = findAutowiringMetadata(beanName, bean.getClass(), pvs);
        try {
            /**
             * 会根据 InjectionMetadata 需要注入的信息，
             * 往 PropertyValues 中设置
             * */
            metadata.inject(bean, beanName, pvs);
        } catch (BeanCreationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new BeanCreationException(beanName, "Injection of autowired dependencies failed", ex);
        }
        return pvs;
    }

    @Deprecated
    @Override
    public PropertyValues postProcessPropertyValues(
            PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) {

        return postProcessProperties(pvs, bean, beanName);
    }

    /**
     * 'Native' processing method for direct calls with an arbitrary target instance,
     * resolving all of its fields and methods which are annotated with one of the
     * configured 'autowired' annotation types.
     * @param bean the target instance to process
     * @throws BeanCreationException if autowiring failed
     * @see #setAutowiredAnnotationTypes(Set)
     */
    public void processInjection(Object bean) throws BeanCreationException {
        Class<?> clazz = bean.getClass();
        InjectionMetadata metadata = findAutowiringMetadata(clazz.getName(), clazz, null);
        try {
            metadata.inject(bean, null, null);
        } catch (BeanCreationException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new BeanCreationException(
                    "Injection of autowired dependencies failed for class [" + clazz + "]", ex);
        }
    }


    /**
     * @param beanName beanName
     * @param clazz bean的class实例
     * @param pvs 这个是最终要填充bean的属性对象
     * @return
     */
    private InjectionMetadata findAutowiringMetadata(String beanName, Class<?> clazz, @Nullable PropertyValues pvs) {
        // Fall back to class name as cache key, for backwards compatibility with custom callers.
        String cacheKey = (StringUtils.hasLength(beanName) ? beanName : clazz.getName());
        // Quick check on the concurrent map first, with minimal locking.
        InjectionMetadata metadata = this.injectionMetadataCache.get(cacheKey);
        /**
         * metadata 是空 或者 metadata的属性{@link InjectionMetadata#targetClass} != clazz
         * 就需要刷新
         * */
        if (InjectionMetadata.needsRefresh(metadata, clazz)) {
            synchronized (this.injectionMetadataCache) {
                metadata = this.injectionMetadataCache.get(cacheKey);
                if (InjectionMetadata.needsRefresh(metadata, clazz)) {
                    if (metadata != null) {
                        // 从 pvs 里面清空 metadata 的数据
                        metadata.clear(pvs);
                    }
                    /**
                     * 构建 InjectionMetadata 对象。
                     * 主要是解析clazz及其所有父类，拿到其中标注了 @Autowired、@Value 的 方法和字段
                     *  字段会包装成`new AutowiredFieldElement(field, required);`
                     *  方法会包装成`new AutowiredMethodElement(method, required, pd);`
                     * */
                    metadata = buildAutowiringMetadata(clazz);
                    // 存入缓存
                    this.injectionMetadataCache.put(cacheKey, metadata);
                }
            }
        }
        return metadata;
    }

    /**
     * 解析clazz及其所有父类，拿到其中标注了 @Autowired、@Value 的 方法和字段构造成InjectionMetadata对象
     * @param clazz
     * @return
     */
    private InjectionMetadata buildAutowiringMetadata(final Class<?> clazz) {
        /**
         * 不是后候选的类，就返回空对象
         *
         * 只要clazz不是java包下的类，不是Ordered类 就是 候选类
         * */
        if (!AnnotationUtils.isCandidateClass(clazz, this.autowiredAnnotationTypes)) {
            return InjectionMetadata.EMPTY;
        }

        List<InjectionMetadata.InjectedElement> elements = new ArrayList<>();
        Class<?> targetClass = clazz;

        /**
         * 先处理clazz，再循环 clazz 所有的父类
         *
         * 处理class里面所有的Field、Method
         * */
        do {
            final List<InjectionMetadata.InjectedElement> currElements = new ArrayList<>();

            // 遍历当前类声明的字段
            ReflectionUtils.doWithLocalFields(targetClass, field -> {
                // 找到 @Autowired、@Value、@javax.inject.Inject 其中一个，就返回，否则返回null
                MergedAnnotation<?> ann = findAutowiredAnnotation(field);
                if (ann != null) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Autowired annotation is not supported on static fields: " + field);
                        }
                        return;
                    }
                    /**
                     * 返回注解的 `required` 属性的值。如果没有就返回true（比如@Value）
                     * */
                    boolean required = determineRequiredStatus(ann);
                    // 构造成 `AutowiredFieldElement` 对象
                    currElements.add(new AutowiredFieldElement(field, required));
                }
            });
            // 遍历当前类声明的方法
            ReflectionUtils.doWithLocalMethods(targetClass, method -> {
                Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
                // 不是桥接方法，桥接方法指的是jvm生成的方法。反正我们自己定义的方法不是
                if (!BridgeMethodResolver.isVisibilityBridgeMethodPair(method, bridgedMethod)) {
                    return;
                }
                // 找到 @Autowired、@Value、@javax.inject.Inject 其中一个，就返回，否则返回null
                MergedAnnotation<?> ann = findAutowiredAnnotation(bridgedMethod);
                if (ann != null && method.equals(ClassUtils.getMostSpecificMethod(method, clazz))) {
                    // 是静态的，不记录
                    if (Modifier.isStatic(method.getModifiers())) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Autowired annotation is not supported on static methods: " + method);
                        }
                        return;
                    }
                    // 方法没有参数，不记录
                    if (method.getParameterCount() == 0) {
                        if (logger.isInfoEnabled()) {
                            logger.info("Autowired annotation should only be used on methods with parameters: " +
                                    method);
                        }
                    }
                    // 不是静态方法、方法参数列表个数大于0的才记录
                    // 返回注解的 `required` 属性的值。如果没有就返回true（比如@Value）
                    boolean required = determineRequiredStatus(ann);
                    PropertyDescriptor pd = BeanUtils.findPropertyForMethod(bridgedMethod, clazz);
                    // 装饰成 AutowiredMethodElement 对象
                    currElements.add(new AutowiredMethodElement(method, required, pd));
                }
            });

            // 存起来
            elements.addAll(0, currElements);
            targetClass = targetClass.getSuperclass();
        }
        while (targetClass != null && targetClass != Object.class);

        return InjectionMetadata.forElements(elements, clazz);
    }

    /**
     * 找到 @Autowired、@Value、@javax.inject.Inject 其中一个，就返回，否则返回null
     * @param ao
     * @return
     */
    @Nullable
    private MergedAnnotation<?> findAutowiredAnnotation(AccessibleObject ao) {
        MergedAnnotations annotations = MergedAnnotations.from(ao);
        for (Class<? extends Annotation> type : this.autowiredAnnotationTypes) {
            MergedAnnotation<?> annotation = annotations.get(type);
            if (annotation.isPresent()) {
                return annotation;
            }
        }
        return null;
    }

    /**
     * 注解有`required`属性，就判断是不是true。比如@Autowired
     * 直接没有`required`属性，就默认就是true。比如@Value
     * Determine if the annotated field or method requires its dependency.
     * <p>A 'required' dependency means that autowiring should fail when no beans
     * are found. Otherwise, the autowiring process will simply bypass the field
     * or method when no beans are found.
     * @param ann the Autowired annotation
     * @return whether the annotation indicates that a dependency is required
     */
    @SuppressWarnings({"deprecation", "cast"})
    protected boolean determineRequiredStatus(MergedAnnotation<?> ann) {
        // The following (AnnotationAttributes) cast is required on JDK 9+.
        return determineRequiredStatus((AnnotationAttributes)
                ann.asMap(mergedAnnotation -> new AnnotationAttributes(mergedAnnotation.getType())));
    }

    /**
     * Determine if the annotated field or method requires its dependency.
     * <p>A 'required' dependency means that autowiring should fail when no beans
     * are found. Otherwise, the autowiring process will simply bypass the field
     * or method when no beans are found.
     * @param ann the Autowired annotation
     * @return whether the annotation indicates that a dependency is required
     * @deprecated since 5.2, in favor of {@link #determineRequiredStatus(MergedAnnotation)}
     */
    @Deprecated
    protected boolean determineRequiredStatus(AnnotationAttributes ann) {
        return (!ann.containsKey(this.requiredParameterName) ||
                this.requiredParameterValue == ann.getBoolean(this.requiredParameterName));
    }

    /**
     * Obtain all beans of the given type as autowire candidates.
     * @param type the type of the bean
     * @return the target beans, or an empty Collection if no bean of this type is found
     * @throws BeansException if bean retrieval failed
     */
    protected <T> Map<String, T> findAutowireCandidates(Class<T> type) throws BeansException {
        if (this.beanFactory == null) {
            throw new IllegalStateException("No BeanFactory configured - " +
                    "override the getBeanOfType method or specify the 'beanFactory' property");
        }
        return BeanFactoryUtils.beansOfTypeIncludingAncestors(this.beanFactory, type);
    }

    /**
     * Register the specified bean as dependent on the autowired beans.
     */
    private void registerDependentBeans(@Nullable String beanName, Set<String> autowiredBeanNames) {
        if (beanName != null) {
            for (String autowiredBeanName : autowiredBeanNames) {
                if (this.beanFactory != null && this.beanFactory.containsBean(autowiredBeanName)) {
                    this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("Autowiring by type from bean name '" + beanName +
                            "' to bean named '" + autowiredBeanName + "'");
                }
            }
        }
    }

    /**
     * Resolve the specified cached method argument or field value.
     */
    @Nullable
    private Object resolvedCachedArgument(@Nullable String beanName, @Nullable Object cachedArgument) {
        if (cachedArgument instanceof DependencyDescriptor) {
            DependencyDescriptor descriptor = (DependencyDescriptor) cachedArgument;
            Assert.state(this.beanFactory != null, "No BeanFactory available");
            return this.beanFactory.resolveDependency(descriptor, beanName, null, null);
        } else {
            return cachedArgument;
        }
    }


    /**
     * Class representing injection information about an annotated field.
     */
    private class AutowiredFieldElement extends InjectionMetadata.InjectedElement {

        private final boolean required;

        private volatile boolean cached;

        /**
         * 缓存的是这个
         * new ShortcutDependencyDescriptor(desc, autowiredBeanName, field.getType());
         */
        @Nullable
        private volatile Object cachedFieldValue;

        public AutowiredFieldElement(Field field, boolean required) {
            super(field, null);
            this.required = required;
        }

        @Override
        protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
            Field field = (Field) this.member;
            Object value;
            if (this.cached) {
                try {
                    value = resolvedCachedArgument(beanName, this.cachedFieldValue);
                } catch (NoSuchBeanDefinitionException ex) {
                    // Unexpected removal of target bean for cached argument -> re-resolve
                    value = resolveFieldValue(field, bean, beanName);
                }
            } else {
                value = resolveFieldValue(field, bean, beanName);
            }
            if (value != null) {
                ReflectionUtils.makeAccessible(field);
                // 反射注入
                field.set(bean, value);
            }
        }

        /**
         *
         * @param field bean对象 要注入的Field
         * @param bean bean对象
         * @param beanName
         * @return
         */
        @Nullable
        private Object resolveFieldValue(Field field, Object bean, @Nullable String beanName) {
            // 装饰成 DependencyDescriptor
            DependencyDescriptor desc = new DependencyDescriptor(field, this.required);
            desc.setContainingClass(bean.getClass());
            Set<String> autowiredBeanNames = new LinkedHashSet<>(1);
            Assert.state(beanFactory != null, "No BeanFactory available");
            TypeConverter typeConverter = beanFactory.getTypeConverter();
            Object value;
            try {
                /**
                 * 使用 beanFactory 解析依赖
                 * */
                value = beanFactory.resolveDependency(desc, beanName, autowiredBeanNames, typeConverter);
            } catch (BeansException ex) {
                throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(field), ex);
            }
            synchronized (this) {
                // 没有缓存过
                if (!this.cached) {
                    Object cachedFieldValue = null;
                    // 只存在，或者是必须的
                    if (value != null || this.required) {
                        cachedFieldValue = desc;
                        /**
                         *  beanName就是bean对象
                         *  autowiredBeanNames就是bean对象的自动注入字段、方法 注入值的beanName
                         *  记录依赖关系
                         * */
                        registerDependentBeans(beanName, autowiredBeanNames);
                        if (autowiredBeanNames.size() == 1) {
                            String autowiredBeanName = autowiredBeanNames.iterator().next();
                            if (beanFactory.containsBean(autowiredBeanName) &&
                                    beanFactory.isTypeMatch(autowiredBeanName, field.getType())) {
                                /**
                                 * 捷径？？？
                                 *
                                 * 取过一次了 装饰成 ShortcutDependencyDescriptor 然后缓存起来
                                 * */
                                cachedFieldValue = new ShortcutDependencyDescriptor(
                                        desc, autowiredBeanName, field.getType());
                            }
                        }
                    }
                    this.cachedFieldValue = cachedFieldValue;
                    this.cached = true;
                }
            }
            return value;
        }
    }


    /**
     * Class representing injection information about an annotated method.
     */
    private class AutowiredMethodElement extends InjectionMetadata.InjectedElement {

        private final boolean required;

        private volatile boolean cached;

        @Nullable
        private volatile Object[] cachedMethodArguments;

        public AutowiredMethodElement(Method method, boolean required, @Nullable PropertyDescriptor pd) {
            super(method, pd);
            this.required = required;
        }

        @Override
        protected void inject(Object bean, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
            if (checkPropertySkipping(pvs)) {
                return;
            }
            Method method = (Method) this.member;
            Object[] arguments;
            if (this.cached) {
                try {
                    arguments = resolveCachedArguments(beanName);
                } catch (NoSuchBeanDefinitionException ex) {
                    // Unexpected removal of target bean for cached argument -> re-resolve
                    arguments = resolveMethodArguments(method, bean, beanName);
                }
            } else {
                // 拿到要注入的参数值
                arguments = resolveMethodArguments(method, bean, beanName);
            }
            if (arguments != null) {
                try {
                    // 反射执行方法
                    ReflectionUtils.makeAccessible(method);
                    method.invoke(bean, arguments);
                } catch (InvocationTargetException ex) {
                    throw ex.getTargetException();
                }
            }
        }

        @Nullable
        private Object[] resolveCachedArguments(@Nullable String beanName) {
            Object[] cachedMethodArguments = this.cachedMethodArguments;
            if (cachedMethodArguments == null) {
                return null;
            }
            Object[] arguments = new Object[cachedMethodArguments.length];
            for (int i = 0; i < arguments.length; i++) {
                arguments[i] = resolvedCachedArgument(beanName, cachedMethodArguments[i]);
            }
            return arguments;
        }

        @Nullable
        private Object[] resolveMethodArguments(Method method, Object bean, @Nullable String beanName) {
            int argumentCount = method.getParameterCount();
            Object[] arguments = new Object[argumentCount];
            DependencyDescriptor[] descriptors = new DependencyDescriptor[argumentCount];
            Set<String> autowiredBeans = new LinkedHashSet<>(argumentCount);
            Assert.state(beanFactory != null, "No BeanFactory available");
            TypeConverter typeConverter = beanFactory.getTypeConverter();
            // 遍历参数列表
            for (int i = 0; i < arguments.length; i++) {
                // 就是一个个解析 方法的参数
                MethodParameter methodParam = new MethodParameter(method, i);
                DependencyDescriptor currDesc = new DependencyDescriptor(methodParam, this.required);
                currDesc.setContainingClass(bean.getClass());
                descriptors[i] = currDesc;
                try {
                    // 拿到解析的值
                    Object arg = beanFactory.resolveDependency(currDesc, beanName, autowiredBeans, typeConverter);
                    if (arg == null && !this.required) {
                        arguments = null;
                        break;
                    }
                    arguments[i] = arg;
                } catch (BeansException ex) {
                    throw new UnsatisfiedDependencyException(null, beanName, new InjectionPoint(methodParam), ex);
                }
            }
            synchronized (this) {
                if (!this.cached) {
                    if (arguments != null) {
                        DependencyDescriptor[] cachedMethodArguments = Arrays.copyOf(descriptors, arguments.length);
                        /**
                         * 记录依赖关系
                         * */
                        registerDependentBeans(beanName, autowiredBeans);
                        if (autowiredBeans.size() == argumentCount) {
                            Iterator<String> it = autowiredBeans.iterator();
                            Class<?>[] paramTypes = method.getParameterTypes();
                            for (int i = 0; i < paramTypes.length; i++) {
                                String autowiredBeanName = it.next();
                                if (beanFactory.containsBean(autowiredBeanName) &&
                                        beanFactory.isTypeMatch(autowiredBeanName, paramTypes[i])) {
                                    cachedMethodArguments[i] = new ShortcutDependencyDescriptor(
                                            descriptors[i], autowiredBeanName, paramTypes[i]);
                                }
                            }
                        }
                        this.cachedMethodArguments = cachedMethodArguments;
                    } else {
                        this.cachedMethodArguments = null;
                    }
                    this.cached = true;
                }
            }
            return arguments;
        }
    }


    /**
     * DependencyDescriptor variant with a pre-resolved target bean name.
     */
    @SuppressWarnings("serial")
    private static class ShortcutDependencyDescriptor extends DependencyDescriptor {

        private final String shortcut;

        private final Class<?> requiredType;

        public ShortcutDependencyDescriptor(DependencyDescriptor original, String shortcut, Class<?> requiredType) {
            super(original);
            this.shortcut = shortcut;
            this.requiredType = requiredType;
        }

        @Override
        public Object resolveShortcut(BeanFactory beanFactory) {
            return beanFactory.getBean(this.shortcut, this.requiredType);
        }
    }

}
