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

package org.springframework.beans.factory.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.config.*;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.OrderComparator;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.core.env.Environment;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;
import org.springframework.util.*;

import javax.inject.Provider;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Spring's default implementation of the {@link ConfigurableListableBeanFactory}
 * and {@link BeanDefinitionRegistry} interfaces: a full-fledged bean factory
 * based on bean definition metadata, extensible through post-processors.
 *
 * <p>Typical usage is registering all bean definitions first (possibly read
 * from a bean definition file), before accessing beans. Bean lookup by name
 * is therefore an inexpensive operation in a local bean definition table,
 * operating on pre-resolved bean definition metadata objects.
 *
 * <p>Note that readers for specific bean definition formats are typically
 * implemented separately rather than as bean factory subclasses: see for example
 * {@link org.springframework.beans.factory.xml.XmlBeanDefinitionReader}.
 *
 * <p>For an alternative implementation of the
 * {@link org.springframework.beans.factory.ListableBeanFactory} interface,
 * have a look at {@link StaticListableBeanFactory}, which manages existing
 * bean instances rather than creating new ones based on bean definitions.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @see #registerBeanDefinition
 * @see #addBeanPostProcessor
 * @see #getBean
 * @see #resolveDependency
 * @since 16 April 2001
 */
@SuppressWarnings("serial")
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {

    @Nullable
    private static Class<?> javaxInjectProviderClass;

    static {
        try {
            javaxInjectProviderClass = ClassUtils.forName("javax.inject.Provider", DefaultListableBeanFactory.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // JSR-330 API not available - Provider interface simply not supported then.
            javaxInjectProviderClass = null;
        }
    }


    /**
     * Map from serialized id to factory instance.
     */
    private static final Map<String, Reference<DefaultListableBeanFactory>> serializableFactories = new ConcurrentHashMap<>(8);

    /**
     * Optional id for this factory, for serialization purposes.
     */
    @Nullable
    private String serializationId;

    /**
     * Whether to allow re-registration of a different definition with the same name.
     */
    private boolean allowBeanDefinitionOverriding = true;

    /**
     * Whether to allow eager class loading even for lazy-init beans.
     */
    private boolean allowEagerClassLoading = true;

    /**
     * 在实例化IOC容器的时候设置的 {@link org.springframework.context.annotation.AnnotationConfigUtils#registerAnnotationConfigProcessors(BeanDefinitionRegistry, Object)}
     * Optional OrderComparator for dependency Lists and arrays.
     */
    @Nullable
    private Comparator<Object> dependencyComparator;

    /**
     * AnnotationConfigApplicationContext 类型的IOC容器，会在实例化的是设置这个属性的值
     * {@link org.springframework.context.annotation.AnnotatedBeanDefinitionReader#AnnotatedBeanDefinitionReader(BeanDefinitionRegistry, Environment)}
     *      {@link org.springframework.context.annotation.AnnotationConfigUtils#registerAnnotationConfigProcessors(BeanDefinitionRegistry, Object)}
     *          设置这个类型的{@link ContextAnnotationAutowireCandidateResolver}
     * Resolver to use for checking if a bean definition is an autowire candidate.
     */
    private AutowireCandidateResolver autowireCandidateResolver = SimpleAutowireCandidateResolver.INSTANCE;

    /**
     * Map from dependency type to corresponding autowired value.
     */
    private final Map<Class<?>, Object> resolvableDependencies = new ConcurrentHashMap<>(16);

    /**
     * Map of bean definition objects, keyed by bean name.
     */
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);

    /**
     * Map from bean name to merged BeanDefinitionHolder.
     */
    private final Map<String, BeanDefinitionHolder> mergedBeanDefinitionHolders = new ConcurrentHashMap<>(256);

    /**
     * 这个是用来缓存依赖注入信息的，提高相同类型依赖的查找时间。
     * key: 依赖类型
     * value: 单例和非单例bean
     * {@link DefaultListableBeanFactory#getBeanNamesForType(Class, boolean, boolean)}
     * Map of singleton and non-singleton bean names, keyed by dependency type.
     */
    private final Map<Class<?>, String[]> allBeanNamesByType = new ConcurrentHashMap<>(64);

    /**
     * 这个是用来缓存依赖注入信息的，提高相同类型依赖的查找时间。
     * key: 依赖类型
     * value: 单例bean
     * {@link DefaultListableBeanFactory#getBeanNamesForType(Class, boolean, boolean)}
     * Map of singleton-only bean names, keyed by dependency type.
     */
    private final Map<Class<?>, String[]> singletonBeanNamesByType = new ConcurrentHashMap<>(64);

    /**
     * List of bean definition names, in registration order.
     */
    private volatile List<String> beanDefinitionNames = new ArrayList<>(256);

    /**
     * 存储的是 直接添加的单例bean的信息
     *
     * {@link DefaultListableBeanFactory#registerSingleton(String, Object)}
     * List of names of manually registered singletons, in registration order.
     */
    private volatile Set<String> manualSingletonNames = new LinkedHashSet<>(16);

    /**
     * Cached array of bean definition names in case of frozen configuration.
     */
    @Nullable
    private volatile String[] frozenBeanDefinitionNames;

    /**
     * Whether bean definition metadata may be cached for all beans.
     */
    private volatile boolean configurationFrozen;


    /**
     * Create a new DefaultListableBeanFactory.
     */
    public DefaultListableBeanFactory() {
        super();
    }

    /**
     * Create a new DefaultListableBeanFactory with the given parent.
     *
     * @param parentBeanFactory the parent BeanFactory
     */
    public DefaultListableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
        super(parentBeanFactory);
    }


    /**
     * Specify an id for serialization purposes, allowing this BeanFactory to be
     * deserialized from this id back into the BeanFactory object, if needed.
     */
    public void setSerializationId(@Nullable String serializationId) {
        if (serializationId != null) {
            serializableFactories.put(serializationId, new WeakReference<>(this));
        } else if (this.serializationId != null) {
            serializableFactories.remove(this.serializationId);
        }
        this.serializationId = serializationId;
    }

    /**
     * Return an id for serialization purposes, if specified, allowing this BeanFactory
     * to be deserialized from this id back into the BeanFactory object, if needed.
     *
     * @since 4.1.2
     */
    @Nullable
    public String getSerializationId() {
        return this.serializationId;
    }

    /**
     * Set whether it should be allowed to override bean definitions by registering
     * a different definition with the same name, automatically replacing the former.
     * If not, an exception will be thrown. This also applies to overriding aliases.
     * <p>Default is "true".
     *
     * @see #registerBeanDefinition
     */
    public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
        this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
    }

    /**
     * Return whether it should be allowed to override bean definitions by registering
     * a different definition with the same name, automatically replacing the former.
     *
     * @since 4.1.2
     */
    public boolean isAllowBeanDefinitionOverriding() {
        return this.allowBeanDefinitionOverriding;
    }

    /**
     * Set whether the factory is allowed to eagerly load bean classes
     * even for bean definitions that are marked as "lazy-init".
     * <p>Default is "true". Turn this flag off to suppress class loading
     * for lazy-init beans unless such a bean is explicitly requested.
     * In particular, by-type lookups will then simply ignore bean definitions
     * without resolved class name, instead of loading the bean classes on
     * demand just to perform a type check.
     *
     * @see AbstractBeanDefinition#setLazyInit
     */
    public void setAllowEagerClassLoading(boolean allowEagerClassLoading) {
        this.allowEagerClassLoading = allowEagerClassLoading;
    }

    /**
     * Return whether the factory is allowed to eagerly load bean classes
     * even for bean definitions that are marked as "lazy-init".
     *
     * @since 4.1.2
     */
    public boolean isAllowEagerClassLoading() {
        return this.allowEagerClassLoading;
    }

    /**
     * Set a {@link java.util.Comparator} for dependency Lists and arrays.
     *
     * @see org.springframework.core.OrderComparator
     * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
     * @since 4.0
     */
    public void setDependencyComparator(@Nullable Comparator<Object> dependencyComparator) {
        this.dependencyComparator = dependencyComparator;
    }

    /**
     * Return the dependency comparator for this BeanFactory (may be {@code null}.
     *
     * @since 4.0
     */
    @Nullable
    public Comparator<Object> getDependencyComparator() {
        return this.dependencyComparator;
    }

    /**
     * Set a custom autowire candidate resolver for this BeanFactory to use
     * when deciding whether a bean definition should be considered as a
     * candidate for autowiring.
     */
    public void setAutowireCandidateResolver(AutowireCandidateResolver autowireCandidateResolver) {
        Assert.notNull(autowireCandidateResolver, "AutowireCandidateResolver must not be null");
        if (autowireCandidateResolver instanceof BeanFactoryAware) {
            if (System.getSecurityManager() != null) {
                AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                    ((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
                    return null;
                }, getAccessControlContext());
            } else {
                ((BeanFactoryAware) autowireCandidateResolver).setBeanFactory(this);
            }
        }
        this.autowireCandidateResolver = autowireCandidateResolver;
    }

    /**
     * Return the autowire candidate resolver for this BeanFactory (never {@code null}).
     */
    public AutowireCandidateResolver getAutowireCandidateResolver() {
        return this.autowireCandidateResolver;
    }


    @Override
    public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
        super.copyConfigurationFrom(otherFactory);
        if (otherFactory instanceof DefaultListableBeanFactory) {
            DefaultListableBeanFactory otherListableFactory = (DefaultListableBeanFactory) otherFactory;
            this.allowBeanDefinitionOverriding = otherListableFactory.allowBeanDefinitionOverriding;
            this.allowEagerClassLoading = otherListableFactory.allowEagerClassLoading;
            this.dependencyComparator = otherListableFactory.dependencyComparator;
            // A clone of the AutowireCandidateResolver since it is potentially BeanFactoryAware
            setAutowireCandidateResolver(otherListableFactory.getAutowireCandidateResolver()
                    .cloneIfNecessary());
            // Make resolvable dependencies (e.g. ResourceLoader) available here as well
            this.resolvableDependencies.putAll(otherListableFactory.resolvableDependencies);
        }
    }


    //---------------------------------------------------------------------
    // Implementation of remaining BeanFactory methods
    //---------------------------------------------------------------------

    @Override
    public <T> T getBean(Class<T> requiredType) throws BeansException {
        return getBean(requiredType, (Object[]) null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
        Assert.notNull(requiredType, "Required type must not be null");
        Object resolved = resolveBean(ResolvableType.forRawClass(requiredType), args, false);
        if (resolved == null) {
            throw new NoSuchBeanDefinitionException(requiredType);
        }
        return (T) resolved;
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        Assert.notNull(requiredType, "Required type must not be null");
        return getBeanProvider(ResolvableType.forRawClass(requiredType), true);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        return getBeanProvider(requiredType, true);
    }


    //---------------------------------------------------------------------
    // Implementation of ListableBeanFactory interface
    //---------------------------------------------------------------------

    @Override
    public boolean containsBeanDefinition(String beanName) {
        Assert.notNull(beanName, "Bean name must not be null");
        return this.beanDefinitionMap.containsKey(beanName);
    }

    @Override
    public int getBeanDefinitionCount() {
        return this.beanDefinitionMap.size();
    }

    @Override
    public String[] getBeanDefinitionNames() {
        String[] frozenNames = this.frozenBeanDefinitionNames;
        if (frozenNames != null) {
            return frozenNames.clone();
        } else {
            return StringUtils.toStringArray(this.beanDefinitionNames);
        }
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
        Assert.notNull(requiredType, "Required type must not be null");
        return getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit);
    }

    @Override
    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
        return new BeanObjectProvider<T>() {
            @Override
            public T getObject() throws BeansException {
                T resolved = resolveBean(requiredType, null, false);
                if (resolved == null) {
                    throw new NoSuchBeanDefinitionException(requiredType);
                }
                return resolved;
            }

            @Override
            public T getObject(Object... args) throws BeansException {
                T resolved = resolveBean(requiredType, args, false);
                if (resolved == null) {
                    throw new NoSuchBeanDefinitionException(requiredType);
                }
                return resolved;
            }

            @Override
            @Nullable
            public T getIfAvailable() throws BeansException {
                try {
                    return resolveBean(requiredType, null, false);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope
                    return null;
                }
            }

            @Override
            public void ifAvailable(Consumer<T> dependencyConsumer) throws BeansException {
                T dependency = getIfAvailable();
                if (dependency != null) {
                    try {
                        dependencyConsumer.accept(dependency);
                    } catch (ScopeNotActiveException ex) {
                        // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                    }
                }
            }

            @Override
            @Nullable
            public T getIfUnique() throws BeansException {
                try {
                    return resolveBean(requiredType, null, true);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope
                    return null;
                }
            }

            @Override
            public void ifUnique(Consumer<T> dependencyConsumer) throws BeansException {
                T dependency = getIfUnique();
                if (dependency != null) {
                    try {
                        dependencyConsumer.accept(dependency);
                    } catch (ScopeNotActiveException ex) {
                        // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                    }
                }
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> stream() {
                return Arrays.stream(getBeanNamesForTypedStream(requiredType, allowEagerInit))
                        .map(name -> (T) getBean(name))
                        .filter(bean -> !(bean instanceof NullBean));
            }

            @SuppressWarnings("unchecked")
            @Override
            public Stream<T> orderedStream() {
                String[] beanNames = getBeanNamesForTypedStream(requiredType, allowEagerInit);
                if (beanNames.length == 0) {
                    return Stream.empty();
                }
                Map<String, T> matchingBeans = CollectionUtils.newLinkedHashMap(beanNames.length);
                for (String beanName : beanNames) {
                    Object beanInstance = getBean(beanName);
                    if (!(beanInstance instanceof NullBean)) {
                        matchingBeans.put(beanName, (T) beanInstance);
                    }
                }
                Stream<T> stream = matchingBeans.values()
                        .stream();
                return stream.sorted(adaptOrderComparator(matchingBeans));
            }
        };
    }

    @Nullable
    private <T> T resolveBean(ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) {
        NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args, nonUniqueAsNull);
        if (namedBean != null) {
            return namedBean.getBeanInstance();
        }
        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory) {
            return ((DefaultListableBeanFactory) parent).resolveBean(requiredType, args, nonUniqueAsNull);
        } else if (parent != null) {
            ObjectProvider<T> parentProvider = parent.getBeanProvider(requiredType);
            if (args != null) {
                return parentProvider.getObject(args);
            } else {
                return (nonUniqueAsNull ? parentProvider.getIfUnique() : parentProvider.getIfAvailable());
            }
        }
        return null;
    }

    private String[] getBeanNamesForTypedStream(ResolvableType requiredType, boolean allowEagerInit) {
        return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, true, allowEagerInit);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type) {
        return getBeanNamesForType(type, true, true);
    }

    @Override
    public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        Class<?> resolved = type.resolve();
        if (resolved != null && !type.hasGenerics()) {
            return getBeanNamesForType(resolved, includeNonSingletons, allowEagerInit);
        } else {
            return doGetBeanNamesForType(type, includeNonSingletons, allowEagerInit);
        }
    }

    @Override
    public String[] getBeanNamesForType(@Nullable Class<?> type) {
        return getBeanNamesForType(type, true, true);
    }

    @Override
    public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        /**
         * 在完成 BeanFactory 阶段会设置 isConfigurationFrozen 该属性为 true
         * {@link org.springframework.context.support.AbstractApplicationContext#finishBeanFactoryInitialization(ConfigurableListableBeanFactory)}
         *
         * allowEagerInit 就是这个 {@link DependencyDescriptor#eager} ,是否初始化FactoryBean使用 {@link FactoryBean#getObjectType()} 来进行类型匹配
         * */
        if (!isConfigurationFrozen() || type == null || !allowEagerInit) {
            //  allowEagerInit 为 false 会发生啥呀
            return doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, allowEagerInit);
        }
        /**
         * 拿到不同分类的缓存。
         * allBeanNamesByType：缓存的是单例bean和非单例bean的信息
         * singletonBeanNamesByType：缓存的是单例bean的信息
         * */
        Map<Class<?>, String[]> cache = (includeNonSingletons ? this.allBeanNamesByType : this.singletonBeanNamesByType);
        // 缓存中有，就返回
        String[] resolvedBeanNames = cache.get(type);
        if (resolvedBeanNames != null) {
            return resolvedBeanNames;
        }
        /**
         * 看到 do 说明要开始做事情的了
         * */
        resolvedBeanNames = doGetBeanNamesForType(ResolvableType.forRawClass(type), includeNonSingletons, true);
        // 是安全缓存。就是看看这个类型 是不是 `getBeanClassLoader()` 可以加载的
        if (ClassUtils.isCacheSafe(type, getBeanClassLoader())) {
            // 存到缓存中
            cache.put(type, resolvedBeanNames);
        }
        return resolvedBeanNames;
    }

    /**
     * 根据类型找到beanName集合
     * @param type  查到的类型
     * @param includeNonSingletons 包含非单例bean
     * @param allowEagerInit 这个是干啥的
     * @return 符合类型的bean的名字，String[]
     */
    private String[] doGetBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
        List<String> result = new ArrayList<>();

        /**
         * 先从 beanDefinitionNames 中找
         * */
        // Check all bean definitions.
        for (String beanName : this.beanDefinitionNames) {
            // beanName 不是别名
            // Only consider bean as eligible if the bean name is not defined as alias for some other bean.
            if (!isAlias(beanName)) {
                try {
                    RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                    // 不是抽象的 且 ( 允许孵化初始化 || 设置了beanClass || 不是懒加载 || ...)
                    // Only check bean definition if it is complete.
                    if (!mbd.isAbstract() && (allowEagerInit
                            || (mbd.hasBeanClass() || !mbd.isLazyInit() || isAllowEagerClassLoading())
                            && !requiresEagerInitForType(mbd.getFactoryBeanName()))) {
                        // 是不是 FactoryBean(只是推断而已，不会创建出bean的)
                        boolean isFactoryBean = isFactoryBean(beanName, mbd);
                        BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
                        boolean matchFound = false;
                        // 允许 FactoryBean的初始化
                        boolean allowFactoryBeanInit = (allowEagerInit || containsSingleton(beanName));
                        boolean isNonLazyDecorated = (dbd != null && !mbd.isLazyInit());
                        // 不是 FactoryBean
                        if (!isFactoryBean) {
                            if (includeNonSingletons || isSingleton(beanName, mbd, dbd)) {
                                /**
                                 * 就是看看容器中有没有类型匹配的bean
                                 * */
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                            }
                        } else {
                            /**
                             * isSingleton 可能会触发FactoryBean的创建，当 beanName是 {@link FactoryBean#getObject()} 这个的时候，
                             * 需要创建出 FactoryBean 才能知道是不是单例的 {@link FactoryBean#isSingleton()}
                             * */
                            if (includeNonSingletons || isNonLazyDecorated || (allowFactoryBeanInit
                                    && isSingleton(beanName, mbd, dbd))) {
                                /**
                                 * 匹配的是这个类型{@link FactoryBean#getObject()}
                                 * */
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                            }
                            if (!matchFound) {
                                // 匹配 FactoryBean 实例本身
                                // In case of FactoryBean, try to match FactoryBean instance itself next.
                                beanName = FACTORY_BEAN_PREFIX + beanName;
                                matchFound = isTypeMatch(beanName, type, allowFactoryBeanInit);
                            }
                        }
                        if (matchFound) {
                            // 找到类型匹配的bean，就记录起来
                            result.add(beanName);
                        }
                    }
                } catch (CannotLoadBeanClassException | BeanDefinitionStoreException ex) {
                    if (allowEagerInit) {
                        throw ex;
                    }
                    // Probably a placeholder: let's ignore it for type matching purposes.
                    LogMessage message = (ex instanceof CannotLoadBeanClassException ? LogMessage.format("Ignoring bean class loading failure for bean '%s'", beanName) : LogMessage.format("Ignoring unresolvable metadata in bean definition '%s'", beanName));
                    logger.trace(message, ex);
                    // Register exception, in case the bean was accidentally unresolvable.
                    onSuppressedException(ex);
                } catch (NoSuchBeanDefinitionException ex) {
                    // Bean definition got removed while we were iterating -> ignore.
                }
            }
        }

        /**
         * 再从 manualSingletonNames 中找
         * 这种是  {@link DefaultListableBeanFactory#registerSingleton(String, Object)} 这样子注册的，直接就放到单例池不会在BeanDefinitionMap中有记录
         * */
        // Check manually registered singletons too.
        for (String beanName : this.manualSingletonNames) {
            try {
                // In case of FactoryBean, match object created by FactoryBean.
                if (isFactoryBean(beanName)) {
                    if ((includeNonSingletons || isSingleton(beanName)) && isTypeMatch(beanName, type)) {
                        result.add(beanName);
                        // Match found for this bean: do not match FactoryBean itself anymore.
                        continue;
                    }
                    // In case of FactoryBean, try to match FactoryBean itself next.
                    beanName = FACTORY_BEAN_PREFIX + beanName;
                }
                // Match raw bean instance (might be raw FactoryBean).
                if (isTypeMatch(beanName, type)) {
                    result.add(beanName);
                }
            } catch (NoSuchBeanDefinitionException ex) {
                // Shouldn't happen - probably a result of circular reference resolution...
                logger.trace(LogMessage.format("Failed to check manually registered singleton with name '%s'", beanName), ex);
            }
        }

        return StringUtils.toStringArray(result);
    }

    private boolean isSingleton(String beanName, RootBeanDefinition mbd, @Nullable BeanDefinitionHolder dbd) {
        return (dbd != null ? mbd.isSingleton() : isSingleton(beanName));
    }

    /**
     * Check whether the specified bean would need to be eagerly initialized
     * in order to determine its type.
     *
     * @param factoryBeanName a factory-bean reference that the bean definition
     *                        defines a factory method for
     * @return whether eager initialization is necessary
     */
    private boolean requiresEagerInitForType(@Nullable String factoryBeanName) {
        return (factoryBeanName != null && isFactoryBean(factoryBeanName) && !containsSingleton(factoryBeanName));
    }

    @Override
    public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
        return getBeansOfType(type, true, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {

        String[] beanNames = getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
        Map<String, T> result = CollectionUtils.newLinkedHashMap(beanNames.length);
        for (String beanName : beanNames) {
            try {
                Object beanInstance = getBean(beanName);
                if (!(beanInstance instanceof NullBean)) {
                    result.put(beanName, (T) beanInstance);
                }
            } catch (BeanCreationException ex) {
                Throwable rootCause = ex.getMostSpecificCause();
                if (rootCause instanceof BeanCurrentlyInCreationException) {
                    BeanCreationException bce = (BeanCreationException) rootCause;
                    String exBeanName = bce.getBeanName();
                    if (exBeanName != null && isCurrentlyInCreation(exBeanName)) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("Ignoring match to currently created bean '" + exBeanName + "': "
                                    + ex.getMessage());
                        }
                        onSuppressedException(ex);
                        // Ignore: indicates a circular reference when autowiring constructors.
                        // We want to find matches other than the currently created bean itself.
                        continue;
                    }
                }
                throw ex;
            }
        }
        return result;
    }

    @Override
    public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        List<String> result = new ArrayList<>();
        for (String beanName : this.beanDefinitionNames) {
            BeanDefinition bd = this.beanDefinitionMap.get(beanName);
            if (bd != null && !bd.isAbstract() && findAnnotationOnBean(beanName, annotationType) != null) {
                result.add(beanName);
            }
        }
        for (String beanName : this.manualSingletonNames) {
            if (!result.contains(beanName) && findAnnotationOnBean(beanName, annotationType) != null) {
                result.add(beanName);
            }
        }
        return StringUtils.toStringArray(result);
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        String[] beanNames = getBeanNamesForAnnotation(annotationType);
        Map<String, Object> result = CollectionUtils.newLinkedHashMap(beanNames.length);
        for (String beanName : beanNames) {
            Object beanInstance = getBean(beanName);
            if (!(beanInstance instanceof NullBean)) {
                result.put(beanName, beanInstance);
            }
        }
        return result;
    }

    @Override
    @Nullable
    public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException {

        return findMergedAnnotationOnBean(beanName, annotationType).synthesize(MergedAnnotation::isPresent)
                .orElse(null);
    }

    private <A extends Annotation> MergedAnnotation<A> findMergedAnnotationOnBean(String beanName, Class<A> annotationType) {

        Class<?> beanType = getType(beanName);
        if (beanType != null) {
            MergedAnnotation<A> annotation = MergedAnnotations.from(beanType, SearchStrategy.TYPE_HIERARCHY)
                    .get(annotationType);
            if (annotation.isPresent()) {
                return annotation;
            }
        }
        if (containsBeanDefinition(beanName)) {
            RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            // Check raw bean class, e.g. in case of a proxy.
            if (bd.hasBeanClass()) {
                Class<?> beanClass = bd.getBeanClass();
                if (beanClass != beanType) {
                    MergedAnnotation<A> annotation = MergedAnnotations.from(beanClass, SearchStrategy.TYPE_HIERARCHY)
                            .get(annotationType);
                    if (annotation.isPresent()) {
                        return annotation;
                    }
                }
            }
            // Check annotations declared on factory method, if any.
            Method factoryMethod = bd.getResolvedFactoryMethod();
            if (factoryMethod != null) {
                MergedAnnotation<A> annotation = MergedAnnotations.from(factoryMethod, SearchStrategy.TYPE_HIERARCHY)
                        .get(annotationType);
                if (annotation.isPresent()) {
                    return annotation;
                }
            }
        }
        return MergedAnnotation.missing();
    }


    //---------------------------------------------------------------------
    // Implementation of ConfigurableListableBeanFactory interface
    //---------------------------------------------------------------------

    @Override
    public void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue) {
        Assert.notNull(dependencyType, "Dependency type must not be null");
        if (autowiredValue != null) {
            if (!(autowiredValue instanceof ObjectFactory || dependencyType.isInstance(autowiredValue))) {
                throw new IllegalArgumentException(
                        "Value [" + autowiredValue + "] does not implement specified dependency type ["
                                + dependencyType.getName() + "]");
            }
            this.resolvableDependencies.put(dependencyType, autowiredValue);
        }
    }

    @Override
    public boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor) throws NoSuchBeanDefinitionException {
        /**
         * 在这里注册的 {@link org.springframework.context.annotation.AnnotationConfigUtils#registerAnnotationConfigProcessors(BeanDefinitionRegistry, Object)}
         * 用的这个{@link ContextAnnotationAutowireCandidateResolver }
         * */
        return isAutowireCandidate(beanName, descriptor, getAutowireCandidateResolver());
    }

    /**
     * Determine whether the specified bean definition qualifies as an autowire candidate,
     * to be injected into other beans which declare a dependency of matching type.
     *
     * @param beanName   the name of the bean definition to check
     * @param descriptor the descriptor of the dependency to resolve
     * @param resolver   the AutowireCandidateResolver to use for the actual resolution algorithm
     * @return whether the bean should be considered as autowire candidate
     */
    protected boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor, AutowireCandidateResolver resolver) throws NoSuchBeanDefinitionException {

        String bdName = BeanFactoryUtils.transformedBeanName(beanName);
        // 先看看 BeanDefinitionMap中看看
        if (containsBeanDefinition(bdName)) {
            /**
             * 是自动注入候选者
             * */
            return isAutowireCandidate(beanName, getMergedLocalBeanDefinition(bdName), descriptor, resolver);
        } else if (containsSingleton(beanName)) {
            /**
             * 再看看单例池中看看。
             * 因为这种方式注册单例bean，是不会记录到BeanDefinitionMap中的{@link DefaultListableBeanFactory#registerSingleton(String, Object)}
             * */
            return isAutowireCandidate(beanName, new RootBeanDefinition(getType(beanName)), descriptor, resolver);
        }

        /**
         * 递归调父工厂
         * */
        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory) {
            // No bean definition found in this factory -> delegate to parent.
            return ((DefaultListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor, resolver);
        } else if (parent instanceof ConfigurableListableBeanFactory) {
            // If no DefaultListableBeanFactory, can't pass the resolver along.
            return ((ConfigurableListableBeanFactory) parent).isAutowireCandidate(beanName, descriptor);
        } else {
            return true;
        }
    }

    /**
     * Determine whether the specified bean definition qualifies as an autowire candidate,
     * to be injected into other beans which declare a dependency of matching type.
     *
     * @param beanName   the name of the bean definition to check
     * @param mbd        the merged bean definition to check
     * @param descriptor the descriptor of the dependency to resolve
     * @param resolver   the AutowireCandidateResolver to use for the actual resolution algorithm
     * @return whether the bean should be considered as autowire candidate
     */
    protected boolean isAutowireCandidate(String beanName, RootBeanDefinition mbd, DependencyDescriptor descriptor, AutowireCandidateResolver resolver) {

        String bdName = BeanFactoryUtils.transformedBeanName(beanName);
        resolveBeanClass(mbd, bdName);
        if (mbd.isFactoryMethodUnique && mbd.factoryMethodToIntrospect == null) {
            new ConstructorResolver(this).resolveFactoryMethodIfPossible(mbd);
        }
        BeanDefinitionHolder holder = (beanName.equals(bdName) ? this.mergedBeanDefinitionHolders.computeIfAbsent(beanName, key -> new BeanDefinitionHolder(mbd, beanName, getAliases(bdName))) : new BeanDefinitionHolder(mbd, beanName, getAliases(bdName)));
        /**
         * ContextAnnotationAutowireCandidateResolver
         * {@link QualifierAnnotationAutowireCandidateResolver#isAutowireCandidate(BeanDefinitionHolder, DependencyDescriptor)}
         * */
        return resolver.isAutowireCandidate(holder, descriptor);
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        BeanDefinition bd = this.beanDefinitionMap.get(beanName);
        if (bd == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No bean named '" + beanName + "' found in " + this);
            }
            throw new NoSuchBeanDefinitionException(beanName);
        }
        return bd;
    }

    @Override
    public Iterator<String> getBeanNamesIterator() {
        CompositeIterator<String> iterator = new CompositeIterator<>();
        iterator.add(this.beanDefinitionNames.iterator());
        iterator.add(this.manualSingletonNames.iterator());
        return iterator;
    }

    @Override
    protected void clearMergedBeanDefinition(String beanName) {
        super.clearMergedBeanDefinition(beanName);
        this.mergedBeanDefinitionHolders.remove(beanName);
    }

    @Override
    public void clearMetadataCache() {
        super.clearMetadataCache();
        this.mergedBeanDefinitionHolders.clear();
        clearByTypeCache();
    }

    @Override
    public void freezeConfiguration() {
        this.configurationFrozen = true;
        this.frozenBeanDefinitionNames = StringUtils.toStringArray(this.beanDefinitionNames);
    }

    @Override
    public boolean isConfigurationFrozen() {
        return this.configurationFrozen;
    }

    /**
     * Considers all beans as eligible for metadata caching
     * if the factory's configuration has been marked as frozen.
     *
     * @see #freezeConfiguration()
     */
    @Override
    protected boolean isBeanEligibleForMetadataCaching(String beanName) {
        return (this.configurationFrozen || super.isBeanEligibleForMetadataCaching(beanName));
    }

    @Override
    public void preInstantiateSingletons() throws BeansException {
        if (logger.isTraceEnabled()) {
            logger.trace("Pre-instantiating singletons in " + this);
        }

        // 获取我们容器中所有bean定义的名称
        // Iterate over a copy to allow for init methods which in turn register new bean definitions.
        // While this may not be part of the regular factory bootstrap, it does otherwise work fine.
        List<String> beanNames = new ArrayList<>(this.beanDefinitionNames);

        // 循环我们所有的bean定义名称
        // Trigger initialization of all non-lazy singleton beans...
        for (String beanName : beanNames) {
            // 合并我们的bean定义: 合并父子类的beanDefinition
            RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);

            /**
             * 根据bean定义判断 不是抽象的 && 是单例的 && 不是懒加载的
             */
            if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                // 是不是工厂bean
                if (isFactoryBean(beanName)) {
                    /**
                     * FactoryBean ：
                     *  1. 会创建两个bean。FactoryBean本身 和 getObject() 返回的值。
                     *  2. getObject() 返回的bean，不是在单例bean创建的时候创建的，是在获取的时候才会生成 context.getBean("myFactoryBean")
                     *
                     * 使用场景：需要所有的bean都创建了，在创建 就可以使用 FactoryBean
                     * 注：如果需要在 单例bean 创建阶段 就创建好两个Bean，可以使用 SmartFactoryBean
                     *      @see cn.haitaoss.javaconfig.factorybean.MySmartFactoryBean
                     *
                     * context.getBean("myFactoryBean") 会发生什么：
                     *  1. 会先从缓存里面获取
                     *      @see org.springframework.beans.factory.support.FactoryBeanRegistrySupport#getCachedObjectForFactoryBean(java.lang.String)
                     *  2. 获取不到就执行 org.springframework.beans.factory.FactoryBean#getObject() 实例化bean，然后放入 cachedObjectForFactoryBean 缓存
                     *      @see FactoryBeanRegistrySupport#doGetObjectFromFactoryBean(FactoryBean, String)
                     *
                     * context.getBean("&myFactoryBean") 会发生什么：
                     *  满足下面的条件，所以直接返回 单例池 里面的bean
                     *  @see BeanFactoryUtils#isFactoryDereference(String)
                     */
                    // 是 factoryBean，会先生成实际的bean &beanName是用来获取实际bean的
                    Object bean = getBean(FACTORY_BEAN_PREFIX + beanName);
                    if (bean instanceof FactoryBean) {
                        FactoryBean<?> factory = (FactoryBean<?>) bean;
                        boolean isEagerInit;
                        if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                            isEagerInit = AccessController.doPrivileged((PrivilegedAction<Boolean>) ((SmartFactoryBean<?>) factory)::isEagerInit, getAccessControlContext());
                        } else {
                            isEagerInit = (factory instanceof SmartFactoryBean
                                    && ((SmartFactoryBean<?>) factory).isEagerInit());
                        }
                        if (isEagerInit) {
                            // Factory 允许提前创建， 就会创建 FactoryBean#getObject
                            getBean(beanName);
                        }
                    }
                } else {
                    // 非工厂Bean就是普通的bean
                    getBean(beanName);
                }
            }
        }

        // 到这里所有的单实例的bean已经加载到单例池(singletonObjects)中了

        // 遍历bean的名字，回调满足条件的单例bean
        // Trigger post-initialization callback for all applicable beans...
        for (String beanName : beanNames) {
            // 从单例缓存池中获取所有的对象
            Object singletonInstance = getSingleton(beanName);
            // 判断当前的bean是否实现了 SmartInitializingSingleton 接口
            if (singletonInstance instanceof SmartInitializingSingleton) {
                StartupStep smartInitialize = this.getApplicationStartup()
                        .start("spring.beans.smart-initialize")
                        .tag("beanName", beanName);
                SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
                if (System.getSecurityManager() != null) {
                    AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
                        smartSingleton.afterSingletonsInstantiated();
                        return null;
                    }, getAccessControlContext());
                } else {
                    smartSingleton.afterSingletonsInstantiated();
                }
                smartInitialize.end();
            }
        }
    }


    //---------------------------------------------------------------------
    // Implementation of BeanDefinitionRegistry interface
    //---------------------------------------------------------------------

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) throws BeanDefinitionStoreException {

        Assert.hasText(beanName, "Bean name must not be empty");
        Assert.notNull(beanDefinition, "BeanDefinition must not be null");

        if (beanDefinition instanceof AbstractBeanDefinition) {
            try {
                ((AbstractBeanDefinition) beanDefinition).validate();
            } catch (BeanDefinitionValidationException ex) {
                throw new BeanDefinitionStoreException(beanDefinition.getResourceDescription(), beanName, "Validation of bean definition failed", ex);
            }
        }

        BeanDefinition existingDefinition = this.beanDefinitionMap.get(beanName);
        if (existingDefinition != null) {
            if (!isAllowBeanDefinitionOverriding()) {
                throw new BeanDefinitionOverrideException(beanName, beanDefinition, existingDefinition);
            } else if (existingDefinition.getRole() < beanDefinition.getRole()) {
                // e.g. was ROLE_APPLICATION, now overriding with ROLE_SUPPORT or ROLE_INFRASTRUCTURE
                if (logger.isInfoEnabled()) {
                    logger.info("Overriding user-defined bean definition for bean '" + beanName
                            + "' with a framework-generated bean definition: replacing [" + existingDefinition
                            + "] with [" + beanDefinition + "]");
                }
            } else if (!beanDefinition.equals(existingDefinition)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Overriding bean definition for bean '" + beanName
                            + "' with a different definition: replacing [" + existingDefinition + "] with ["
                            + beanDefinition + "]");
                }
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Overriding bean definition for bean '" + beanName
                            + "' with an equivalent definition: replacing [" + existingDefinition + "] with ["
                            + beanDefinition + "]");
                }
            }
            this.beanDefinitionMap.put(beanName, beanDefinition);
        } else {
            if (hasBeanCreationStarted()) {
                // Cannot modify startup-time collection elements anymore (for stable iteration)
                synchronized (this.beanDefinitionMap) {
                    this.beanDefinitionMap.put(beanName, beanDefinition);
                    List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames.size() + 1);
                    updatedDefinitions.addAll(this.beanDefinitionNames);
                    updatedDefinitions.add(beanName);
                    this.beanDefinitionNames = updatedDefinitions;
                    removeManualSingletonName(beanName);
                }
            } else {
                // Still in startup registration phase
                this.beanDefinitionMap.put(beanName, beanDefinition);
                this.beanDefinitionNames.add(beanName);
                removeManualSingletonName(beanName);
            }
            this.frozenBeanDefinitionNames = null;
        }

        if (existingDefinition != null || containsSingleton(beanName)) {
            resetBeanDefinition(beanName);
        } else if (isConfigurationFrozen()) {
            clearByTypeCache();
        }
    }

    @Override
    public void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException {
        Assert.hasText(beanName, "'beanName' must not be empty");

        BeanDefinition bd = this.beanDefinitionMap.remove(beanName);
        if (bd == null) {
            if (logger.isTraceEnabled()) {
                logger.trace("No bean named '" + beanName + "' found in " + this);
            }
            throw new NoSuchBeanDefinitionException(beanName);
        }

        if (hasBeanCreationStarted()) {
            // Cannot modify startup-time collection elements anymore (for stable iteration)
            synchronized (this.beanDefinitionMap) {
                List<String> updatedDefinitions = new ArrayList<>(this.beanDefinitionNames);
                updatedDefinitions.remove(beanName);
                this.beanDefinitionNames = updatedDefinitions;
            }
        } else {
            // Still in startup registration phase
            this.beanDefinitionNames.remove(beanName);
        }
        this.frozenBeanDefinitionNames = null;

        resetBeanDefinition(beanName);
    }

    /**
     * Reset all bean definition caches for the given bean,
     * including the caches of beans that are derived from it.
     * <p>Called after an existing bean definition has been replaced or removed,
     * triggering {@link #clearMergedBeanDefinition}, {@link #destroySingleton}
     * and {@link MergedBeanDefinitionPostProcessor#resetBeanDefinition} on the
     * given bean and on all bean definitions that have the given bean as parent.
     *
     * @param beanName the name of the bean to reset
     * @see #registerBeanDefinition
     * @see #removeBeanDefinition
     */
    protected void resetBeanDefinition(String beanName) {
        // Remove the merged bean definition for the given bean, if already created.
        clearMergedBeanDefinition(beanName);

        // Remove corresponding bean from singleton cache, if any. Shouldn't usually
        // be necessary, rather just meant for overriding a context's default beans
        // (e.g. the default StaticMessageSource in a StaticApplicationContext).
        destroySingleton(beanName);

        // Notify all post-processors that the specified bean definition has been reset.
        for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
            processor.resetBeanDefinition(beanName);
        }

        // Reset all bean definitions that have the given bean as parent (recursively).
        for (String bdName : this.beanDefinitionNames) {
            if (!beanName.equals(bdName)) {
                BeanDefinition bd = this.beanDefinitionMap.get(bdName);
                // Ensure bd is non-null due to potential concurrent modification of beanDefinitionMap.
                if (bd != null && beanName.equals(bd.getParentName())) {
                    resetBeanDefinition(bdName);
                }
            }
        }
    }

    /**
     * Only allows alias overriding if bean definition overriding is allowed.
     */
    @Override
    protected boolean allowAliasOverriding() {
        return isAllowBeanDefinitionOverriding();
    }

    /**
     * Also checks for an alias overriding a bean definition of the same name.
     */
    @Override
    protected void checkForAliasCircle(String name, String alias) {
        super.checkForAliasCircle(name, alias);
        if (!isAllowBeanDefinitionOverriding() && containsBeanDefinition(alias)) {
            throw new IllegalStateException("Cannot register alias '" + alias + "' for name '" + name
                    + "': Alias would override bean definition '" + alias + "'");
        }
    }

    @Override
    public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
        super.registerSingleton(beanName, singletonObject);
        updateManualSingletonNames(set -> set.add(beanName), set -> !this.beanDefinitionMap.containsKey(beanName));
        clearByTypeCache();
    }

    @Override
    public void destroySingletons() {
        super.destroySingletons();
        updateManualSingletonNames(Set::clear, set -> !set.isEmpty());
        clearByTypeCache();
    }

    @Override
    public void destroySingleton(String beanName) {
        super.destroySingleton(beanName);
        removeManualSingletonName(beanName);
        clearByTypeCache();
    }

    private void removeManualSingletonName(String beanName) {
        updateManualSingletonNames(set -> set.remove(beanName), set -> set.contains(beanName));
    }

    /**
     * Update the factory's internal set of manual singleton names.
     *
     * @param action    the modification action
     * @param condition a precondition for the modification action
     *                  (if this condition does not apply, the action can be skipped)
     */
    private void updateManualSingletonNames(Consumer<Set<String>> action, Predicate<Set<String>> condition) {
        if (hasBeanCreationStarted()) {
            // Cannot modify startup-time collection elements anymore (for stable iteration)
            synchronized (this.beanDefinitionMap) {
                if (condition.test(this.manualSingletonNames)) {
                    Set<String> updatedSingletons = new LinkedHashSet<>(this.manualSingletonNames);
                    action.accept(updatedSingletons);
                    this.manualSingletonNames = updatedSingletons;
                }
            }
        } else {
            // Still in startup registration phase
            if (condition.test(this.manualSingletonNames)) {
                action.accept(this.manualSingletonNames);
            }
        }
    }

    /**
     * Remove any assumptions about by-type mappings.
     */
    private void clearByTypeCache() {
        this.allBeanNamesByType.clear();
        this.singletonBeanNamesByType.clear();
    }


    //---------------------------------------------------------------------
    // Dependency resolution functionality
    //---------------------------------------------------------------------

    @Override
    public <T> NamedBeanHolder<T> resolveNamedBean(Class<T> requiredType) throws BeansException {
        Assert.notNull(requiredType, "Required type must not be null");
        NamedBeanHolder<T> namedBean = resolveNamedBean(ResolvableType.forRawClass(requiredType), null, false);
        if (namedBean != null) {
            return namedBean;
        }
        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof AutowireCapableBeanFactory) {
            return ((AutowireCapableBeanFactory) parent).resolveNamedBean(requiredType);
        }
        throw new NoSuchBeanDefinitionException(requiredType);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> NamedBeanHolder<T> resolveNamedBean(ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) throws BeansException {

        Assert.notNull(requiredType, "Required type must not be null");
        String[] candidateNames = getBeanNamesForType(requiredType);

        if (candidateNames.length > 1) {
            List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
            for (String beanName : candidateNames) {
                if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
                    autowireCandidates.add(beanName);
                }
            }
            if (!autowireCandidates.isEmpty()) {
                candidateNames = StringUtils.toStringArray(autowireCandidates);
            }
        }

        if (candidateNames.length == 1) {
            return resolveNamedBean(candidateNames[0], requiredType, args);
        } else if (candidateNames.length > 1) {
            Map<String, Object> candidates = CollectionUtils.newLinkedHashMap(candidateNames.length);
            for (String beanName : candidateNames) {
                if (containsSingleton(beanName) && args == null) {
                    Object beanInstance = getBean(beanName);
                    candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
                } else {
                    candidates.put(beanName, getType(beanName));
                }
            }
            // 通过@Primary找
            String candidateName = determinePrimaryCandidate(candidates, requiredType.toClass());
            if (candidateName == null) {
                // 通过 @Priority(1) 找到属性值最小的
                candidateName = determineHighestPriorityCandidate(candidates, requiredType.toClass());
            }
            if (candidateName != null) {
                Object beanInstance = candidates.get(candidateName);
                if (beanInstance == null) {
                    return null;
                }
                if (beanInstance instanceof Class) {
                    // 是 Class 说明，还没有实例化，在这里进行 getBean
                    return resolveNamedBean(candidateName, requiredType, args);
                }
                return new NamedBeanHolder<>(candidateName, (T) beanInstance);
            }
            if (!nonUniqueAsNull) {
                // 无法确定唯一一个，所以只能爆出了
                throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
            }
        }

        return null;
    }

    @Nullable
    private <T> NamedBeanHolder<T> resolveNamedBean(String beanName, ResolvableType requiredType, @Nullable Object[] args) throws BeansException {

        Object bean = getBean(beanName, null, args);
        if (bean instanceof NullBean) {
            return null;
        }
        return new NamedBeanHolder<T>(beanName, adaptBeanInstance(beanName, bean, requiredType.toClass()));
    }

    /**
     *
     * @param descriptor the descriptor for the dependency (field/method/constructor) （就是对 field/method/constructor 的装饰而已）
     * @param requestingBeanName the name of the bean which declares the given dependency（声明给定依赖项的 bean 的名称，就是这个依赖是哪个bean对象的）
     * @param autowiredBeanNames a Set that all names of autowired beans (used for
     * resolving the given dependency) are supposed to be added to（）（依赖的类型的bean的名字，因为容器中一个类型可能会有多个实例）
     * @param typeConverter the TypeConverter to use for populating arrays and collections（类型转换器）
     * @return
     * @throws BeansException
     */
    @Override
    @Nullable
    public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {
        /**
         * descriptor 是 Field 的装饰，下面这个初始是不会生效的。
         * descriptor 是 Method 的装饰, 会设置这个东东 {@link DefaultParameterNameDiscoverer}
         * */
        descriptor.initParameterNameDiscovery(getParameterNameDiscoverer());
        if (Optional.class == descriptor.getDependencyType()) {
            return createOptionalDependency(descriptor, requestingBeanName);
        } else if (ObjectFactory.class == descriptor.getDependencyType()
                || ObjectProvider.class == descriptor.getDependencyType()) {
            return new DependencyObjectProvider(descriptor, requestingBeanName);
        } else if (javaxInjectProviderClass == descriptor.getDependencyType()) {
            return new Jsr330Factory().createDependencyProvider(descriptor, requestingBeanName);
        } else {
            /**
             * class A{
             *  @Autowired
             *  @Lazy
             *  private A a;
             * }
             * 是这个类型的 ContextAnnotationAutowireCandidateResolver ，这个东西是在实例化AnnotationConfigApplicationContext的时候设置的
             *
             * 这一步主要是判断是否有@Laxy(true),有就通过ProxyFactory创建代理对象
             * */
            Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(descriptor, requestingBeanName);
            if (result == null) {
                /**
                 * 就是上面没有创建代理对象，就执行解析依赖。
                 * */
                result = doResolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
            }
            return result;
        }
    }

    @Nullable
    public Object doResolveDependency(DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) throws BeansException {
        /**
         * 万恶之源
         * {@link AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#resolveFieldValue(Field, Object, String)}
         * {@link AutowiredAnnotationBeanPostProcessor.AutowiredMethodElement#resolveMethodArguments(Method, Object, String)}
         * */
        // ThreadLocal 记录一下
        InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
        try {
            /**
             * 如果被解析过了，会缓存成 ShortcutDependencyDescriptor 类型，所以是第二次才会真正的执行，因为
             * DependencyDescriptor 的实例，该方法没有做具体实现直接返回null
             * */
            Object shortcut = descriptor.resolveShortcut(this);
            if (shortcut != null) {
                return shortcut;
            }
            // 依赖的类型
            Class<?> type = descriptor.getDependencyType();
            /**
             *
             * getAutowireCandidateResolver 是这个 ContextAnnotationAutowireCandidateResolver
             * 是在实例化 AnnotationConfigApplicationContext 的时候注册的
             *      {@link org.springframework.context.annotation.AnnotatedBeanDefinitionReader#AnnotatedBeanDefinitionReader(BeanDefinitionRegistry, Environment)}
             *
             * 执行的 ContextAnnotationAutowireCandidateResolver 父类的方法{@link QualifierAnnotationAutowireCandidateResolver#getSuggestedValue(DependencyDescriptor)}
             *
             * 这里是处理@Value注解，拿到@Value注解的值。查找顺序: 字段、方法参数没有@Value() -> 如果是方法参数依赖，就看看方法上有没有@Value
             * */
            Object value = getAutowireCandidateResolver().getSuggestedValue(descriptor);
            if (value != null) {
                if (value instanceof String) {
                    // 解析占位符的
                    String strVal = resolveEmbeddedValue((String) value);
                    BeanDefinition bd = (
                            beanName != null && containsBean(beanName) ? getMergedBeanDefinition(beanName) : null);
                    /**
                     * 如果是 @Value("#{a}")，会从容器中获取 a 这个bean
                     * 进行SpEl解析
                     * */
                    value = evaluateBeanDefinitionString(strVal, bd);
                }
                /**
                 * 拿到 TypeConverter，默认是没有，所以会创建 `new SimpleTypeConverter();`
                 * */
                TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
                try {
                    /**
                     * 默认是这个 SimpleTypeConverter
                     * */
                    return converter.convertIfNecessary(value, type, descriptor.getTypeDescriptor());
                } catch (UnsupportedOperationException ex) {
                    // A custom TypeConverter which does not support TypeDescriptor resolution...
                    return (descriptor.getField()
                            != null ? converter.convertIfNecessary(value, type, descriptor.getField()) : converter.convertIfNecessary(value, type, descriptor.getMethodParameter()));
                }
            }

            /**
             * 没有@Value注解的时候，然后参数的类似是 集合、Map、数组的情况
             *  {@link DefaultListableBeanFactory#findAutowireCandidates(String, Class, DependencyDescriptor)}
             * */
            Object multipleBeans = resolveMultipleBeans(descriptor, beanName, autowiredBeanNames, typeConverter);
            if (multipleBeans != null) {
                return multipleBeans;
            }

            /**
             * 找到自动注入的候选bean：
             *  1. 通过类型从 BeanFactory 中找到beanName
             *  2. 通过类型从 BeanFactory的依赖中 {@link DefaultListableBeanFactory#resolvableDependencies} 找到beanName
             *  3. 通过BeanFactory实例化满足条件的bean
             *
             * {@link DefaultListableBeanFactory#findAutowireCandidates(String, Class, DependencyDescriptor)}
             * */
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
            if (matchingBeans.isEmpty()) {
                /**
                 * @Autowired(required = true)
                 * 这个依赖是必须的，还找不到bean，那就直接报错
                 * */
                if (isRequired(descriptor)) {
                    // 抛出异常
                    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
                }
                return null;
            }

            String autowiredBeanName;
            Object instanceCandidate;

            if (matchingBeans.size() > 1) {
                /**
                 * 确定要注入的候选者是那个。
                 *
                 * 有@Primary的就是要返回的，如果没有@Primary，在找有@Priority的，找到@Priority(值) 值最小的返回
                 * */
                autowiredBeanName = determineAutowireCandidate(matchingBeans, descriptor);
                if (autowiredBeanName == null) {
                    // 是需要的
                    if (isRequired(descriptor) || !indicatesMultipleBeans(type)) {
                        // 抛出异常
                        return descriptor.resolveNotUnique(descriptor.getResolvableType(), matchingBeans);
                    } else {
                        // In case of an optional Collection/Map, silently ignore a non-unique case:
                        // possibly it was meant to be an empty collection of multiple regular beans
                        // (before 4.3 in particular when we didn't even look for collection beans).
                        return null;
                    }
                }
                // 拿到唯一的结果
                instanceCandidate = matchingBeans.get(autowiredBeanName);
            } else {
                // We have exactly one match.
                Map.Entry<String, Object> entry = matchingBeans.entrySet()
                        .iterator()
                        .next();
                autowiredBeanName = entry.getKey();
                instanceCandidate = entry.getValue();
            }

            if (autowiredBeanNames != null) {
                // 记录这个依赖对应的beanName
                autowiredBeanNames.add(autowiredBeanName);
            }
            if (instanceCandidate instanceof Class) {
                /**
                 * instanceCandidate 是 Class，说明还没有实例化，那就`getBean`
                 * {@link DependencyDescriptor#resolveCandidate(String, Class, BeanFactory)} `return beanFactory.getBean(beanName);`
                 * */
                instanceCandidate = descriptor.resolveCandidate(autowiredBeanName, type, this);
            }
            Object result = instanceCandidate;
            if (result instanceof NullBean) {
                // 结果是空，还是必须的就直接抛出异常
                if (isRequired(descriptor)) {
                    raiseNoMatchingBeanFound(type, descriptor.getResolvableType(), descriptor);
                }
                result = null;
            }
            if (!ClassUtils.isAssignableValue(type, result)) {
                throw new BeanNotOfRequiredTypeException(autowiredBeanName, type, instanceCandidate.getClass());
            }
            return result;
        } finally {
            // 移除标记
            ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
        }
    }

    @Nullable
    private Object resolveMultipleBeans(DependencyDescriptor descriptor, @Nullable String beanName, @Nullable Set<String> autowiredBeanNames, @Nullable TypeConverter typeConverter) {
        /**
         * 要注入的对象的类型
         * 这个是拿到什么呀，写个泛型试试看
         *
         * 试试List<List<String>>的情况
         * */
        Class<?> type = descriptor.getDependencyType();

        if (descriptor instanceof StreamDependencyDescriptor) {
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, type, descriptor);
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            Stream<Object> stream = matchingBeans.keySet()
                    .stream()
                    .map(name -> descriptor.resolveCandidate(name, type, this))
                    .filter(bean -> !(bean instanceof NullBean));
            if (((StreamDependencyDescriptor) descriptor).isOrdered()) {
                stream = stream.sorted(adaptOrderComparator(matchingBeans));
            }
            return stream;
        } else if (type.isArray()) {
            /**
             * 拿到数组的元素的类型。
             * 比如`Object[]` 拿到的就是 Object
             * */
            Class<?> componentType = type.getComponentType();
            /**
             * TODOHAITAO: 2022/10/9 这里解析是要做啥啊，我人麻了
             * */
            ResolvableType resolvableType = descriptor.getResolvableType();
            Class<?> resolvedArrayType = resolvableType.resolve(type);
            if (resolvedArrayType != type) {
                componentType = resolvableType.getComponentType()
                        .resolve();
            }
            if (componentType == null) {
                return null;
            }
            /**
             * 从容器中找到 componentType 类型的bean。
             * key: BeanName
             * value: bean实例
             *
             * `new MultiElementDescriptor(descriptor)` 是有用的，决定了采用什么方式 生成bean实例
             * {@link DefaultListableBeanFactory#addCandidateEntry(Map, String, DependencyDescriptor, Class)}
             * */
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, componentType, new MultiElementDescriptor(descriptor));
            if (matchingBeans.isEmpty()) {
                return null;
            }
            if (autowiredBeanNames != null) {
                // 记录 记录自动注入依赖的beanName
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
            // 使用TypeConverter 进行转换一下
            Object result = converter.convertIfNecessary(matchingBeans.values(), resolvedArrayType);
            if (result instanceof Object[]) {
                /**
                 * 使用依赖排序器，对结果进行排序，默认是这个 AnnotationAwareOrderComparator
                 * 这个是在实例化IOC容器的时候设置的 {@link org.springframework.context.annotation.AnnotationConfigApplicationContext#AnnotationConfigApplicationContext()}
                 * */
                Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
                if (comparator != null) {
                    Arrays.sort((Object[]) result, comparator);
                }
            }
            return result;
        } else if (Collection.class.isAssignableFrom(type) && type.isInterface()) {
            Class<?> elementType = descriptor.getResolvableType()
                    .asCollection()
                    .resolveGeneric();
            if (elementType == null) {
                return null;
            }
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, elementType, new MultiElementDescriptor(descriptor));
            if (matchingBeans.isEmpty()) {
                return null;
            }
            if (autowiredBeanNames != null) {
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            TypeConverter converter = (typeConverter != null ? typeConverter : getTypeConverter());
            Object result = converter.convertIfNecessary(matchingBeans.values(), type);
            if (result instanceof List) {
                if (((List<?>) result).size() > 1) {
                    Comparator<Object> comparator = adaptDependencyComparator(matchingBeans);
                    if (comparator != null) {
                        ((List<?>) result).sort(comparator);
                    }
                }
            }
            return result;
        } else if (Map.class == type) {
            ResolvableType mapType = descriptor.getResolvableType()
                    .asMap();
            Class<?> keyType = mapType.resolveGeneric(0);
            // key 不是String 类型，直接 return null 不处理
            if (String.class != keyType) {
                return null;
            }
            // 拿到Map的value的泛型类型
            Class<?> valueType = mapType.resolveGeneric(1);
            if (valueType == null) {
                return null;
            }
            // 从容器中找到符合类型的bean
            Map<String, Object> matchingBeans = findAutowireCandidates(beanName, valueType, new MultiElementDescriptor(descriptor));
            if (matchingBeans.isEmpty()) {
                return null;
            }
            if (autowiredBeanNames != null) {
                // 记录符合依赖类型的beanNames
                autowiredBeanNames.addAll(matchingBeans.keySet());
            }
            return matchingBeans;
        } else {
            return null;
        }
    }

    private boolean isRequired(DependencyDescriptor descriptor) {
        return getAutowireCandidateResolver().isRequired(descriptor);
    }

    private boolean indicatesMultipleBeans(Class<?> type) {
        return (type.isArray() || (type.isInterface() && (Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type))));
    }

    @Nullable
    private Comparator<Object> adaptDependencyComparator(Map<String, ?> matchingBeans) {
        Comparator<Object> comparator = getDependencyComparator();
        if (comparator instanceof OrderComparator) {
            return ((OrderComparator) comparator).withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
        } else {
            return comparator;
        }
    }

    private Comparator<Object> adaptOrderComparator(Map<String, ?> matchingBeans) {
        Comparator<Object> dependencyComparator = getDependencyComparator();
        OrderComparator comparator = (dependencyComparator instanceof OrderComparator ? (OrderComparator) dependencyComparator : OrderComparator.INSTANCE);
        return comparator.withSourceProvider(createFactoryAwareOrderSourceProvider(matchingBeans));
    }

    private OrderComparator.OrderSourceProvider createFactoryAwareOrderSourceProvider(Map<String, ?> beans) {
        IdentityHashMap<Object, String> instancesToBeanNames = new IdentityHashMap<>();
        beans.forEach((beanName, instance) -> instancesToBeanNames.put(instance, beanName));
        return new FactoryAwareOrderSourceProvider(instancesToBeanNames);
    }

    /**
     * 1. 先从BeanFactory中根据类型找到匹配的bean
     * 2. 再从依赖中找到匹配的bean
     * 3. 收集到 candidateNames
     * 4. 实例化这些 candidateNames
     *
     * Find bean instances that match the required type.
     * Called during autowiring for the specified bean.
     *
     * @param beanName     the name of the bean that is about to be wired
     * @param requiredType the actual type of bean to look for
     *                     (may be an array component type or collection element type)
     * @param descriptor   the descriptor of the dependency to resolve
     * @return a Map of candidate names and candidate instances that match
     * the required type (never {@code null})
     * @throws BeansException in case of errors
     * @see #autowireByType
     * @see #autowireConstructor
     */
    protected Map<String, Object> findAutowireCandidates(@Nullable String beanName, Class<?> requiredType, DependencyDescriptor descriptor) {
        /**
         * 找到类型匹配的 beanName 返回。
         * 遍历所有的 BeanDefinition 进行类型匹配，主要是分两种类型：
         *  - 不是FactoryBean：单例池有，就使用单例池的结果判断类型。单例池没有就通过BeanDefinition判断
         *  - 是 FactoryBean：分为FactoryBean实例的判断和 FactoryBean#getObject 返回值的判断。(总之会实例化 FactoryBean 实例)
         *
         * */
        String[] candidateNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this, requiredType, true, descriptor.isEager());
        Map<String, Object> result = CollectionUtils.newLinkedHashMap(candidateNames.length);
        /**
         * 在这里 {@link org.springframework.context.support.AbstractApplicationContext#prepareBeanFactory(ConfigurableListableBeanFactory)}
         * 会设置这个属性 resolvableDependencies。
         *
         * Tips：resolvableDependencies 也叫bean伪装，因为这些依赖值是直接new出来的，不是通过`getBean()` 创建出来的。相当于扩展了BeanFactory可以注入的依赖类型。
         * */
        for (Map.Entry<Class<?>, Object> classObjectEntry : this.resolvableDependencies.entrySet()) {
            // 可解析的依赖类型
            Class<?> autowiringType = classObjectEntry.getKey();
            // requiredType 是 可解析的依赖类型 的子类
            if (autowiringType.isAssignableFrom(requiredType)) {
                // 依赖的值
                Object autowiringValue = classObjectEntry.getValue();
                // 看看是否需要 创建代理对象
                autowiringValue = AutowireUtils.resolveAutowiringValue(autowiringValue, requiredType);
                // 依赖的值 是 requiredType 的实例
                if (requiredType.isInstance(autowiringValue)) {
                    // 记录一下
                    result.put(ObjectUtils.identityToString(autowiringValue), autowiringValue);
                    break;
                }
            }
        }
        for (String candidate : candidateNames) {
            /**
             * 不是自引用 且 是自动注入候选，才记录。这里主要是如果依赖存在@Qualifier注解，才会判断名字是否匹配的。
             *
             * 没看太懂 {@link DefaultListableBeanFactory#isAutowireCandidate(String, DependencyDescriptor)}
             * */
            if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, descriptor)) {
                /**
                 * 这里就是会从容器中实例化这个bean `beanFactory.getBean(beanName);`
                 * */
                addCandidateEntry(result, candidate, descriptor, requiredType);
            }
        }
        /**
         * 兜底处理
         * */
        if (result.isEmpty()) {
            boolean multiple = indicatesMultipleBeans(requiredType);
            // Consider fallback matches if the first pass failed to find anything...
            DependencyDescriptor fallbackDescriptor = descriptor.forFallbackMatch();
            for (String candidate : candidateNames) {
                /**
                 * isAutowireCandidate 是自动注入候选，才记录。这里主要是如果依赖存在@Qualifier注解，才会判断名字是否匹配的。
                 * */
                if (!isSelfReference(beanName, candidate) && isAutowireCandidate(candidate, fallbackDescriptor) && (
                        !multiple || getAutowireCandidateResolver().hasQualifier(descriptor))) {
                    addCandidateEntry(result, candidate, descriptor, requiredType);
                }
            }
            if (result.isEmpty() && !multiple) {
                /**
                 * isAutowireCandidate 是自动注入候选，才记录。这里主要是如果依赖存在@Qualifier注解，才会判断名字是否匹配的。
                 * */
                // Consider self references as a final pass...
                // but in the case of a dependency collection, not the very same bean itself.
                for (String candidate : candidateNames) {
                    if (isSelfReference(beanName, candidate) && (!(descriptor instanceof MultiElementDescriptor)
                            || !beanName.equals(candidate))
                            && isAutowireCandidate(candidate, fallbackDescriptor)) {
                        addCandidateEntry(result, candidate, descriptor, requiredType);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Add an entry to the candidate map: a bean instance if available or just the resolved
     * type, preventing early bean initialization ahead of primary candidate selection.
     */
    private void addCandidateEntry(Map<String, Object> candidates, String candidateName, DependencyDescriptor descriptor, Class<?> requiredType) {
        /**
         * 依赖类型是 数组、集合、Map时  才会装饰成 MultiElementDescriptor
         *
         * {@link DefaultListableBeanFactory#resolveMultipleBeans(DependencyDescriptor, String, Set, TypeConverter)}
         * */
        if (descriptor instanceof MultiElementDescriptor) {
            /**
             * 依赖类型是多个，所以需要把类型的bean都通过BeanFactory创建出来
             *
             * {@link DependencyDescriptor#resolveCandidate(String, Class, BeanFactory)}
             * 其实就是 `return beanFactory.getBean(beanName);`
             * */
            Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
            if (!(beanInstance instanceof NullBean)) {
                candidates.put(candidateName, beanInstance);
            }
        } else if (containsSingleton(candidateName) || (descriptor instanceof StreamDependencyDescriptor
                && ((StreamDependencyDescriptor) descriptor).isOrdered())) {
            /**
             * candidateName 已经在单例池创建好了，所以直接拿
             * */
            Object beanInstance = descriptor.resolveCandidate(candidateName, requiredType, this);
            candidates.put(candidateName, (beanInstance instanceof NullBean ? null : beanInstance));
        } else {
            /**
             * 依赖类型不是多个，且单例池没有，那么只记录其class。从BeanFactory中 通过beanName拿到其类型，
             *
             * 目的：因为依赖类型是单个，也就是一个bean而已，你直接把匹配的类型全部都实例化了肯定不靠谱呀
             * public class A {
             *  @Autowired
             *  public B b;
             *
             *  class Son1 extends A {}
             *  class Son2 extends A {}
             * }
             *
             * Tips：具体的实例化是在确定好唯一一个beanName的时候在实例化的。{@link DefaultListableBeanFactory#doResolveDependency(DependencyDescriptor, String, Set, TypeConverter)}
             * */
            candidates.put(candidateName, getType(candidateName));
        }
    }

    /**
     * Determine the autowire candidate in the given set of beans.
     * <p>Looks for {@code @Primary} and {@code @Priority} (in that order).
     *
     * @param candidates a Map of candidate names and candidate instances
     *                   that match the required type, as returned by {@link #findAutowireCandidates}
     * @param descriptor the target dependency to match against
     * @return the name of the autowire candidate, or {@code null} if none found
     */
    @Nullable
    protected String determineAutowireCandidate(Map<String, Object> candidates, DependencyDescriptor descriptor) {
        Class<?> requiredType = descriptor.getDependencyType();
        // 这里是找到有@Primary标注的bean，就返回
        String primaryCandidate = determinePrimaryCandidate(candidates, requiredType);
        if (primaryCandidate != null) {
            return primaryCandidate;
        }
        /**
         * 找到 排序值最小的候选bean 返回。
         *
         * 候选的bean有 @Priority，返回排序值小的bean。
         * 如果出现两个排序值一样的bean，直接抛出异常
         * */
        String priorityCandidate = determineHighestPriorityCandidate(candidates, requiredType);
        if (priorityCandidate != null) {
            return priorityCandidate;
        }
        /**
         * 应急计划。
         *  - 在 {@link DefaultListableBeanFactory#resolvableDependencies} 中能找到 bean
         *  - 候选bean的name 和 依赖的name(字段的名字，参数的名字) 一致也行
         * 通过这个也能确定唯一的bean
         * */
        // Fallback
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateName = entry.getKey();
            Object beanInstance = entry.getValue();
            if ((beanInstance != null && this.resolvableDependencies.containsValue(beanInstance))
                    /**
                     * 字段名或者方法的参数名 与 candidateName 相等就是true
                     * */
                    || matchesBeanName(candidateName, descriptor.getDependencyName())) {
                return candidateName;
            }
        }
        return null;
    }

    /**
     * Determine the primary candidate in the given set of beans.
     *
     * @param candidates   a Map of candidate names and candidate instances
     *                     (or candidate classes if not created yet) that match the required type
     * @param requiredType the target dependency type to match against
     * @return the name of the primary candidate, or {@code null} if none found
     * @see #isPrimary(String, Object)
     */
    @Nullable
    protected String determinePrimaryCandidate(Map<String, Object> candidates, Class<?> requiredType) {
        // 记录 @Primary 注解的beanName
        String primaryBeanName = null;
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateBeanName = entry.getKey();
            Object beanInstance = entry.getValue();
            // 这里就是看看有没有 @Primary
            if (isPrimary(candidateBeanName, beanInstance)) {
                if (primaryBeanName != null) {
                    boolean candidateLocal = containsBeanDefinition(candidateBeanName);
                    boolean primaryLocal = containsBeanDefinition(primaryBeanName);
                    /**
                     * primaryBeanName 是前面遍历到的bean,有 @Primary 就记录一下
                     * candidateBeanName 就是这次循环内容 有 @Primary 的情况
                     *
                     * 如果两个都存在说明有重复的 @Primary 所以直接报错
                     * */
                    if (candidateLocal && primaryLocal) {
                        throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
                                "more than one 'primary' bean found among candidates: " + candidates.keySet());
                    } else if (candidateLocal) {
                        primaryBeanName = candidateBeanName;
                    }
                } else {
                    // 设置值
                    primaryBeanName = candidateBeanName;
                }
            }
        }
        return primaryBeanName;
    }

    /**
     * 就是判断候选的bean中有没有 @Priority， 有的话 返回排序值小的bean，当出现两个排序值一样的候选bean直接报错 <br/>
     * Determine the candidate with the highest priority in the given set of beans.
     * <p>Based on {@code @javax.annotation.Priority}. As defined by the related
     * {@link org.springframework.core.Ordered} interface, the lowest value has
     * the highest priority.
     *
     * @param candidates   a Map of candidate names and candidate instances
     *                     (or candidate classes if not created yet) that match the required type
     * @param requiredType the target dependency type to match against
     * @return the name of the candidate with the highest priority,
     * or {@code null} if none found
     * @see #getPriority(Object)
     */
    @Nullable
    protected String determineHighestPriorityCandidate(Map<String, Object> candidates, Class<?> requiredType) {
        // 最终要返回的结果（排序值小的结果）
        String highestPriorityBeanName = null;
        // 临时遍历，记录 排序值
        Integer highestPriority = null;
        for (Map.Entry<String, Object> entry : candidates.entrySet()) {
            String candidateBeanName = entry.getKey();
            Object beanInstance = entry.getValue();
            if (beanInstance != null) {
                /**
                 * 会使用这个属性，拿到bean的排序是 {@link DefaultListableBeanFactory#getDependencyComparator()}
                 * 该属性是在 初始化IOC容器的时候设置的 {@link org.springframework.context.annotation.AnnotationConfigUtils#registerAnnotationConfigProcessors(BeanDefinitionRegistry, Object)}
                 *      获取排序值的方法 {@link AnnotationAwareOrderComparator#getPriority(Object)}
                 *      可以通过 @Priority 来设置排序值
                 * */
                Integer candidatePriority = getPriority(beanInstance);
                if (candidatePriority != null) {
                    if (highestPriorityBeanName != null) {
                        /**
                         * 前面的循环已经有了 排序值，当前循环的排序值 比较。
                         * 相等 就直接报错咯
                         * */
                        if (candidatePriority.equals(highestPriority)) {
                            throw new NoUniqueBeanDefinitionException(requiredType, candidates.size(),
                                    "Multiple beans found with the same priority ('" + highestPriority
                                            + "') among candidates: " + candidates.keySet());
                        } else if (candidatePriority < highestPriority) {
                            // 排序值小，就替换
                            highestPriorityBeanName = candidateBeanName;
                            highestPriority = candidatePriority;
                        }
                    } else {
                        // 记录最终要返回的bean
                        highestPriorityBeanName = candidateBeanName;
                        // 记录 排序值
                        highestPriority = candidatePriority;
                    }
                }
            }
        }
        return highestPriorityBeanName;
    }

    /**
     * Return whether the bean definition for the given bean name has been
     * marked as a primary bean.
     *
     * @param beanName     the name of the bean
     * @param beanInstance the corresponding bean instance (can be null)
     * @return whether the given bean qualifies as primary
     */
    protected boolean isPrimary(String beanName, Object beanInstance) {
        String transformedBeanName = transformedBeanName(beanName);
        if (containsBeanDefinition(transformedBeanName)) {
            return getMergedLocalBeanDefinition(transformedBeanName).isPrimary();
        }
        BeanFactory parent = getParentBeanFactory();
        return (parent instanceof DefaultListableBeanFactory
                && ((DefaultListableBeanFactory) parent).isPrimary(transformedBeanName, beanInstance));
    }

    /**
     * Return the priority assigned for the given bean instance by
     * the {@code javax.annotation.Priority} annotation.
     * <p>The default implementation delegates to the specified
     * {@link #setDependencyComparator dependency comparator}, checking its
     * {@link OrderComparator#getPriority method} if it is an extension of
     * Spring's common {@link OrderComparator} - typically, an
     * {@link org.springframework.core.annotation.AnnotationAwareOrderComparator}.
     * If no such comparator is present, this implementation returns {@code null}.
     *
     * @param beanInstance the bean instance to check (can be {@code null})
     * @return the priority assigned to that bean or {@code null} if none is set
     */
    @Nullable
    protected Integer getPriority(Object beanInstance) {
        Comparator<Object> comparator = getDependencyComparator();
        if (comparator instanceof OrderComparator) {
            return ((OrderComparator) comparator).getPriority(beanInstance);
        }
        return null;
    }

    /**
     * Determine whether the given candidate name matches the bean name or the aliases
     * stored in this bean definition.
     */
    protected boolean matchesBeanName(String beanName, @Nullable String candidateName) {
        return (candidateName != null && (candidateName.equals(beanName)
                || ObjectUtils.containsElement(getAliases(beanName), candidateName)));
    }

    /**
     * Determine whether the given beanName/candidateName pair indicates a self reference,
     * i.e. whether the candidate points back to the original bean or to a factory method
     * on the original bean.
     */
    private boolean isSelfReference(@Nullable String beanName, @Nullable String candidateName) {
        return (beanName != null && candidateName != null && (beanName.equals(candidateName) || (
                containsBeanDefinition(candidateName)
                        && beanName.equals(getMergedLocalBeanDefinition(candidateName).getFactoryBeanName()))));
    }

    /**
     * Raise a NoSuchBeanDefinitionException or BeanNotOfRequiredTypeException
     * for an unresolvable dependency.
     */
    private void raiseNoMatchingBeanFound(Class<?> type, ResolvableType resolvableType, DependencyDescriptor descriptor) throws BeansException {

        checkBeanNotOfRequiredType(type, descriptor);

        throw new NoSuchBeanDefinitionException(resolvableType,
                "expected at least 1 bean which qualifies as autowire candidate. " + "Dependency annotations: "
                        + ObjectUtils.nullSafeToString(descriptor.getAnnotations()));
    }

    /**
     * Raise a BeanNotOfRequiredTypeException for an unresolvable dependency, if applicable,
     * i.e. if the target type of the bean would match but an exposed proxy doesn't.
     */
    private void checkBeanNotOfRequiredType(Class<?> type, DependencyDescriptor descriptor) {
        for (String beanName : this.beanDefinitionNames) {
            try {
                RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                Class<?> targetType = mbd.getTargetType();
                if (targetType != null && type.isAssignableFrom(targetType)
                        && isAutowireCandidate(beanName, mbd, descriptor, getAutowireCandidateResolver())) {
                    // Probably a proxy interfering with target type match -> throw meaningful exception.
                    Object beanInstance = getSingleton(beanName, false);
                    Class<?> beanType = (beanInstance != null && beanInstance.getClass()
                            != NullBean.class ? beanInstance.getClass() : predictBeanType(beanName, mbd));
                    if (beanType != null && !type.isAssignableFrom(beanType)) {
                        throw new BeanNotOfRequiredTypeException(beanName, type, beanType);
                    }
                }
            } catch (NoSuchBeanDefinitionException ex) {
                // Bean definition got removed while we were iterating -> ignore.
            }
        }

        BeanFactory parent = getParentBeanFactory();
        if (parent instanceof DefaultListableBeanFactory) {
            ((DefaultListableBeanFactory) parent).checkBeanNotOfRequiredType(type, descriptor);
        }
    }

    /**
     * Create an {@link Optional} wrapper for the specified dependency.
     */
    private Optional<?> createOptionalDependency(DependencyDescriptor descriptor, @Nullable String beanName, final Object... args) {

        DependencyDescriptor descriptorToUse = new NestedDependencyDescriptor(descriptor) {
            @Override
            public boolean isRequired() {
                return false;
            }

            @Override
            public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
                return (!ObjectUtils.isEmpty(args) ? beanFactory.getBean(beanName, args) : super.resolveCandidate(beanName, requiredType, beanFactory));
            }
        };
        Object result = doResolveDependency(descriptorToUse, beanName, null, null);
        return (result instanceof Optional ? (Optional<?>) result : Optional.ofNullable(result));
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(ObjectUtils.identityToString(this));
        sb.append(": defining beans [");
        sb.append(StringUtils.collectionToCommaDelimitedString(this.beanDefinitionNames));
        sb.append("]; ");
        BeanFactory parent = getParentBeanFactory();
        if (parent == null) {
            sb.append("root of factory hierarchy");
        } else {
            sb.append("parent: ")
                    .append(ObjectUtils.identityToString(parent));
        }
        return sb.toString();
    }


    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        throw new NotSerializableException("DefaultListableBeanFactory itself is not deserializable - "
                + "just a SerializedBeanFactoryReference is");
    }

    protected Object writeReplace() throws ObjectStreamException {
        if (this.serializationId != null) {
            return new SerializedBeanFactoryReference(this.serializationId);
        } else {
            throw new NotSerializableException("DefaultListableBeanFactory has no serialization id");
        }
    }


    /**
     * Minimal id reference to the factory.
     * Resolved to the actual factory instance on deserialization.
     */
    private static class SerializedBeanFactoryReference implements Serializable {

        private final String id;

        public SerializedBeanFactoryReference(String id) {
            this.id = id;
        }

        private Object readResolve() {
            Reference<?> ref = serializableFactories.get(this.id);
            if (ref != null) {
                Object result = ref.get();
                if (result != null) {
                    return result;
                }
            }
            // Lenient fallback: dummy factory in case of original factory not found...
            DefaultListableBeanFactory dummyFactory = new DefaultListableBeanFactory();
            dummyFactory.serializationId = this.id;
            return dummyFactory;
        }
    }


    /**
     * A dependency descriptor marker for nested elements.
     */
    private static class NestedDependencyDescriptor extends DependencyDescriptor {

        public NestedDependencyDescriptor(DependencyDescriptor original) {
            super(original);
            increaseNestingLevel();
        }
    }


    /**
     * A dependency descriptor for a multi-element declaration with nested elements.
     */
    private static class MultiElementDescriptor extends NestedDependencyDescriptor {

        public MultiElementDescriptor(DependencyDescriptor original) {
            super(original);
        }
    }


    /**
     * A dependency descriptor marker for stream access to multiple elements.
     */
    private static class StreamDependencyDescriptor extends DependencyDescriptor {

        private final boolean ordered;

        public StreamDependencyDescriptor(DependencyDescriptor original, boolean ordered) {
            super(original);
            this.ordered = ordered;
        }

        public boolean isOrdered() {
            return this.ordered;
        }
    }


    private interface BeanObjectProvider<T> extends ObjectProvider<T>, Serializable {
    }


    /**
     * Serializable ObjectFactory/ObjectProvider for lazy resolution of a dependency.
     */
    private class DependencyObjectProvider implements BeanObjectProvider<Object> {

        private final DependencyDescriptor descriptor;

        private final boolean optional;

        @Nullable
        private final String beanName;

        public DependencyObjectProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
            this.descriptor = new NestedDependencyDescriptor(descriptor);
            this.optional = (this.descriptor.getDependencyType() == Optional.class);
            this.beanName = beanName;
        }

        @Override
        public Object getObject() throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName);
            } else {
                Object result = doResolveDependency(this.descriptor, this.beanName, null, null);
                if (result == null) {
                    throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
                }
                return result;
            }
        }

        @Override
        public Object getObject(final Object... args) throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName, args);
            } else {
                DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                    @Override
                    public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) {
                        return beanFactory.getBean(beanName, args);
                    }
                };
                Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
                if (result == null) {
                    throw new NoSuchBeanDefinitionException(this.descriptor.getResolvableType());
                }
                return result;
            }
        }

        @Override
        @Nullable
        public Object getIfAvailable() throws BeansException {
            try {
                if (this.optional) {
                    return createOptionalDependency(this.descriptor, this.beanName);
                } else {
                    DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                        @Override
                        public boolean isRequired() {
                            return false;
                        }
                    };
                    return doResolveDependency(descriptorToUse, this.beanName, null, null);
                }
            } catch (ScopeNotActiveException ex) {
                // Ignore resolved bean in non-active scope
                return null;
            }
        }

        @Override
        public void ifAvailable(Consumer<Object> dependencyConsumer) throws BeansException {
            Object dependency = getIfAvailable();
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                }
            }
        }

        @Override
        @Nullable
        public Object getIfUnique() throws BeansException {
            DependencyDescriptor descriptorToUse = new DependencyDescriptor(this.descriptor) {
                @Override
                public boolean isRequired() {
                    return false;
                }

                @Override
                @Nullable
                public Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) {
                    return null;
                }
            };
            try {
                if (this.optional) {
                    return createOptionalDependency(descriptorToUse, this.beanName);
                } else {
                    return doResolveDependency(descriptorToUse, this.beanName, null, null);
                }
            } catch (ScopeNotActiveException ex) {
                // Ignore resolved bean in non-active scope
                return null;
            }
        }

        @Override
        public void ifUnique(Consumer<Object> dependencyConsumer) throws BeansException {
            Object dependency = getIfUnique();
            if (dependency != null) {
                try {
                    dependencyConsumer.accept(dependency);
                } catch (ScopeNotActiveException ex) {
                    // Ignore resolved bean in non-active scope, even on scoped proxy invocation
                }
            }
        }

        @Nullable
        protected Object getValue() throws BeansException {
            if (this.optional) {
                return createOptionalDependency(this.descriptor, this.beanName);
            } else {
                return doResolveDependency(this.descriptor, this.beanName, null, null);
            }
        }

        @Override
        public Stream<Object> stream() {
            return resolveStream(false);
        }

        @Override
        public Stream<Object> orderedStream() {
            return resolveStream(true);
        }

        @SuppressWarnings("unchecked")
        private Stream<Object> resolveStream(boolean ordered) {
            DependencyDescriptor descriptorToUse = new StreamDependencyDescriptor(this.descriptor, ordered);
            Object result = doResolveDependency(descriptorToUse, this.beanName, null, null);
            return (result instanceof Stream ? (Stream<Object>) result : Stream.of(result));
        }
    }


    /**
     * Separate inner class for avoiding a hard dependency on the {@code javax.inject} API.
     * Actual {@code javax.inject.Provider} implementation is nested here in order to make it
     * invisible for Graal's introspection of DefaultListableBeanFactory's nested classes.
     */
    private class Jsr330Factory implements Serializable {

        public Object createDependencyProvider(DependencyDescriptor descriptor, @Nullable String beanName) {
            return new Jsr330Provider(descriptor, beanName);
        }

        private class Jsr330Provider extends DependencyObjectProvider implements Provider<Object> {

            public Jsr330Provider(DependencyDescriptor descriptor, @Nullable String beanName) {
                super(descriptor, beanName);
            }

            @Override
            @Nullable
            public Object get() throws BeansException {
                return getValue();
            }
        }
    }


    /**
     * An {@link org.springframework.core.OrderComparator.OrderSourceProvider} implementation
     * that is aware of the bean metadata of the instances to sort.
     * <p>Lookup for the method factory of an instance to sort, if any, and let the
     * comparator retrieve the {@link org.springframework.core.annotation.Order}
     * value defined on it. This essentially allows for the following construct:
     */
    private class FactoryAwareOrderSourceProvider implements OrderComparator.OrderSourceProvider {

        private final Map<Object, String> instancesToBeanNames;

        public FactoryAwareOrderSourceProvider(Map<Object, String> instancesToBeanNames) {
            this.instancesToBeanNames = instancesToBeanNames;
        }

        @Override
        @Nullable
        public Object getOrderSource(Object obj) {
            String beanName = this.instancesToBeanNames.get(obj);
            if (beanName == null || !containsBeanDefinition(beanName)) {
                return null;
            }
            RootBeanDefinition beanDefinition = getMergedLocalBeanDefinition(beanName);
            List<Object> sources = new ArrayList<>(2);
            Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
            if (factoryMethod != null) {
                sources.add(factoryMethod);
            }
            Class<?> targetType = beanDefinition.getTargetType();
            if (targetType != null && targetType != obj.getClass()) {
                sources.add(targetType);
            }
            return sources.toArray();
        }
    }

}
