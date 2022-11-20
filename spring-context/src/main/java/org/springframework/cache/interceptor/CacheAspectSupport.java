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

package org.springframework.cache.interceptor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.util.function.SupplierUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Base class for caching aspects, such as the {@link CacheInterceptor} or an
 * AspectJ aspect.
 *
 * <p>This enables the underlying Spring caching infrastructure to be used easily
 * to implement an aspect for any aspect system.
 *
 * <p>Subclasses are responsible for calling relevant methods in the correct order.
 *
 * <p>Uses the <b>Strategy</b> design pattern. A {@link CacheOperationSource} is
 * used for determining caching operations, a {@link KeyGenerator} will build the
 * cache keys, and a {@link CacheResolver} will resolve the actual cache(s) to use.
 *
 * <p>Note: A cache aspect is serializable but does not perform any actual caching
 * after deserialization.
 *
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @author Stephane Nicoll
 * @since 3.1
 */
public abstract class CacheAspectSupport extends AbstractCacheInvoker
        implements BeanFactoryAware, InitializingBean, SmartInitializingSingleton {

    protected final Log logger = LogFactory.getLog(getClass());

    private final Map<CacheOperationCacheKey, CacheOperationMetadata> metadataCache = new ConcurrentHashMap<>(1024);

    private final CacheOperationExpressionEvaluator evaluator = new CacheOperationExpressionEvaluator();

    @Nullable
    private CacheOperationSource cacheOperationSource;

    /**
     * 默认是 SimpleKeyGenerator
     */
    private SingletonSupplier<KeyGenerator> keyGenerator = SingletonSupplier.of(SimpleKeyGenerator::new);

    /**
     * 默认是 null
     */
    @Nullable
    private SingletonSupplier<CacheResolver> cacheResolver;

    @Nullable
    private BeanFactory beanFactory;

    private boolean initialized = false;


    /**
     * Configure this aspect with the given error handler, key generator and cache resolver/manager
     * suppliers, applying the corresponding default if a supplier is not resolvable.
     * @since 5.1
     */
    public void configure(
            @Nullable Supplier<CacheErrorHandler> errorHandler, @Nullable Supplier<KeyGenerator> keyGenerator,
            @Nullable Supplier<CacheResolver> cacheResolver, @Nullable Supplier<CacheManager> cacheManager) {

        this.errorHandler = new SingletonSupplier<>(errorHandler, SimpleCacheErrorHandler::new);
        this.keyGenerator = new SingletonSupplier<>(keyGenerator, SimpleKeyGenerator::new);
        this.cacheResolver = new SingletonSupplier<>(cacheResolver,
                () -> SimpleCacheResolver.of(SupplierUtils.resolve(cacheManager)));
    }


    /**
     * Set one or more cache operation sources which are used to find the cache
     * attributes. If more than one source is provided, they will be aggregated
     * using a {@link CompositeCacheOperationSource}.
     * @see #setCacheOperationSource
     */
    public void setCacheOperationSources(CacheOperationSource... cacheOperationSources) {
        Assert.notEmpty(cacheOperationSources, "At least 1 CacheOperationSource needs to be specified");
        this.cacheOperationSource = (cacheOperationSources.length > 1 ?
                new CompositeCacheOperationSource(cacheOperationSources) : cacheOperationSources[0]);
    }

    /**
     * Set the CacheOperationSource for this cache aspect.
     * @since 5.1
     * @see #setCacheOperationSources
     */
    public void setCacheOperationSource(@Nullable CacheOperationSource cacheOperationSource) {
        this.cacheOperationSource = cacheOperationSource;
    }

    /**
     * Return the CacheOperationSource for this cache aspect.
     */
    @Nullable
    public CacheOperationSource getCacheOperationSource() {
        return this.cacheOperationSource;
    }

    /**
     * Set the default {@link KeyGenerator} that this cache aspect should delegate to
     * if no specific key generator has been set for the operation.
     * <p>The default is a {@link SimpleKeyGenerator}.
     */
    public void setKeyGenerator(KeyGenerator keyGenerator) {
        this.keyGenerator = SingletonSupplier.of(keyGenerator);
    }

    /**
     * Return the default {@link KeyGenerator} that this cache aspect delegates to.
     */
    public KeyGenerator getKeyGenerator() {
        return this.keyGenerator.obtain();
    }

    /**
     * Set the default {@link CacheResolver} that this cache aspect should delegate
     * to if no specific cache resolver has been set for the operation.
     * <p>The default resolver resolves the caches against their names and the
     * default cache manager.
     * @see #setCacheManager
     * @see SimpleCacheResolver
     */
    public void setCacheResolver(@Nullable CacheResolver cacheResolver) {
        this.cacheResolver = SingletonSupplier.ofNullable(cacheResolver);
    }

    /**
     * Return the default {@link CacheResolver} that this cache aspect delegates to.
     */
    @Nullable
    public CacheResolver getCacheResolver() {
        return SupplierUtils.resolve(this.cacheResolver);
    }

    /**
     * Set the {@link CacheManager} to use to create a default {@link CacheResolver}.
     * Replace the current {@link CacheResolver}, if any.
     * @see #setCacheResolver
     * @see SimpleCacheResolver
     */
    public void setCacheManager(CacheManager cacheManager) {
        this.cacheResolver = SingletonSupplier.of(new SimpleCacheResolver(cacheManager));
    }

    /**
     * Set the containing {@link BeanFactory} for {@link CacheManager} and other
     * service lookups.
     * @since 4.3
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }


    @Override
    public void afterPropertiesSet() {
        Assert.state(getCacheOperationSource() != null, "The 'cacheOperationSources' property is required: " +
                "If there are no cacheable methods, then don't use a cache aspect.");
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (getCacheResolver() == null) {
            // Lazily initialize cache resolver via default cache manager...
            Assert.state(this.beanFactory != null, "CacheResolver or BeanFactory must be set on cache aspect");
            try {
                setCacheManager(this.beanFactory.getBean(CacheManager.class));
            } catch (NoUniqueBeanDefinitionException ex) {
                throw new IllegalStateException("No CacheResolver specified, and no unique bean of type " +
                        "CacheManager found. Mark one as primary or declare a specific CacheManager to use.", ex);
            } catch (NoSuchBeanDefinitionException ex) {
                throw new IllegalStateException("No CacheResolver specified, and no bean of type CacheManager found. " +
                        "Register a CacheManager bean or remove the @EnableCaching annotation from your configuration.", ex);
            }
        }
        this.initialized = true;
    }


    /**
     * Convenience method to return a String representation of this Method
     * for use in logging. Can be overridden in subclasses to provide a
     * different identifier for the given method.
     * @param method the method we're interested in
     * @param targetClass class the method is on
     * @return log message identifying this method
     * @see org.springframework.util.ClassUtils#getQualifiedMethodName
     */
    protected String methodIdentification(Method method, Class<?> targetClass) {
        Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
        return ClassUtils.getQualifiedMethodName(specificMethod);
    }

    protected Collection<? extends Cache> getCaches(
            CacheOperationInvocationContext<CacheOperation> context, CacheResolver cacheResolver) {

        // 就是通过注解写的 name 从 CacheManager 中获取 Cache 实例
        Collection<? extends Cache> caches = cacheResolver.resolveCaches(context);

        // 不能没有
        if (caches.isEmpty()) {
            throw new IllegalStateException("No cache could be resolved for '" +
                    context.getOperation() + "' using resolver '" + cacheResolver +
                    "'. At least one cache should be provided per cache operation.");
        }
        return caches;
    }

    protected CacheOperationContext getOperationContext(
            CacheOperation operation, Method method, Object[] args, Object target, Class<?> targetClass) {
        /**
         * 将 CacheOperation 装饰成 CacheOperationMetadata 对象
         * 该对象的关键属性：KeyGenerator、CacheResolver，而CacheResolver里面组合了 CacheManager
         *
         * 注：实例化 CacheOperationMetadata 会检验容器中是否有 CacheManager
         * */
        CacheOperationMetadata metadata = getCacheOperationMetadata(operation, method, targetClass);
        /**
         * 装饰成 CacheOperationContext
         * 主要是拿到 CacheOperation 对应的 Cache 和 将方法参数铺平(就是存在可变参数，就将可变参数铺平)
         * */
        return new CacheOperationContext(metadata, args, target);
    }

    /**
     * Return the {@link CacheOperationMetadata} for the specified operation.
     * <p>Resolve the {@link CacheResolver} and the {@link KeyGenerator} to be
     * used for the operation.
     * @param operation the operation
     * @param method the method on which the operation is invoked
     * @param targetClass the target type
     * @return the resolved metadata for the operation
     */
    protected CacheOperationMetadata getCacheOperationMetadata(
            CacheOperation operation, Method method, Class<?> targetClass) {

        // 生成key
        CacheOperationCacheKey cacheKey = new CacheOperationCacheKey(operation, method, targetClass);
        // 读缓存
        CacheOperationMetadata metadata = this.metadataCache.get(cacheKey);
        if (metadata == null) {
            KeyGenerator operationKeyGenerator;
            if (StringUtils.hasText(operation.getKeyGenerator())) {
                // 从容器中获取 KeyGenerator
                operationKeyGenerator = getBean(operation.getKeyGenerator(), KeyGenerator.class);
            } else {
                // 获取默认的 默认是 SimpleKeyGenerator
                operationKeyGenerator = getKeyGenerator();
            }
            CacheResolver operationCacheResolver;
            if (StringUtils.hasText(operation.getCacheResolver())) {
                // 从容器中获取 CacheResolver
                operationCacheResolver = getBean(operation.getCacheResolver(), CacheResolver.class);
            } else if (StringUtils.hasText(operation.getCacheManager())) {
                // 从容器中获取 CacheManager
                CacheManager cacheManager = getBean(operation.getCacheManager(), CacheManager.class);
                // 将 CacheManager 装饰成 SimpleCacheResolver
                operationCacheResolver = new SimpleCacheResolver(cacheManager);
            } else {
                /**
                 * 啥都没得，就获取默认的。
                 *
                 * 通过 CachingConfigurer 类型的bean设置的，如果设置了 cacheResolver 就返回，没有就判断有cacheManager，就装饰成SimpleCacheResolver返回
                 *   {@link CachingConfigurer#cacheResolver()}
                 *   {@link CachingConfigurer#cacheManager()}
                 *
                 * Tips：也就是说CachingConfigurer必须得设置 CacheResolver或者CacheManager 其中一个，否则这里拿不到
                 * */
                operationCacheResolver = getCacheResolver();
                /**
                 * 非空校验
                 *
                 * 也就是说CachingConfigurer必须得设置 CacheResolver或者CacheManager 其中一个，否则到这一步就报错
                 * */
                Assert.state(operationCacheResolver != null, "No CacheResolver/CacheManager set");
            }
            metadata = new CacheOperationMetadata(operation, method, targetClass,
                    operationKeyGenerator, operationCacheResolver);
            // 缓存起来
            this.metadataCache.put(cacheKey, metadata);
        }
        return metadata;
    }

    /**
     * Return a bean with the specified name and type. Used to resolve services that
     * are referenced by name in a {@link CacheOperation}.
     * @param beanName the name of the bean, as defined by the operation
     * @param expectedType type for the bean
     * @return the bean matching that name
     * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException if such bean does not exist
     * @see CacheOperation#getKeyGenerator()
     * @see CacheOperation#getCacheManager()
     * @see CacheOperation#getCacheResolver()
     */
    protected <T> T getBean(String beanName, Class<T> expectedType) {
        if (this.beanFactory == null) {
            throw new IllegalStateException(
                    "BeanFactory must be set on cache aspect for " + expectedType.getSimpleName() + " retrieval");
        }
        return BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.beanFactory, expectedType, beanName);
    }

    /**
     * Clear the cached metadata.
     */
    protected void clearMetadataCache() {
        this.metadataCache.clear();
        this.evaluator.clear();
    }

    @Nullable
    protected Object execute(CacheOperationInvoker invoker, Object target, Method method, Object[] args) {
        /**
         * 这个bean实例化完就会设置为true
         * {@link CacheAspectSupport#afterSingletonsInstantiated()}
         * */
        // Check whether aspect is enabled (to cope with cases where the AJ is pulled in automatically)
        if (this.initialized) {
            // 被代理类
            Class<?> targetClass = getTargetClass(target);
            // 拿到解析缓存注解的工具
            CacheOperationSource cacheOperationSource = getCacheOperationSource();
            if (cacheOperationSource != null) {
                // 返回的是方法或者类上 @Cacheable、@CacheEvict、@CachePut 的解析结果
                Collection<CacheOperation> operations = cacheOperationSource.getCacheOperations(method, targetClass);
                // 不是空，表示方法上有缓存注解
                if (!CollectionUtils.isEmpty(operations)) {
                    /**
                     * 将 operations、方法、方法参数、执行方法的对象和执行方法的类型 装饰成 CacheOperationContexts 对象
                     * 然后 invoke
                     * */
                    return execute(invoker, method,
                            new CacheOperationContexts(operations, method, args, target, targetClass));
                }
            }
        }

        return invoker.invoke();
    }

    /**
     * Execute the underlying operation (typically in case of cache miss) and return
     * the result of the invocation. If an exception occurs it will be wrapped in a
     * {@link CacheOperationInvoker.ThrowableWrapper}: the exception can be handled
     * or modified but it <em>must</em> be wrapped in a
     * {@link CacheOperationInvoker.ThrowableWrapper} as well.
     * @param invoker the invoker handling the operation being cached
     * @return the result of the invocation
     * @see CacheOperationInvoker#invoke()
     */
    @Nullable
    protected Object invokeOperation(CacheOperationInvoker invoker) {
        return invoker.invoke();
    }

    private Class<?> getTargetClass(Object target) {
        return AopProxyUtils.ultimateTargetClass(target);
    }

    @Nullable
    private Object execute(final CacheOperationInvoker invoker, Method method, CacheOperationContexts contexts) {
        /**
         * 是同步的。就是 @Cacheable(sync = true)
         * */
        // Special handling of synchronized invocation
        if (contexts.isSynchronized()) {
            // 同步的方法上只允许有一个 缓存注解，所以拿第一个就行了
            CacheOperationContext context = contexts.get(CacheableOperation.class).iterator().next();
            /**
             * 条件通过。
             * 有设置 @Cacheable(condition = "#root.methodName.startsWith('x')") 就解析SpEL看看结果是ture就是通过
             * 没有设置condition属性，就直接是true
             *
             * 很简单，就是将 方法的参数和一些固定参数(method、target、targetClass、args、caches) 构造出 EvaluationContext 然后使用 SpelExpressionParser 解析 SpEL表达式
             *      {@link CacheOperationContext#isConditionPassing(Object)}
             * */
            if (isConditionPassing(context, CacheOperationExpressionEvaluator.NO_RESULT)) {
                /**
                 * 生成key。
                 * 如果有设置 @Cacheable(key = "#p1")， SpEL的值+Method+Target生成的key 作为缓存的key
                 * 没有指定key属性，就根据{@link KeyGenerator#generate(Object, Method, Object...)} 返回值作为缓存的key
                 * */
                Object key = generateKey(context, CacheOperationExpressionEvaluator.NO_RESULT);
                // 同步的方法上只允许有一个 缓存注解，所以拿第一个Cache实例就行了
                Cache cache = context.getCaches().iterator().next();
                try {
                    /**
                     * handleSynchronizedGet 就是 使用 key从Cache中获取，获取不到就invoke方法获取值，会将方法返回值值存到 Cache 中。
                     *
                     * 存入Cache的细节：
                     *  1. 如果方法返回值是Optional类型的，会 {@link Optional#get()} 存到缓存
                     *  2. 如果Cache配了序列化器，会将值序列化在存到缓存中，获取的时候也会判断是否有序列化器 有就反序列化值
                     *
                     * wrapCacheValue 就是如果方法的返回值是 Optional 类型的，就包装一下
                     *
                     * 注：并没有看到具体的同步操作，是不是假的同步呀 能不能同步 还得看具体的 {@link Cache#get(Object)} 如何实现的
                     *      莫非，所谓的同步就是校验 方法上只能有一个  @Cacheable 注解？？？
                     *      看了官方文档，这个同步的实现得看具体的Cache实例 https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#cache-annotations-cacheable-synchronized
                     * */
                    return wrapCacheValue(method, handleSynchronizedGet(invoker, key, cache));
                } catch (Cache.ValueRetrievalException ex) {
                    // Directly propagate ThrowableWrapper from the invoker,
                    // or potentially also an IllegalArgumentException etc.
                    ReflectionUtils.rethrowRuntimeException(ex.getCause());
                }
            } else {
                // 不符合缓存条件，反射调用方法返回
                // No caching required, only call the underlying method
                return invokeOperation(invoker);
            }
        }

        /**
         * 清空缓存，就看是根据key删除 还是 直接清空整个Cache
         * 比如 @CacheEvict(allEntries = true) 就是清空整个缓存
         * */
        // Process any early evictions
        processCacheEvicts(contexts.get(CacheEvictOperation.class), true,
                CacheOperationExpressionEvaluator.NO_RESULT);
        /**
         * 就是遍历 CacheOperationContext，条件通过的，在遍历 Cache 根据key获取缓存值，找到就返回
         * */
        // Check if we have a cached item matching the conditions
        Cache.ValueWrapper cacheHit = findCachedItem(contexts.get(CacheableOperation.class));

        /**
         * 就是没有缓存的值，那就构造 CachePutRequest ，就是用来将 方法返回值 存到 Cache中的
         * {@link CachePutRequest#apply(Object)}
         *
         * 该属性是记录符合条件的 @CachePut + @Cacheable
         * */
        // Collect puts from any @Cacheable miss, if no cached item is found
        List<CachePutRequest> cachePutRequests = new ArrayList<>();
        if (cacheHit == null) {
            // 收集满足 condition 的 CacheableOperation
            collectPutRequests(contexts.get(CacheableOperation.class),
                    CacheOperationExpressionEvaluator.NO_RESULT, cachePutRequests);
        }

        Object cacheValue;
        Object returnValue;

        /**
         * 有缓存 且 没有符合条件的的@CachePut
         *
         * 那就不需要更新，那就直接拿缓存的值就行了
         * */
        if (cacheHit != null && !hasCachePut(contexts)) {
            // 拿到缓存的值
            // If there are no put requests, just use the cache hit
            cacheValue = cacheHit.get();
            // 是否需要装饰为Optional对象
            returnValue = wrapCacheValue(method, cacheValue);
        } else {
            // 放行方法，拿到返回值
            // Invoke the method if we don't have a cache hit
            returnValue = invokeOperation(invoker);
            // 是否需要解构Optional对象，拿到其值
            cacheValue = unwrapReturnValue(returnValue);
        }

        /**
         * 收集符合条件的@CachePut
         * */
        // Collect any explicit @CachePuts
        collectPutRequests(contexts.get(CachePutOperation.class), cacheValue, cachePutRequests);

        // Process any collected put requests, either from @CachePut or a @Cacheable miss
        for (CachePutRequest cachePutRequest : cachePutRequests) {
            /**
             * 就是就方法返回值 设置到 Cache 中,此时会进行 unless 的解析判断
             * {@link CacheOperationContext#canPutToCache(Object)}
             * */
            cachePutRequest.apply(cacheValue);
        }


        // 清空缓存
        // Process any late evictions
        processCacheEvicts(contexts.get(CacheEvictOperation.class), false, cacheValue);

        // 返回
        return returnValue;
    }

    @Nullable
    private Object handleSynchronizedGet(CacheOperationInvoker invoker, Object key, Cache cache) {
        InvocationAwareResult invocationResult = new InvocationAwareResult();
        /**
         * 这里就看你写的 Cache 实现类的方法咯，主要是对值的序列化处理
         * {@link ConcurrentMapCache#get(Object, Callable)}
         * */
        Object result = cache.get(key, () -> {
            // 缓存没有执行
            invocationResult.invoked = true;
            if (logger.isTraceEnabled()) {
                // 打印 没命中缓存的日志
                logger.trace("No cache entry for key '" + key + "' in cache " + cache.getName());
            }
            /**
             * 结构返回值，若返回值是Optional类型的，就 {@link Optional#get()}
             * */
            return unwrapReturnValue(invokeOperation(invoker));
        });
        if (!invocationResult.invoked && logger.isTraceEnabled()) {
            // 打印 命中缓存的日志
            logger.trace("Cache entry for key '" + key + "' found in cache '" + cache.getName() + "'");
        }
        // 返回结果
        return result;
    }

    @Nullable
    private Object wrapCacheValue(Method method, @Nullable Object cacheValue) {
        if (method.getReturnType() == Optional.class &&
                (cacheValue == null || cacheValue.getClass() != Optional.class)) {
            return Optional.ofNullable(cacheValue);
        }
        return cacheValue;
    }

    @Nullable
    private Object unwrapReturnValue(@Nullable Object returnValue) {
        return ObjectUtils.unwrapOptional(returnValue);
    }

    private boolean hasCachePut(CacheOperationContexts contexts) {
        // Evaluate the conditions *without* the result object because we don't have it yet...
        Collection<CacheOperationContext> cachePutContexts = contexts.get(CachePutOperation.class);
        Collection<CacheOperationContext> excluded = new ArrayList<>();
        for (CacheOperationContext context : cachePutContexts) {
            try {
                /**
                 * 条件不通过，就说明是不需要更新的
                 *
                 * 使用这个表示，SpEL不可以使用这个变量，要是用了就报错，但是下面捕获到异常并没有做处理，所以用了 也没事
                 * {@link CacheEvaluationContext#lookupVariable(String)}
                 * */
                if (!context.isConditionPassing(CacheOperationExpressionEvaluator.RESULT_UNAVAILABLE)) {
                    // 记录
                    excluded.add(context);
                }
            } catch (VariableNotAvailableException ex) {
                // Ignoring failure due to missing result, consider the cache put has to proceed
            }
        }
        // 不想等，说明是有需要执行的 CachePutOperation
        // Check if all puts have been excluded by condition
        return (cachePutContexts.size() != excluded.size());
    }

    private void processCacheEvicts(
            Collection<CacheOperationContext> contexts, boolean beforeInvocation, @Nullable Object result) {
        // 遍历所有的  CacheEvictOperation
        for (CacheOperationContext context : contexts) {
            CacheEvictOperation operation = (CacheEvictOperation) context.metadata.operation;
            /**
             * CacheEvictOperation 是调用前执行 且 满足条件
             * 就是 @CacheEvict(beforeInvocation = true)
             * */
            if (beforeInvocation == operation.isBeforeInvocation() && isConditionPassing(context, result)) {
                // 清空缓存
                performCacheEvict(context, operation, result);
            }
        }
    }

    private void performCacheEvict(
            CacheOperationContext context, CacheEvictOperation operation, @Nullable Object result) {

        Object key = null;
        // 遍历关联的Cache
        for (Cache cache : context.getCaches()) {
            /**
             * 就是 @CacheEvict(allEntries = true)
             * */
            if (operation.isCacheWide()) {
                logInvalidating(context, operation, null);
                // 清空整个 Cache
                doClear(cache, operation.isBeforeInvocation());
            } else {
                if (key == null) {
                    // 缓存的key
                    key = generateKey(context, result);
                }
                logInvalidating(context, operation, key);
                // 根据 key 清除 Cache 中存储的值
                doEvict(cache, key, operation.isBeforeInvocation());
            }
        }
    }

    private void logInvalidating(CacheOperationContext context, CacheEvictOperation operation, @Nullable Object key) {
        if (logger.isTraceEnabled()) {
            logger.trace("Invalidating " + (key != null ? "cache key [" + key + "]" : "entire cache") +
                    " for operation " + operation + " on method " + context.metadata.method);
        }
    }

    /**
     * Find a cached item only for {@link CacheableOperation} that passes the condition.
     * @param contexts the cacheable operations
     * @return a {@link Cache.ValueWrapper} holding the cached item,
     * or {@code null} if none is found
     */
    @Nullable
    private Cache.ValueWrapper findCachedItem(Collection<CacheOperationContext> contexts) {
        Object result = CacheOperationExpressionEvaluator.NO_RESULT;
        // 就是方法上写了多个 @Cacheable
        for (CacheOperationContext context : contexts) {
            // 匹配
            if (isConditionPassing(context, result)) {
                // 生成key
                Object key = generateKey(context, result);
                /**
                 * 因为一个 @Cacheable(cacheNames = {"a", "b"}) 是可以写多个 Cache的，所以会遍历所有的 Cache 根据key找，先找到就返回
                 * */
                Cache.ValueWrapper cached = findInCaches(context, key);
                // 也就是说 先命中就返回其值
                if (cached != null) {
                    return cached;
                } else {
                    if (logger.isTraceEnabled()) {
                        logger.trace("No cache entry for key '" + key + "' in cache(s) " + context.getCacheNames());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Collect the {@link CachePutRequest} for all {@link CacheOperation} using
     * the specified result item.
     * @param contexts the contexts to handle
     * @param result the result item (never {@code null})
     * @param putRequests the collection to update
     */
    private void collectPutRequests(Collection<CacheOperationContext> contexts,
                                    @Nullable Object result, Collection<CachePutRequest> putRequests) {

        for (CacheOperationContext context : contexts) {
            // 条件通过
            if (isConditionPassing(context, result)) {
                Object key = generateKey(context, result);
                // 构造器 CachePutRequest
                putRequests.add(new CachePutRequest(context, key));
            }
        }
    }

    @Nullable
    private Cache.ValueWrapper findInCaches(CacheOperationContext context, Object key) {
        // 遍历 Cache
        for (Cache cache : context.getCaches()) {
            // 就是根据 key 获取咯
            Cache.ValueWrapper wrapper = doGet(cache, key);
            if (wrapper != null) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Cache entry for key '" + key + "' found in cache '" + cache.getName() + "'");
                }
                return wrapper;
            }
        }
        return null;
    }

    private boolean isConditionPassing(CacheOperationContext context, @Nullable Object result) {
        boolean passing = context.isConditionPassing(result);
        if (!passing && logger.isTraceEnabled()) {
            logger.trace("Cache condition failed on method " + context.metadata.method +
                    " for operation " + context.metadata.operation);
        }
        return passing;
    }

    private Object generateKey(CacheOperationContext context, @Nullable Object result) {
        Object key = context.generateKey(result);
        if (key == null) {
            throw new IllegalArgumentException("Null key returned for cache operation (maybe you are " +
                    "using named params on classes without debug info?) "
                    + context.metadata.operation);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Computed cache key '" + key + "' for operation " + context.metadata.operation);
        }
        return key;
    }


    private class CacheOperationContexts {

        private final MultiValueMap<Class<? extends CacheOperation>, CacheOperationContext> contexts;

        private final boolean sync;

        public CacheOperationContexts(Collection<? extends CacheOperation> operations, Method method,
                                      Object[] args, Object target, Class<?> targetClass) {

            this.contexts = new LinkedMultiValueMap<>(operations.size());
            for (CacheOperation op : operations) {
                /**
                 * 将 CacheOperation 转成 CacheOperationContext
                 * */
                this.contexts.add(op.getClass(), getOperationContext(op, method, args, target, targetClass));
            }
            /**
             * 推断是否同步。
             *
             * 是对 @Cacheable(sync=true) 的重重校验：
             *  1. 没有 @Cacheable(sync=true) 就 `return false`
             *  2. 有`sync=true`的情况
             *      2.1 只允许写一个缓存注解
             * 	    2.2 cacheNames 属性，只能写一个name
             * 	    2.3 unless 属性，不能设置
             * 	    注：不满足这三点就直接报错 `throw new IllegalStateException`
             * */
            this.sync = determineSyncFlag(method);
        }

        public Collection<CacheOperationContext> get(Class<? extends CacheOperation> operationClass) {
            Collection<CacheOperationContext> result = this.contexts.get(operationClass);
            return (result != null ? result : Collections.emptyList());
        }

        public boolean isSynchronized() {
            return this.sync;
        }

        private boolean determineSyncFlag(Method method) {
            // 拿到 CacheableOperation 即 @Cacheable 的解析结果
            List<CacheOperationContext> cacheOperationContexts = this.contexts.get(CacheableOperation.class);
            if (cacheOperationContexts == null) {  // no @Cacheable operation at all
                return false;
            }
            boolean syncEnabled = false;
            for (CacheOperationContext cacheOperationContext : cacheOperationContexts) {
                // 是同步
                if (((CacheableOperation) cacheOperationContext.getOperation()).isSync()) {
                    syncEnabled = true;
                    break;
                }
            }
            // 是同步的，需要进一步校验
            if (syncEnabled) {
                // 开启同步，就不支持多个注解了
                if (this.contexts.size() > 1) {
                    throw new IllegalStateException(
                            "@Cacheable(sync=true) cannot be combined with other cache operations on '" + method + "'");
                }
                // 咋感觉这一步有点多余呀
                if (cacheOperationContexts.size() > 1) {
                    throw new IllegalStateException(
                            "Only one @Cacheable(sync=true) entry is allowed on '" + method + "'");
                }
                CacheOperationContext cacheOperationContext = cacheOperationContexts.iterator().next();
                // 到这里，说明就写了一个 @Cacheable ，所以拿第一个就行
                CacheableOperation operation = (CacheableOperation) cacheOperationContext.getOperation();
                // 不支持多个 Cache
                if (cacheOperationContext.getCaches().size() > 1) {
                    throw new IllegalStateException(
                            "@Cacheable(sync=true) only allows a single cache on '" + operation + "'");
                }
                // 不支持 unless 规则
                if (StringUtils.hasText(operation.getUnless())) {
                    throw new IllegalStateException(
                            "@Cacheable(sync=true) does not support unless attribute on '" + operation + "'");
                }
                return true;
            }
            return false;
        }
    }


    /**
     * Metadata of a cache operation that does not depend on a particular invocation
     * which makes it a good candidate for caching.
     */
    protected static class CacheOperationMetadata {

        private final CacheOperation operation;

        private final Method method;

        private final Class<?> targetClass;

        private final Method targetMethod;

        private final AnnotatedElementKey methodKey;

        private final KeyGenerator keyGenerator;

        private final CacheResolver cacheResolver;

        public CacheOperationMetadata(CacheOperation operation, Method method, Class<?> targetClass,
                                      KeyGenerator keyGenerator, CacheResolver cacheResolver) {

            this.operation = operation;
            this.method = BridgeMethodResolver.findBridgedMethod(method);
            this.targetClass = targetClass;
            this.targetMethod = (!Proxy.isProxyClass(targetClass) ?
                    AopUtils.getMostSpecificMethod(method, targetClass) : this.method);
            /**
             * Method对象+类型 作为 methodKey，在生成缓存key的时候会使用到，
             * 也就是说即使是多例bean，也能保证执行同一个cache方法 也能生成同样的缓存key，从而可以使用同样的缓存值
             * */
            this.methodKey = new AnnotatedElementKey(this.targetMethod, targetClass);
            this.keyGenerator = keyGenerator;
            this.cacheResolver = cacheResolver;
        }
    }


    /**
     * A {@link CacheOperationInvocationContext} context for a {@link CacheOperation}.
     */
    protected class CacheOperationContext implements CacheOperationInvocationContext<CacheOperation> {

        private final CacheOperationMetadata metadata;

        /**
         * 执行方法的入参。
         * 注：如果最后一个参数是可变参数，会将可变参数铺平
         */
        private final Object[] args;

        private final Object target;

        /**
         * 这个 CacheOperation 对应的 Cache
         */
        private final Collection<? extends Cache> caches;

        /**
         * 这个 CacheOperation 对应的 cacheName
         */
        private final Collection<String> cacheNames;

        @Nullable
        private Boolean conditionPassing;

        public CacheOperationContext(CacheOperationMetadata metadata, Object[] args, Object target) {
            this.metadata = metadata;
            /**
             * 处理方法的最后一个参数是可变参数的情况。若是可变参数，就拿到最后一个参数，将其铺平，和其它参数放在一块
             *
             * 目的是：在生成缓存key时会根据方法参数来生成，方法的入参相同应当是同一个key。
             *        但是动态参数的入参每次都是new数组来存储，这就导致方法的参数是一样的，但是动态参数是不同的对象，
             *        所以这里需要将动态参数给铺平
             * */
            this.args = extractArgs(metadata.method, args);
            this.target = target;
            /**
             * 使用 CacheResolver 通过注解设置的cacheName 拿到对应的 Cache 实例，
             * 本质还是使用的 {@link CacheManager#getCache(String)},
             * 常用的CacheManager都支持，没有预设Cache就在getCache的时候创建
             * */
            this.caches = CacheAspectSupport.this.getCaches(this, metadata.cacheResolver);
            // 记录
            this.cacheNames = createCacheNames(this.caches);
        }

        @Override
        public CacheOperation getOperation() {
            return this.metadata.operation;
        }

        @Override
        public Object getTarget() {
            return this.target;
        }

        @Override
        public Method getMethod() {
            return this.metadata.method;
        }

        @Override
        public Object[] getArgs() {
            return this.args;
        }

        /**
         * 如果最后一个参数是可变参数，就将可变参数 铺平，和其它参数放在一块
         * @param method 方法对象
         * @param args 方法参数，指的是方法被调用时的参数
         * @return
         */
        private Object[] extractArgs(Method method, Object[] args) {
            /**
             * 比如 public void a(int i,String... x) 就是变量参数
             * */
            // 参数列表不是可变参数
            if (!method.isVarArgs()) {
                return args;
            }
            /**
             * 因为最后一个参数是可变参数，所以拿到最后一个参数 铺平，和其它参数放在一块
             * */
            Object[] varArgs = ObjectUtils.toObjectArray(args[args.length - 1]);
            Object[] combinedArgs = new Object[args.length - 1 + varArgs.length];
            System.arraycopy(args, 0, combinedArgs, 0, args.length - 1);
            System.arraycopy(varArgs, 0, combinedArgs, args.length - 1, varArgs.length);
            return combinedArgs;
        }

        /**
         * 是通过条件。
         * - 有设置 @Cacheable(condition = "#root.methodName.startsWith('x')") 就解析SpEL看看结果是ture就是通过
         * - 没有设置condition属性，就直接是true
         * @param result
         * @return
         */
        protected boolean isConditionPassing(@Nullable Object result) {
            /**
             * 每次执行方法都会为每一个 CacheOperation 构造一个新的 CacheOperationContext 实例，所以这里可以这么判断
             * {@link CacheAspectSupport#execute(CacheOperationInvoker, Object, Method, Object[])}
             * */
            if (this.conditionPassing == null) {
                // 写了 condition 属性
                if (StringUtils.hasText(this.metadata.operation.getCondition())) {
                    // SpEL解析的构造上下文对象
                    EvaluationContext evaluationContext = createEvaluationContext(result);
                    // SpEL 解析表达式
                    this.conditionPassing = evaluator.condition(this.metadata.operation.getCondition(),
                            this.metadata.methodKey, evaluationContext);
                } else {
                    // 没有写 condition 属性 不需要判断
                    this.conditionPassing = true;
                }
            }
            return this.conditionPassing;
        }

        /**
         * 可以设置缓存
         * @param value
         * @return
         */
        protected boolean canPutToCache(@Nullable Object value) {
            String unless = "";
            if (this.metadata.operation instanceof CacheableOperation) {
                unless = ((CacheableOperation) this.metadata.operation).getUnless();
            } else if (this.metadata.operation instanceof CachePutOperation) {
                unless = ((CachePutOperation) this.metadata.operation).getUnless();
            }
            /**
             * 写了 unless 条件
             * @Cacheable(unless = "#result != null ")
             * */
            if (StringUtils.hasText(unless)) {
                // 构造 EvaluationContext，就是设置根对象，方法参数的变量，返回值变量
                EvaluationContext evaluationContext = createEvaluationContext(value);
                // 解析 SpEL ，解析结果是 true 就是不可以更新缓存，所以是 !
                return !evaluator.unless(unless, this.metadata.methodKey, evaluationContext);
            }
            // 没写 unless 直接返回true
            return true;
        }

        /**
         * 计算缓存的key。
         * - 如果有设置 @Cacheable(key = "#p1")， SpEL的值+Method+Target生成的key 作为缓存的key
         * - 没有指定key属性，就根据{@link KeyGenerator#generate(Object, Method, Object...)} 返回值作为缓存的key
         *
         * Compute the key for the given caching operation.
         */
        @Nullable
        protected Object generateKey(@Nullable Object result) {
            // 有key
            if (StringUtils.hasText(this.metadata.operation.getKey())) {
                // SpEL解析的上下文对象
                EvaluationContext evaluationContext = createEvaluationContext(result);
                // 就是拿到 SpEL表达式的值+methodKey 作为key
                return evaluator.key(this.metadata.operation.getKey(), this.metadata.methodKey, evaluationContext);
            }
            /**
             * 没有key，就使用 keyGenerator 生成key
             *
             * 默认的SimpleKeyGenerator 是只根据args来生成key的 {@link SimpleKeyGenerator#generate(Object, Method, Object...)}
             * */
            return this.metadata.keyGenerator.generate(this.target, this.metadata.method, this.args);
        }

        private EvaluationContext createEvaluationContext(@Nullable Object result) {
            return evaluator.createEvaluationContext(this.caches, this.metadata.method, this.args,
                    this.target, this.metadata.targetClass, this.metadata.targetMethod, result, beanFactory);
        }

        protected Collection<? extends Cache> getCaches() {
            return this.caches;
        }

        protected Collection<String> getCacheNames() {
            return this.cacheNames;
        }

        private Collection<String> createCacheNames(Collection<? extends Cache> caches) {
            Collection<String> names = new ArrayList<>();
            for (Cache cache : caches) {
                names.add(cache.getName());
            }
            return names;
        }
    }


    private class CachePutRequest {

        private final CacheOperationContext context;

        private final Object key;

        public CachePutRequest(CacheOperationContext context, Object key) {
            this.context = context;
            this.key = key;
        }

        public void apply(@Nullable Object result) {
            /**
             * @Cacheable(unless = "#result != null ")
             *  1. 指定了unless属性，就执行SpEL，结果是false 才可以设置缓存
             *  2. 没有指定unless属性，就是可以设置缓存
             * */
            if (this.context.canPutToCache(result)) {
                for (Cache cache : this.context.getCaches()) {
                    // 根据key往Cache中设置缓存
                    doPut(cache, this.key, result);
                }
            }
        }
    }


    private static final class CacheOperationCacheKey implements Comparable<CacheOperationCacheKey> {

        private final CacheOperation cacheOperation;

        private final AnnotatedElementKey methodCacheKey;

        private CacheOperationCacheKey(CacheOperation cacheOperation, Method method, Class<?> targetClass) {
            this.cacheOperation = cacheOperation;
            this.methodCacheKey = new AnnotatedElementKey(method, targetClass);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheOperationCacheKey)) {
                return false;
            }
            CacheOperationCacheKey otherKey = (CacheOperationCacheKey) other;
            return (this.cacheOperation.equals(otherKey.cacheOperation) &&
                    this.methodCacheKey.equals(otherKey.methodCacheKey));
        }

        @Override
        public int hashCode() {
            return (this.cacheOperation.hashCode() * 31 + this.methodCacheKey.hashCode());
        }

        @Override
        public String toString() {
            return this.cacheOperation + " on " + this.methodCacheKey;
        }

        @Override
        public int compareTo(CacheOperationCacheKey other) {
            int result = this.cacheOperation.getName().compareTo(other.cacheOperation.getName());
            if (result == 0) {
                result = this.methodCacheKey.compareTo(other.methodCacheKey);
            }
            return result;
        }
    }

    /**
     * Internal holder class for recording that a cache method was invoked.
     */
    private static class InvocationAwareResult {

        boolean invoked;

    }

}
