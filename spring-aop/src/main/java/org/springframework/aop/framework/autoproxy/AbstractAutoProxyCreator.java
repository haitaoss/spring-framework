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

package org.springframework.aop.framework.autoproxy;

import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.*;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.AbstractBeanFactoryBasedTargetSource;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.core.SmartClassLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that wraps each eligible bean with an AOP proxy, delegating to specified interceptors
 * before invoking the bean itself.
 *
 * <p>This class distinguishes between "common" interceptors: shared for all proxies it
 * creates, and "specific" interceptors: unique per bean instance. There need not be any
 * common interceptors. If there are, they are set using the interceptorNames property.
 * As with {@link org.springframework.aop.framework.ProxyFactoryBean}, interceptors names
 * in the current factory are used rather than bean references to allow correct handling
 * of prototype advisors and interceptors: for example, to support stateful mixins.
 * Any advice type is supported for {@link #setInterceptorNames "interceptorNames"} entries.
 *
 * <p>Such auto-proxying is particularly useful if there's a large number of beans that
 * need to be wrapped with similar proxies, i.e. delegating to the same interceptors.
 * Instead of x repetitive proxy definitions for x target beans, you can register
 * one single such post processor with the bean factory to achieve the same effect.
 *
 * <p>Subclasses can apply any strategy to decide if a bean is to be proxied, e.g. by type,
 * by name, by definition details, etc. They can also return additional interceptors that
 * should just be applied to the specific bean instance. A simple concrete implementation is
 * {@link BeanNameAutoProxyCreator}, identifying the beans to be proxied via given names.
 *
 * <p>Any number of {@link TargetSourceCreator} implementations can be used to create
 * a custom target source: for example, to pool prototype objects. Auto-proxying will
 * occur even if there is no advice, as long as a TargetSourceCreator specifies a custom
 * {@link org.springframework.aop.TargetSource}. If there are no TargetSourceCreators set,
 * or if none matches, a {@link org.springframework.aop.target.SingletonTargetSource}
 * will be used by default to wrap the target bean instance.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Rob Harrop
 * @see #setInterceptorNames
 * @see #getAdvicesAndAdvisorsForBean
 * @see BeanNameAutoProxyCreator
 * @see DefaultAdvisorAutoProxyCreator
 * @since 13.10.2003
 */
@SuppressWarnings("serial")
public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {

    /**
     * Convenience constant for subclasses: Return value for "do not proxy".
     *
     * @see #getAdvicesAndAdvisorsForBean
     */
    @Nullable
    protected static final Object[] DO_NOT_PROXY = null;

    /**
     * Convenience constant for subclasses: Return value for
     * "proxy without additional interceptors, just the common ones".
     *
     * @see #getAdvicesAndAdvisorsForBean
     */
    protected static final Object[] PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS = new Object[0];


    /**
     * Logger available to subclasses.
     */
    protected final Log logger = LogFactory.getLog(getClass());

    /**
     * Default is global AdvisorAdapterRegistry.
     */
    private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();

    /**
     * Indicates whether or not the proxy should be frozen. Overridden from super
     * to prevent the configuration from becoming frozen too early.
     */
    private boolean freezeProxy = false;

    /**
     * Default is no common interceptors.
     */
    private String[] interceptorNames = new String[0];

    private boolean applyCommonInterceptorsFirst = true;

    @Nullable
    private TargetSourceCreator[] customTargetSourceCreators;

    @Nullable
    private BeanFactory beanFactory;

    /**
     * 已AOP代理对象集合。
     * 记录实例化前阶段 通过 {@link AbstractAutoProxyCreator#getCustomTargetSource(Class, String)} 创建了 TargetSource并生成了AOP切面的代理对象
     */
    private final Set<String> targetSourcedBeans = Collections.newSetFromMap(new ConcurrentHashMap<>(16));

    /**
     * 提前代理引用集合。记录一些那些bean执行了提前AOP
     */
    private final Map<Object, Object> earlyProxyReferences = new ConcurrentHashMap<>(16);

    /**
     * 记录AOP代理对象
     * <p>
     * key: cacheKey
     * value: proxy.getClass()
     */
    private final Map<Object, Class<?>> proxyTypes = new ConcurrentHashMap<>(16);

    /**
     * 这个集合，名字虽然叫做 advisedBeans ，但是他记录的信息 并不只是 advisor相关的bean(Advice、Pointcut、Advisor、AopInfrastructureBean 类型的)，
     * 还会记录满足和不满足 aop 的bean。反正记录的是 已经处理过的bean
     * <p>
     * key : 通过beanName生成的唯一值，或者是beanClass实例 {@link AbstractAutoProxyCreator#getCacheKey(Class, String)}
     * value：
     * - true：缓存的是符合 aop 增强的bean
     * - false： Advice、Pointcut、Advisor、AopInfrastructureBean 类型的bean；不符合 aop 增强的bean；
     */
    private final Map<Object, Boolean> advisedBeans = new ConcurrentHashMap<>(256);


    /**
     * Set whether or not the proxy should be frozen, preventing advice
     * from being added to it once it is created.
     * <p>Overridden from the super class to prevent the proxy configuration
     * from being frozen before the proxy is created.
     */
    @Override
    public void setFrozen(boolean frozen) {
        this.freezeProxy = frozen;
    }

    @Override
    public boolean isFrozen() {
        return this.freezeProxy;
    }

    /**
     * Specify the {@link AdvisorAdapterRegistry} to use.
     * <p>Default is the global {@link AdvisorAdapterRegistry}.
     *
     * @see org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry
     */
    public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
        this.advisorAdapterRegistry = advisorAdapterRegistry;
    }

    /**
     * Set custom {@code TargetSourceCreators} to be applied in this order.
     * If the list is empty, or they all return null, a {@link SingletonTargetSource}
     * will be created for each bean.
     * <p>Note that TargetSourceCreators will kick in even for target beans
     * where no advices or advisors have been found. If a {@code TargetSourceCreator}
     * returns a {@link TargetSource} for a specific bean, that bean will be proxied
     * in any case.
     * <p>{@code TargetSourceCreators} can only be invoked if this post processor is used
     * in a {@link BeanFactory} and its {@link BeanFactoryAware} callback is triggered.
     *
     * @param targetSourceCreators the list of {@code TargetSourceCreators}.
     *                             Ordering is significant: The {@code TargetSource} returned from the first matching
     *                             {@code TargetSourceCreator} (that is, the first that returns non-null) will be used.
     */
    public void setCustomTargetSourceCreators(TargetSourceCreator... targetSourceCreators) {
        this.customTargetSourceCreators = targetSourceCreators;
    }

    /**
     * Set the common interceptors. These must be bean names in the current factory.
     * They can be of any advice or advisor type Spring supports.
     * <p>If this property isn't set, there will be zero common interceptors.
     * This is perfectly valid, if "specific" interceptors such as matching
     * Advisors are all we want.
     */
    public void setInterceptorNames(String... interceptorNames) {
        this.interceptorNames = interceptorNames;
    }

    /**
     * Set whether the common interceptors should be applied before bean-specific ones.
     * Default is "true"; else, bean-specific interceptors will get applied first.
     */
    public void setApplyCommonInterceptorsFirst(boolean applyCommonInterceptorsFirst) {
        this.applyCommonInterceptorsFirst = applyCommonInterceptorsFirst;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * Return the owning {@link BeanFactory}.
     * May be {@code null}, as this post-processor doesn't need to belong to a bean factory.
     */
    @Nullable
    protected BeanFactory getBeanFactory() {
        return this.beanFactory;
    }


    @Override
    @Nullable
    public Class<?> predictBeanType(Class<?> beanClass, String beanName) {
        if (this.proxyTypes.isEmpty()) {
            return null;
        }
        Object cacheKey = getCacheKey(beanClass, beanName);
        return this.proxyTypes.get(cacheKey);
    }

    @Override
    @Nullable
    public Constructor<?>[] determineCandidateConstructors(Class<?> beanClass, String beanName) {
        return null;
    }

    @Override
    public Object getEarlyBeanReference(Object bean, String beanName) {
        Object cacheKey = getCacheKey(bean.getClass(), beanName);
        this.earlyProxyReferences.put(cacheKey, bean);
        return wrapIfNecessary(bean, beanName, cacheKey);
    }

    /**
     * 在Bean创建的生命周期会回调该方法，如果返回值不是null，
     * 会执行 {@link BeanPostProcessor#postProcessAfterInitialization(Object, String)}
     * 然后直接返回bean，不会进行后续bean生命周期的执行
     *
     * @param beanClass the class of the bean to be instantiated
     * @param beanName  the name of the bean
     * @return
     */
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
        // 获取key
        Object cacheKey = getCacheKey(beanClass, beanName);

        // beanName是空 或者 targetSourcedBeans(已AOP代理对象集合)中没有记录beanName
        if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
            // advisedBeans(增强bean集合) 中记录了，就返回null
            if (this.advisedBeans.containsKey(cacheKey)) {
                return null;
            }
            /**
             * 是基础设置类 或者 应该跳过 就返回null
             *
             * isInfrastructureClass：是这些 Advice、Pointcut、Advisor、AopInfrastructureBean 类型的子类
             * shouldSkip {@link AutoProxyUtils#isOriginalInstance(String, Class)}
             *  beanName是空 或者 beanName的长度不等于 类全名+.ORIGINAL  ---> false
             *  类全名开头 且 结尾是.ORIGINAL    ---> true
             *
             *  什么情况会设置这个 .ORIGINAL ???
             * */
            if (isInfrastructureClass(beanClass) || shouldSkip(beanClass, beanName)) {
                // 记录在 advisedBeans(增强bean集合) 中，？？？value是啥意思
                /**
                 * false 表示这个bean 是 advisor 相关的bean
                 * */
                this.advisedBeans.put(cacheKey, Boolean.FALSE);
                return null;
            }
        }

        /**
         * {@link AbstractAutoProxyCreator#customTargetSourceCreators} 属性不为null，就遍历 {@link TargetSourceCreator}
         * 执行 {@link TargetSourceCreator#getTargetSource(Class, String)} 创建 TargetSource，返回值不是null就直接return
         *
         * 注：TargetSource 封装了被代理对象，然后aop是对TargetSource进行代理
         *
         * TODOHAITAO: 2022/10/3 我猜测这里都是直接返回null的
         * */
        // Create proxy here if we have a custom TargetSource.
        // Suppresses unnecessary default instantiation of the target bean:
        // The TargetSource will handle target instances in a custom fashion.
        TargetSource targetSource = getCustomTargetSource(beanClass, beanName);
        if (targetSource != null) {
            // 有beanName
            if (StringUtils.hasLength(beanName)) {
                // 记录在 targetSourcedBeans(被代理对象集合) 中
                this.targetSourcedBeans.add(beanName);
            }
            /**
             * {@link AbstractAdvisorAutoProxyCreator#getAdvicesAndAdvisorsForBean(Class, String, TargetSource)}
             * 1. 会将容器中所有的 Advisor 都进行实例化，缓存起来。
             * 2. 按照 类匹配 或者 (类匹配+AspectJ表达式匹配)
             * 3. 返回满足条件的 Advisor
             * */
            Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
            /**
             * 创建代理对象
             * */
            Object proxy = createProxy(beanClass, beanName, specificInterceptors, targetSource);
            this.proxyTypes.put(cacheKey, proxy.getClass());
            // 返回创建好的代理对象，这里返回值不是null，所以会中断后续Bean的声明周期，也就是后面的填充bean、初始化等等操作都不会执行了
            return proxy;
        }

        return null;
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
        return pvs;  // skip postProcessPropertyValues
    }

    /**
     * Create a proxy with the configured interceptors if the bean is
     * identified as one to proxy by the subclass.
     *
     * @see #getAdvicesAndAdvisorsForBean
     */
    @Override
    public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
        if (bean != null) {
            Object cacheKey = getCacheKey(bean.getClass(), beanName);
            if (this.earlyProxyReferences.remove(cacheKey) != bean) {
                return wrapIfNecessary(bean, beanName, cacheKey);
            }
        }
        return bean;
    }


    /**
     * Build a cache key for the given bean class and bean name.
     * <p>Note: As of 4.2.3, this implementation does not return a concatenated
     * class/name String anymore but rather the most efficient cache key possible:
     * a plain bean name, prepended with {@link BeanFactory#FACTORY_BEAN_PREFIX}
     * in case of a {@code FactoryBean}; or if no bean name specified, then the
     * given bean {@code Class} as-is.
     *
     * @param beanClass the bean class
     * @param beanName  the bean name
     * @return the cache key for the given class and name
     */
    protected Object getCacheKey(Class<?> beanClass, @Nullable String beanName) {
        if (StringUtils.hasLength(beanName)) {
            return (FactoryBean.class.isAssignableFrom(beanClass) ?
                    BeanFactory.FACTORY_BEAN_PREFIX + beanName : beanName);
        } else {
            return beanClass;
        }
    }

    /**
     * Wrap the given bean if necessary, i.e. if it is eligible for being proxied.
     *
     * @param bean     the raw bean instance
     * @param beanName the name of the bean
     * @param cacheKey the cache key for metadata access
     * @return a proxy wrapping the bean, or the raw bean instance as-is
     */
    protected Object
    wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
        /**
         * 在 {@link AbstractAutoProxyCreator#postProcessBeforeInstantiation} 的时候就创建了代理对象，
         * 那就别搞了 直接返回入参bean
         * */
        if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
            return bean;
        }
        // 表示 adviseBean 已经创建了代理对象 或者是 infrastructureBean
        if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
            return bean;
        }
        // 是 Advice、Pointcut、Advisor、AopInfrastructureBean 类型的子类
        if (isInfrastructureClass(bean.getClass()) || shouldSkip(bean.getClass(), beanName)) {
            this.advisedBeans.put(cacheKey, Boolean.FALSE);
            return bean;
        }

        /**
         * 就是拿到和这个bean 匹配的 advisors 集合 信息。会触发 Advisor类型的实例化、@Aspect bean的实例化
         * {@link AbstractAdvisorAutoProxyCreator#getAdvicesAndAdvisorsForBean(Class, String, TargetSource)}
         * */
        // Create proxy if we have advice.
        Object[] specificInterceptors = getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
        if (specificInterceptors != DO_NOT_PROXY) {
            /**
             * 哈哈哈哈。记录一下这个 bean 满足aop规则，创建了代理对象
             * */
            this.advisedBeans.put(cacheKey, Boolean.TRUE);
            // 创建代理对象
            Object proxy = createProxy(bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
            this.proxyTypes.put(cacheKey, proxy.getClass());
            return proxy;
        }

        // 记录 虽然不是 advisor 但是也没有匹配 aop 规则的bean
        this.advisedBeans.put(cacheKey, Boolean.FALSE);
        return bean;
    }

    /**
     * Return whether the given bean class represents an infrastructure class
     * that should never be proxied.
     * <p>The default implementation considers Advices, Advisors and
     * AopInfrastructureBeans as infrastructure classes.
     *
     * @param beanClass the class of the bean
     * @return whether the bean represents an infrastructure class
     * @see org.aopalliance.aop.Advice
     * @see org.springframework.aop.Advisor
     * @see org.springframework.aop.framework.AopInfrastructureBean
     * @see #shouldSkip
     */
    protected boolean isInfrastructureClass(Class<?> beanClass) {
        boolean retVal = Advice.class.isAssignableFrom(beanClass) || Pointcut.class.isAssignableFrom(beanClass)
                || Advisor.class.isAssignableFrom(beanClass)
                || AopInfrastructureBean.class.isAssignableFrom(beanClass);
        if (retVal && logger.isTraceEnabled()) {
            logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.getName() + "]");
        }
        return retVal;
    }

    /**
     * Subclasses should override this method to return {@code true} if the
     * given bean should not be considered for auto-proxying by this post-processor.
     * <p>Sometimes we need to be able to avoid this happening, e.g. if it will lead to
     * a circular reference or if the existing target instance needs to be preserved.
     * This implementation returns {@code false} unless the bean name indicates an
     * "original instance" according to {@code AutowireCapableBeanFactory} conventions.
     *
     * @param beanClass the class of the bean
     * @param beanName  the name of the bean
     * @return whether to skip the given bean
     * @see org.springframework.beans.factory.config.AutowireCapableBeanFactory#ORIGINAL_INSTANCE_SUFFIX
     */
    protected boolean shouldSkip(Class<?> beanClass, String beanName) {
        return AutoProxyUtils.isOriginalInstance(beanName, beanClass);
    }

    /**
     * Create a target source for bean instances. Uses any TargetSourceCreators if set.
     * Returns {@code null} if no custom TargetSource should be used.
     * <p>This implementation uses the "customTargetSourceCreators" property.
     * Subclasses can override this method to use a different mechanism.
     *
     * @param beanClass the class of the bean to create a TargetSource for
     * @param beanName  the name of the bean
     * @return a TargetSource for this bean
     * @see #setCustomTargetSourceCreators
     */
    @Nullable
    protected TargetSource getCustomTargetSource(Class<?> beanClass, String beanName) {
        // We can't create fancy target sources for directly registered singletons.
        if (this.customTargetSourceCreators != null && this.beanFactory != null
                && this.beanFactory.containsBean(beanName)) {
            for (TargetSourceCreator tsc : this.customTargetSourceCreators) {
                TargetSource ts = tsc.getTargetSource(beanClass, beanName);
                if (ts != null) {
                    // Found a matching TargetSource.
                    if (logger.isTraceEnabled()) {
                        logger.trace("TargetSourceCreator [" + tsc + "] found custom TargetSource for bean with name '"
                                + beanName + "'");
                    }
                    return ts;
                }
            }
        }

        // No custom TargetSource found.
        return null;
    }

    /**
     * 为给定的bean创建代理对象，主要是通过ProxyFactory来创建代理对象，会有两个策略 cglib代理或者jdk代理，<br/>
     * Create an AOP proxy for the given bean.
     *
     * @param beanClass            the class of the bean
     * @param beanName             the name of the bean
     * @param specificInterceptors the set of interceptors that is
     *                             specific to this bean (may be empty, but not null)
     * @param targetSource         the TargetSource for the proxy,
     *                             already pre-configured to access the bean
     * @return the AOP proxy for the bean
     * @see #buildAdvisors
     */
    protected Object createProxy(Class<?> beanClass, @Nullable String beanName, @Nullable Object[] specificInterceptors, TargetSource targetSource) {

        if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
            //
            /**
             * 给beanName对应的BeanDefinition {@link AutoProxyUtils#ORIGINAL_TARGET_CLASS_ATTRIBUTE} 属性,为beanClass
             * 就是记录源对象是啥
             * */
            AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName, beanClass);
        }

        ProxyFactory proxyFactory = new ProxyFactory();
        /**
         * 关注这两个属性：proxyTargetClass、exposeProxy
         *
         * @EnableAspectJAutoProxy() 可以设置这两个属性值
         *  AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
         *  AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
         *
         * 属性 proxyTargetClass 在这里有用 {@link DefaultAopProxyFactory#createAopProxy(AdvisedSupport)}
         * 属性 exposeProxy 在这里有用 {@link CglibAopProxy#getCallbacks(Class)}
         * */
        proxyFactory.copyFrom(this);

        if (proxyFactory.isProxyTargetClass()) {
            /**
             * 显式处理 JDK 代理目标(用于引入通知场景)
             *
             * beanClass 是jdk代理产生的类
             * */
            // Explicit handling of JDK proxy targets (for introduction advice scenarios)
            if (Proxy.isProxyClass(beanClass)) {
                // Must allow for introductions; can't just set interfaces to the proxy's interfaces only.
                for (Class<?> ifc : beanClass.getInterfaces()) {
                    // 记录实现的接口
                    proxyFactory.addInterface(ifc);
                }
            }
        } else {
            /**
             * 就是检查BeanDefinition是否设置了属性
             *
             * @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
             * @Compoent
             * class A{}
             *
             * 这样子，shouldProxyTargetClass 就是 true
             * */
            //
            // No proxyTargetClass flag enforced, let's apply our default checks...
            if (shouldProxyTargetClass(beanClass, beanName)) {
                proxyFactory.setProxyTargetClass(true);
            } else {
                /**
                 * 记录 beanClass 实现的自定义接口
                 * */
                evaluateProxyInterfaces(beanClass, proxyFactory);
            }
        }

        /**
         * 主要是将 {@link AbstractAutoProxyCreator#interceptorNames} 的bean，作为通用的 Advisor。
         * 通用的 Advisor 的优先级比specificInterceptors 要先执行
         *
         * 子类可以回调 {@link AbstractAutoProxyCreator#setInterceptorNames(String...)} 进行设置
         * */
        Advisor[] advisors = buildAdvisors(beanName, specificInterceptors);
        // 设置 advisor
        proxyFactory.addAdvisors(advisors);
        // 被代理对象。我们的普通bean 被装饰成 TargetSource 了
        proxyFactory.setTargetSource(targetSource);

        // 预留模板方法。空方法
        customizeProxyFactory(proxyFactory);

        /**
         * 是否冻结代理，就是是否支持修改 advisors
         * 看这些地方,都会校验这个属性
         *  新增 {@link AdvisedSupport#addAdvisors(Collection)}
         *  删除 {@link AdvisedSupport#removeAdvisor(int)}
         * */
        proxyFactory.setFrozen(this.freezeProxy);
        if (advisorsPreFiltered()) {
            proxyFactory.setPreFiltered(true);
        }

        // Use original ClassLoader if bean class not locally loaded in overriding class loader
        ClassLoader classLoader = getProxyClassLoader();
        if (classLoader instanceof SmartClassLoader && classLoader != beanClass.getClassLoader()) {
            classLoader = ((SmartClassLoader) classLoader).getOriginalClassLoader();
        }
        /**
         * 创建代理对象
         *  {@link ProxyFactory#getProxy(ClassLoader)}
         *  {@link ProxyCreatorSupport#createAopProxy()}
         *
         *
         * 创建 AopProxy {@link DefaultAopProxyFactory#createAopProxy(AdvisedSupport)}
         *      - 简单来说，被代理对象 不是接口 且 不是Proxy的子类 且 {@link AdvisedSupport#getProxiedInterfaces()}至多只有一个SpringProxy类型的接口 就创建 `new ObjenesisCglibAopProxy(config);`
         *      - 否则创建 `new JdkDynamicAopProxy(config);`
         *      注：config 参数其实就是 ProxyFactory对象
         *
         * 使用 AopProxy 创建代理对象 {@link AopProxy#getProxy(ClassLoader)}
         *      {@link ObjenesisCglibAopProxy#getProxy(ClassLoader)}
         *      {@link JdkDynamicAopProxy#getProxy(ClassLoader)}
         *
         * ObjenesisCglibAopProxy 增强逻辑实现原理 {@link ObjenesisCglibAopProxy#getProxy(ClassLoader)}
         *      Enhancer enhancer = new Enhancer();
         *      enhancer.setCallbacks({@link CglibAopProxy#getCallbacks(Class)}); // 执行方法会回调callback
         *          注：会设置  `new DynamicAdvisedInterceptor(this.advised);` callback，this 就是 ObjenesisCglibAopProxy，而其{@link CglibAopProxy#advised}属性 其实就是 ProxyFactory 从而可以拿到Advisors
         *      enhancer.setCallbackFilter(ProxyCallbackFilter); // 返回要执行的 callback 的索引
         *
         *      执行代理对象的方式时会执行 {@link CglibAopProxy.ProxyCallbackFilter#accept(Method)}
         *          就是 除了 finalize、equals、hashcode 等，都会执行 DynamicAdvisedInterceptor 这个callback
         *      也就是执行 {@link CglibAopProxy.DynamicAdvisedInterceptor#intercept(Object, Method, Object[], MethodProxy)}
         *          1. exposeProxy 属性是 true，往 ThreadLocal中记录当前代理对象  `oldProxy=AopContext.setCurrentProxy(proxy)`
         *          2. 获取被代理对象 {@link TargetSource#getTarget()}。这里也是很细节，因为TargetSource实例是这个类型的 {@link AbstractBeanFactoryBasedTargetSource}，所以每次获取target都是从IOC容器中拿，
         *              也就是说 如果bean是多例的，每次执行都会创建出新的对象。
         *          3. 根据method得到对应的拦截器链 {@link AdvisedSupport#getInterceptorsAndDynamicInterceptionAdvice(Method, Class)}
         *              拦截器链是空：反射执行
         *              拦截器链不是空：将拦截器链装饰成 CglibMethodInvocation ，然后执行{@link CglibAopProxy.CglibMethodInvocation#proceed()}，其实就是执行其父类 {@link ReflectiveMethodInvocation#proceed()}
         *              注：拦截器链就是 method 匹配到的 advisor 解析的结果，可以看这里 {@link DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice(Advised, Method, Class)}
         *          4. exposeProxy 属性是 true，恢复之前的值  `AopContext.setCurrentProxy(oldProxy);`
         *
         * JdkDynamicAopProxy 增强逻辑的实现 {@link JdkDynamicAopProxy#getProxy(ClassLoader)}
         *      `Proxy.newProxyInstance(classLoader, this.proxiedInterfaces, this);` 可以知道 第三个参数(InvocationHandler) 是 this，也就是 JdkDynamicAopProxy，而其{@link JdkDynamicAopProxy#advised}属性 其实就是 ProxyFactory从而可以拿到Advisors
         *      所以执行代理对象的方式时会执行 {@link JdkDynamicAopProxy#invoke(Object, Method, Object[])}
         *          1. 是 equals(子类没有重写的情况下)、hashCode(子类没有重写的情况下)、DecoratingProxy.class 的方法、Advised.class 的方法
         *              直接反射执行
         *          2. exposeProxy 属性是 true，往 ThreadLocal中记录当前代理对象  `oldProxy=AopContext.setCurrentProxy(proxy)`
         *          3. 根据method得到对应的拦截器链 {@link AdvisedSupport#getInterceptorsAndDynamicInterceptionAdvice(Method, Class)}
         *              拦截器链是空：反射执行
         *              拦截器链不是空：将拦截器链装饰成 ReflectiveMethodInvocation ，然后执行{@link ReflectiveMethodInvocation#proceed()}
         *              注：拦截器链就是 method 匹配到的 advisor 解析的结果，可以看这里 {@link DefaultAdvisorChainFactory#getInterceptorsAndDynamicInterceptionAdvice(Advised, Method, Class)}
         *          4. exposeProxy 属性是 true，恢复之前的值  `AopContext.setCurrentProxy(oldProxy);`
         * */
        return proxyFactory.getProxy(classLoader);
    }

    /**
     * Determine whether the given bean should be proxied with its target class rather than its interfaces.
     * <p>Checks the {@link AutoProxyUtils#PRESERVE_TARGET_CLASS_ATTRIBUTE "preserveTargetClass" attribute}
     * of the corresponding bean definition.
     *
     * @param beanClass the class of the bean
     * @param beanName  the name of the bean
     * @return whether the given bean should be proxied with its target class
     * @see AutoProxyUtils#shouldProxyTargetClass
     */
    protected boolean shouldProxyTargetClass(Class<?> beanClass, @Nullable String beanName) {
        return (this.beanFactory instanceof ConfigurableListableBeanFactory
                && AutoProxyUtils.shouldProxyTargetClass((ConfigurableListableBeanFactory) this.beanFactory, beanName));
    }

    /**
     * Return whether the Advisors returned by the subclass are pre-filtered
     * to match the bean's target class already, allowing the ClassFilter check
     * to be skipped when building advisors chains for AOP invocations.
     * <p>Default is {@code false}. Subclasses may override this if they
     * will always return pre-filtered Advisors.
     *
     * @return whether the Advisors are pre-filtered
     * @see #getAdvicesAndAdvisorsForBean
     * @see org.springframework.aop.framework.Advised#setPreFiltered
     */
    protected boolean advisorsPreFiltered() {
        return false;
    }

    /**
     * Determine the advisors for the given bean, including the specific interceptors
     * as well as the common interceptor, all adapted to the Advisor interface.
     *
     * @param beanName             the name of the bean
     * @param specificInterceptors the set of interceptors that is
     *                             specific to this bean (may be empty, but not null)
     * @return the list of Advisors for the given bean
     */
    protected Advisor[] buildAdvisors(@Nullable String beanName, @Nullable Object[] specificInterceptors) {
        /**
         * 处理 {@link AbstractAutoProxyCreator#interceptorNames} 这个属性
         *
         * 注：但是没啥用，我看这个属性是空的。子类需要调用 {@link AbstractAutoProxyCreator#setInterceptorNames(String...)} 才能设置属性值
         * */
        // Handle prototypes correctly...
        Advisor[] commonInterceptors = resolveInterceptorNames();

        List<Object> allInterceptors = new ArrayList<>();
        if (specificInterceptors != null) {
            if (specificInterceptors.length > 0) {
                // specificInterceptors may equal PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
                allInterceptors.addAll(Arrays.asList(specificInterceptors));
            }
            if (commonInterceptors.length > 0) {
                if (this.applyCommonInterceptorsFirst) {
                    allInterceptors.addAll(0, Arrays.asList(commonInterceptors));
                } else {
                    allInterceptors.addAll(Arrays.asList(commonInterceptors));
                }
            }
        }
        if (logger.isTraceEnabled()) {
            int nrOfCommonInterceptors = commonInterceptors.length;
            int nrOfSpecificInterceptors = (specificInterceptors != null ? specificInterceptors.length : 0);
            logger.trace("Creating implicit proxy for bean '" + beanName + "' with " + nrOfCommonInterceptors
                    + " common interceptors and " + nrOfSpecificInterceptors + " specific interceptors");
        }

        Advisor[] advisors = new Advisor[allInterceptors.size()];
        for (int i = 0; i < allInterceptors.size(); i++) {
            /**
             * 装饰一下，就是如果是 Advice 类型的，需要装饰成 DefaultPointcutAdvisor
             * */
            advisors[i] = this.advisorAdapterRegistry.wrap(allInterceptors.get(i));
        }
        return advisors;
    }

    /**
     * Resolves the specified interceptor names to Advisor objects.
     *
     * @see #setInterceptorNames
     */
    private Advisor[] resolveInterceptorNames() {
        BeanFactory bf = this.beanFactory;
        ConfigurableBeanFactory cbf = (bf instanceof ConfigurableBeanFactory ? (ConfigurableBeanFactory) bf : null);
        List<Advisor> advisors = new ArrayList<>();
        /**
         * interceptorNames 默认是空的
         * */
        for (String beanName : this.interceptorNames) {
            if (cbf == null || !cbf.isCurrentlyInCreation(beanName)) {
                Assert.state(bf != null, "BeanFactory required for resolving interceptor names");
                Object next = bf.getBean(beanName);
                /**
                 * 从容器中找到该bean，然后装饰一下
                 * */
                advisors.add(this.advisorAdapterRegistry.wrap(next));
            }
        }
        return advisors.toArray(new Advisor[0]);
    }

    /**
     * Subclasses may choose to implement this: for example,
     * to change the interfaces exposed.
     * <p>The default implementation is empty.
     *
     * @param proxyFactory a ProxyFactory that is already configured with
     *                     TargetSource and interfaces and will be used to create the proxy
     *                     immediately after this method returns
     */
    protected void customizeProxyFactory(ProxyFactory proxyFactory) {
    }


    /**
     * Return whether the given bean is to be proxied, what additional
     * advices (e.g. AOP Alliance interceptors) and advisors to apply.
     *
     * @param beanClass          the class of the bean to advise
     * @param beanName           the name of the bean
     * @param customTargetSource the TargetSource returned by the
     *                           {@link #getCustomTargetSource} method: may be ignored.
     *                           Will be {@code null} if no custom target source is in use.
     * @return an array of additional interceptors for the particular bean;
     * or an empty array if no additional interceptors but just the common ones;
     * or {@code null} if no proxy at all, not even with the common interceptors.
     * See constants DO_NOT_PROXY and PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS.
     * @throws BeansException in case of errors
     * @see #DO_NOT_PROXY
     * @see #PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS
     */
    @Nullable
    protected abstract Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource customTargetSource) throws BeansException;
}
