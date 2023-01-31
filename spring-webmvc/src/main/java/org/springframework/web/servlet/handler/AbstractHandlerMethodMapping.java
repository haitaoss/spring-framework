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

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMethodMappingNamingStrategy;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abstract base class for {@link HandlerMapping} implementations that define
 * a mapping between a request and a {@link HandlerMethod}.
 *
 * <p>For each registered handler method, a unique mapping is maintained with
 * subclasses defining the details of the mapping type {@code <T>}.
 *
 * @param <T> the mapping for a {@link HandlerMethod} containing the conditions
 *            needed to match the handler method to an incoming request.
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 */
public abstract class AbstractHandlerMethodMapping<T> extends AbstractHandlerMapping implements InitializingBean {

    /**
     * Bean name prefix for target beans behind scoped proxies. Used to exclude those
     * targets from handler method detection, in favor of the corresponding proxies.
     * <p>We're not checking the autowire-candidate status here, which is how the
     * proxy target filtering problem is being handled at the autowiring level,
     * since autowire-candidate may have been turned to {@code false} for other
     * reasons, while still expecting the bean to be eligible for handler methods.
     * <p>Originally defined in {@link org.springframework.aop.scope.ScopedProxyUtils}
     * but duplicated here to avoid a hard dependency on the spring-aop module.
     */
    private static final String SCOPED_TARGET_NAME_PREFIX = "scopedTarget.";

    private static final HandlerMethod PREFLIGHT_AMBIGUOUS_MATCH = new HandlerMethod(
            new EmptyHandler(), ClassUtils.getMethod(EmptyHandler.class, "handle"));

    private static final CorsConfiguration ALLOW_CORS_CONFIG = new CorsConfiguration();

    static {
        ALLOW_CORS_CONFIG.addAllowedOriginPattern("*");
        ALLOW_CORS_CONFIG.addAllowedMethod("*");
        ALLOW_CORS_CONFIG.addAllowedHeader("*");
        ALLOW_CORS_CONFIG.setAllowCredentials(true);
    }


    private boolean detectHandlerMethodsInAncestorContexts = false;

    @Nullable
    private HandlerMethodMappingNamingStrategy<T> namingStrategy;

    /**
     * 注册、记录 @RequestMapping 对应的方法信息
     */
    private final MappingRegistry mappingRegistry = new MappingRegistry();


    @Override
    public void setPatternParser(PathPatternParser patternParser) {
        Assert.state(this.mappingRegistry.getRegistrations()
                        .isEmpty(),
                "PathPatternParser must be set before the initialization of " + "request mappings through InitializingBean#afterPropertiesSet."
        );
        super.setPatternParser(patternParser);
    }

    /**
     * Whether to detect handler methods in beans in ancestor ApplicationContexts.
     * <p>Default is "false": Only beans in the current ApplicationContext are
     * considered, i.e. only in the context that this HandlerMapping itself
     * is defined in (typically the current DispatcherServlet's context).
     * <p>Switch this flag on to detect handler beans in ancestor contexts
     * (typically the Spring root WebApplicationContext) as well.
     *
     * @see #getCandidateBeanNames()
     */
    public void setDetectHandlerMethodsInAncestorContexts(boolean detectHandlerMethodsInAncestorContexts) {
        this.detectHandlerMethodsInAncestorContexts = detectHandlerMethodsInAncestorContexts;
    }

    /**
     * Configure the naming strategy to use for assigning a default name to every
     * mapped handler method.
     * <p>The default naming strategy is based on the capital letters of the
     * class name followed by "#" and then the method name, e.g. "TC#getFoo"
     * for a class named TestController with method getFoo.
     */
    public void setHandlerMethodMappingNamingStrategy(HandlerMethodMappingNamingStrategy<T> namingStrategy) {
        this.namingStrategy = namingStrategy;
    }

    /**
     * Return the configured naming strategy or {@code null}.
     */
    @Nullable
    public HandlerMethodMappingNamingStrategy<T> getNamingStrategy() {
        return this.namingStrategy;
    }

    /**
     * Return a (read-only) map with all mappings and HandlerMethod's.
     */
    public Map<T, HandlerMethod> getHandlerMethods() {
        this.mappingRegistry.acquireReadLock();
        try {
            return Collections.unmodifiableMap(this.mappingRegistry.getRegistrations()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().handlerMethod)));
        } finally {
            this.mappingRegistry.releaseReadLock();
        }
    }

    /**
     * Return the handler methods for the given mapping name.
     *
     * @param mappingName the mapping name
     * @return a list of matching HandlerMethod's or {@code null}; the returned
     * list will never be modified and is safe to iterate.
     * @see #setHandlerMethodMappingNamingStrategy
     */
    @Nullable
    public List<HandlerMethod> getHandlerMethodsForMappingName(String mappingName) {
        return this.mappingRegistry.getHandlerMethodsByMappingName(mappingName);
    }

    /**
     * Return the internal mapping registry. Provided for testing purposes.
     */
    MappingRegistry getMappingRegistry() {
        return this.mappingRegistry;
    }

    /**
     * Register the given mapping.
     * <p>This method may be invoked at runtime after initialization has completed.
     *
     * @param mapping the mapping for the handler method
     * @param handler the handler
     * @param method  the method
     */
    public void registerMapping(T mapping, Object handler, Method method) {
        if (logger.isTraceEnabled()) {
            logger.trace("Register \"" + mapping + "\" to " + method.toGenericString());
        }
        this.mappingRegistry.register(mapping, handler, method);
    }

    /**
     * Un-register the given mapping.
     * <p>This method may be invoked at runtime after initialization has completed.
     *
     * @param mapping the mapping to unregister
     */
    public void unregisterMapping(T mapping) {
        if (logger.isTraceEnabled()) {
            logger.trace("Unregister mapping \"" + mapping + "\"");
        }
        this.mappingRegistry.unregister(mapping);
    }


    // Handler method detection

    /**
     * Detects handler methods at initialization.
     *
     * @see #initHandlerMethods
     */
    @Override
    public void afterPropertiesSet() {
        initHandlerMethods();
    }

    /**
     * Scan beans in the ApplicationContext, detect and register handler methods.
     *
     * @see #getCandidateBeanNames()
     * @see #processCandidateBean
     * @see #handlerMethodsInitialized
     */
    protected void initHandlerMethods() {
        for (String beanName : getCandidateBeanNames()) {
            if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
                processCandidateBean(beanName);
            }
        }
        /**
         * getHandlerMethods() 返回的是 List<HandlerMethod(Method+beanName+IOC容器)>
         *
         * 注：啥也没干，子类可以重新这个方法做些事情
         * */
        handlerMethodsInitialized(getHandlerMethods());
    }

    /**
     * Determine the names of candidate beans in the application context.
     *
     * @see #setDetectHandlerMethodsInAncestorContexts
     * @see BeanFactoryUtils#beanNamesForTypeIncludingAncestors
     * @since 5.1
     */
    protected String[] getCandidateBeanNames() {
        return (this.detectHandlerMethodsInAncestorContexts ? BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
                obtainApplicationContext(), Object.class) : obtainApplicationContext().getBeanNamesForType(
                Object.class));
    }

    /**
     * Determine the type of the specified candidate bean and call
     * {@link #detectHandlerMethods} if identified as a handler type.
     * <p>This implementation avoids bean creation through checking
     * {@link org.springframework.beans.factory.BeanFactory#getType}
     * and calling {@link #detectHandlerMethods} with the bean name.
     *
     * @param beanName the name of the candidate bean
     * @see #isHandler
     * @see #detectHandlerMethods
     * @since 5.1
     */
    protected void processCandidateBean(String beanName) {
        Class<?> beanType = null;
        try {
            beanType = obtainApplicationContext().getType(beanName);
        } catch (Throwable ex) {
            // An unresolvable bean type, probably from a lazy bean - let's ignore it.
            if (logger.isTraceEnabled()) {
                logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
            }
        }
        // 类上有 @Controller 或者 @RequestMapping
        if (beanType != null && isHandler(beanType)) {
            // 侦查 HandlerMethod
            detectHandlerMethods(beanName);
        }
    }

    /**
     * Look for handler methods in the specified handler bean.
     *
     * @param handler either a bean name or an actual handler instance
     * @see #getMappingForMethod
     */
    protected void detectHandlerMethods(Object handler) {
        // handler 是 beanName
        Class<?> handlerType = (handler instanceof String ? obtainApplicationContext().getType(
                (String) handler) : handler.getClass());

        if (handlerType != null) {
            // 是cglib代理生成的class 就返回父类，否则就返回 handlerType
            Class<?> userType = ClassUtils.getUserClass(handlerType);
            /**
             * 返回方法信息 <Method,RequestMappingInfo>
             * */
            Map<Method, T> methods = MethodIntrospector.selectMethods(userType,
                    (MethodIntrospector.MetadataLookup<T>) method -> {
                        try {
                            /**
                             * 将 方法上 @RequestMapping 注解映射成 RequestMappingInfo 实体，若类也有 @RequestMapping 就整合两个注解的信息。
                             * 返回 RequestMappingInfo。
                             *
                             * 注：前提是方法上有 @RequestMapping 才会返回非null值，如果返回的是null就不会收集到最终的Map中
                             * {@link RequestMappingHandlerMapping#getMappingForMethod(Method, Class)}
                             * */
                            return getMappingForMethod(method, userType);
                        } catch (Throwable ex) {
                            throw new IllegalStateException(
                                    "Invalid mapping on handler class [" + userType.getName() + "]: " + method, ex);
                        }
                    }
            );
            if (logger.isTraceEnabled()) {
                logger.trace(formatMappings(userType, methods));
            } else if (mappingsLogger.isDebugEnabled()) {
                mappingsLogger.debug(formatMappings(userType, methods));
            }
            // 遍历
            methods.forEach((method, mapping) -> {
                Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                // 注册
                registerHandlerMethod(handler, invocableMethod, mapping);
            });
        }
    }

    private String formatMappings(Class<?> userType, Map<Method, T> methods) {
        String packageName = ClassUtils.getPackageName(userType);
        String formattedType = (StringUtils.hasText(packageName) ? Arrays.stream(packageName.split("\\."))
                .map(packageSegment -> packageSegment.substring(0, 1))
                .collect(Collectors.joining(".", "", "." + userType.getSimpleName())) : userType.getSimpleName());
        Function<Method, String> methodFormatter = method -> Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(",", "(", ")"));
        return methods.entrySet()
                .stream()
                .map(e -> {
                    Method method = e.getKey();
                    return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
                })
                .collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
    }

    /**
     * Register a handler method and its unique mapping. Invoked at startup for
     * each detected handler method.
     *
     * @param handler the bean name of the handler or the handler instance
     * @param method  the method to register
     * @param mapping the mapping conditions associated with the handler method
     * @throws IllegalStateException if another method was already registered
     *                               under the same mapping
     */
    protected void registerHandlerMethod(Object handler, Method method, T mapping) {
        this.mappingRegistry.register(mapping, handler, method);
    }

    /**
     * Create the HandlerMethod instance.
     *
     * @param handler either a bean name or an actual handler instance
     * @param method  the target method
     * @return the created HandlerMethod
     */
    protected HandlerMethod createHandlerMethod(Object handler, Method method) {
        if (handler instanceof String) {
            return new HandlerMethod((String) handler, obtainApplicationContext().getAutowireCapableBeanFactory(),
                    obtainApplicationContext(), method
            );
        }
        return new HandlerMethod(handler, method);
    }

    /**
     * Extract and return the CORS configuration for the mapping.
     */
    @Nullable
    protected CorsConfiguration initCorsConfiguration(Object handler, Method method, T mapping) {
        return null;
    }

    /**
     * Invoked after all handler methods have been detected.
     *
     * @param handlerMethods a read-only map with handler methods and mappings.
     */
    protected void handlerMethodsInitialized(Map<T, HandlerMethod> handlerMethods) {
        // Total includes detected mappings + explicit registrations via registerMapping
        int total = handlerMethods.size();
        if ((logger.isTraceEnabled() && total == 0) || (logger.isDebugEnabled() && total > 0)) {
            logger.debug(total + " mappings in " + formatMappingName());
        }
    }


    // Handler method lookup

    /**
     * Look up a handler method for the given request.
     */
    @Override
    @Nullable
    protected HandlerMethod getHandlerInternal(HttpServletRequest request) throws Exception {
        // 拿到请求的资源路径
        String lookupPath = initLookupPath(request);
        this.mappingRegistry.acquireReadLock();
        try {
            // 根据请求url匹配@RequestMapping("/xx")中的路径，返回解析好的 HandlerMethod（是由 Method+beanName+BeanFactory...）。
            HandlerMethod handlerMethod = lookupHandlerMethod(lookupPath, request);
            // 创建新的，其实就是根据beanName从容器中获取bean实例
            return (handlerMethod != null ? handlerMethod.createWithResolvedBean() : null);
        } finally {
            this.mappingRegistry.releaseReadLock();
        }
    }

    /**
     * Look up the best-matching handler method for the current request.
     * If multiple matches are found, the best match is selected.
     *
     * @param lookupPath mapping lookup path within the current servlet mapping
     * @param request    the current request
     * @return the best-matching handler method, or {@code null} if no match
     * @see #handleMatch(Object, String, HttpServletRequest)
     * @see #handleNoMatch(Set, String, HttpServletRequest)
     */
    @Nullable
    protected HandlerMethod lookupHandlerMethod(String lookupPath, HttpServletRequest request) throws Exception {
        List<Match> matches = new ArrayList<>();
        // 从注册信息中获取与 lookupPath 匹配的(就是请求路径与@RequestMapping(path)进行equals比较)
        List<T> directPathMatches = this.mappingRegistry.getMappingsByDirectPath(lookupPath);
        if (directPathMatches != null) {
            /**
             * 会根据 @RequestMapping(value = "", consumes = {""}, produces = {""}) 的信息 与 request 进行匹配，
             * 匹配就记录到 matches 中
             *
             * {@link RequestMappingInfo#getMatchingCondition(HttpServletRequest)}
             * */
            addMatchingMappings(directPathMatches, matches, request);
        }
        if (matches.isEmpty()) {
            // 同上
            addMatchingMappings(this.mappingRegistry.getRegistrations()
                    .keySet(), matches, request);
        }
        // 不为空
        if (!matches.isEmpty()) {
            Match bestMatch = matches.get(0);
            if (matches.size() > 1) {
                Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
                // 排序
                matches.sort(comparator);
                bestMatch = matches.get(0);
                if (logger.isTraceEnabled()) {
                    logger.trace(matches.size() + " matching mappings: " + matches);
                }
                // 是浏览器发出的预检请求
                if (CorsUtils.isPreFlightRequest(request)) {
                    for (Match match : matches) {
                        /**
                         * 匹配的方法或者其类上 写了 @CrossOrigin
                         *
                         * 看解析的地方就知道了
                         * {@link AbstractHandlerMethodMapping.MappingRegistry#register(Object, Object, Method)}
                         * */
                        if (match.hasCorsConfig()) {
                            // 快速返回
                            return PREFLIGHT_AMBIGUOUS_MATCH;
                        }
                    }
                } else {
                    // 直接报错
                    Match secondBestMatch = matches.get(1);
                    if (comparator.compare(bestMatch, secondBestMatch) == 0) {
                        Method m1 = bestMatch.getHandlerMethod()
                                .getMethod();
                        Method m2 = secondBestMatch.getHandlerMethod()
                                .getMethod();
                        String uri = request.getRequestURI();
                        throw new IllegalStateException(
                                "Ambiguous handler methods mapped for '" + uri + "': {" + m1 + ", " + m2 + "}");
                    }
                }
            }
            // 记录到request域中
            request.setAttribute(BEST_MATCHING_HANDLER_ATTRIBUTE, bestMatch.getHandlerMethod());
            /**
             * 这个时候会对 /users/{id} 占位符的处理 然后将占位符的值存到request域中
             * */
            handleMatch(bestMatch.mapping, lookupPath, request);
            // 返回 HandlerMethod
            return bestMatch.getHandlerMethod();
        } else {
            /**
             * 访问路径匹配了，但是其他的信息不匹配(method、content-type、accept、params) 就抛出对应的异常。
             * 若访问路径不匹配，就返回null
             * */
            return handleNoMatch(this.mappingRegistry.getRegistrations()
                    .keySet(), lookupPath, request);
        }
    }

    private void addMatchingMappings(Collection<T> mappings, List<Match> matches, HttpServletRequest request) {
        for (T mapping : mappings) {
            /**
             * 会根据request的信息进行匹配 @RequestMapping(value = "", consumes = {""}, produces = {""})
             * {@link RequestMappingInfo#getMatchingCondition(HttpServletRequest)}
             * */
            T match = getMatchingMapping(mapping, request);
            if (match != null) {
                matches.add(new Match(match, this.mappingRegistry.getRegistrations()
                        .get(mapping)));
            }
        }
    }

    /**
     * Invoked when a matching mapping is found.
     *
     * @param mapping    the matching mapping
     * @param lookupPath mapping lookup path within the current servlet mapping
     * @param request    the current request
     */
    protected void handleMatch(T mapping, String lookupPath, HttpServletRequest request) {
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, lookupPath);
    }

    /**
     * Invoked when no matching mapping is not found.
     *
     * @param mappings   all registered mappings
     * @param lookupPath mapping lookup path within the current servlet mapping
     * @param request    the current request
     * @throws ServletException in case of errors
     */
    @Nullable
    protected HandlerMethod handleNoMatch(Set<T> mappings, String lookupPath,
                                          HttpServletRequest request) throws Exception {

        return null;
    }

    @Override
    protected boolean hasCorsConfigurationSource(Object handler) {
        return super.hasCorsConfigurationSource(
                handler) || (handler instanceof HandlerMethod && this.mappingRegistry.getCorsConfiguration(
                (HandlerMethod) handler) != null);
    }

    @Override
    protected CorsConfiguration getCorsConfiguration(Object handler, HttpServletRequest request) {
        CorsConfiguration corsConfig = super.getCorsConfiguration(handler, request);
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            /**
             * 匹配Handler的时候会根据是否有@CrossOrigin做特殊处理
             * {@link AbstractHandlerMethodMapping#lookupHandlerMethod(String, HttpServletRequest)}
             * */
            if (handlerMethod.equals(PREFLIGHT_AMBIGUOUS_MATCH)) {
                // 允许所有
                return AbstractHandlerMethodMapping.ALLOW_CORS_CONFIG;
            } else {
                /**
                 * 这个是在注册的时候解析 @CrossOrigin
                 * {@link MappingRegistry#register(Object, Object, Method)}
                 * */
                CorsConfiguration corsConfigFromMethod = this.mappingRegistry.getCorsConfiguration(handlerMethod);
                corsConfig = (corsConfig != null ? corsConfig.combine(corsConfigFromMethod) : corsConfigFromMethod);
            }
        }
        return corsConfig;
    }


    // Abstract template methods

    /**
     * Whether the given type is a handler with handler methods.
     *
     * @param beanType the type of the bean being checked
     * @return "true" if this a handler type, "false" otherwise.
     */
    protected abstract boolean isHandler(Class<?> beanType);

    /**
     * Provide the mapping for a handler method. A method for which no
     * mapping can be provided is not a handler method.
     *
     * @param method      the method to provide a mapping for
     * @param handlerType the handler type, possibly a sub-type of the method's
     *                    declaring class
     * @return the mapping, or {@code null} if the method is not mapped
     */
    @Nullable
    protected abstract T getMappingForMethod(Method method, Class<?> handlerType);

    /**
     * Extract and return the URL paths contained in the supplied mapping.
     *
     * @deprecated as of 5.3 in favor of providing non-pattern mappings via
     * {@link #getDirectPaths(Object)} instead
     */
    @Deprecated
    protected Set<String> getMappingPathPatterns(T mapping) {
        return Collections.emptySet();
    }

    /**
     * Return the request mapping paths that are not patterns.
     *
     * @since 5.3
     */
    protected Set<String> getDirectPaths(T mapping) {
        Set<String> urls = Collections.emptySet();
        for (String path : getMappingPathPatterns(mapping)) {
            if (!getPathMatcher().isPattern(path)) {
                urls = (urls.isEmpty() ? new HashSet<>(1) : urls);
                urls.add(path);
            }
        }
        return urls;
    }

    /**
     * Check if a mapping matches the current request and return a (potentially
     * new) mapping with conditions relevant to the current request.
     *
     * @param mapping the mapping to get a match for
     * @param request the current HTTP servlet request
     * @return the match, or {@code null} if the mapping doesn't match
     */
    @Nullable
    protected abstract T getMatchingMapping(T mapping, HttpServletRequest request);

    /**
     * Return a comparator for sorting matching mappings.
     * The returned comparator should sort 'better' matches higher.
     *
     * @param request the current request
     * @return the comparator (never {@code null})
     */
    protected abstract Comparator<T> getMappingComparator(HttpServletRequest request);


    /**
     * A registry that maintains all mappings to handler methods, exposing methods
     * to perform lookups and providing concurrent access.
     * <p>Package-private for testing purposes.
     */
    class MappingRegistry {

        /**
         * MappingRegistration：映射登记员，其中包含了 RequestMapping+HandlerMethod等等
         * RequestMapping 是 @RequestMapping 解析成的对象
         * HandlerMethod 记录了 Method+beanName+BeanFactory
         */
        private final Map<T, MappingRegistration<T>> registry = new HashMap<>();

        /**
         * key：映射路径
         */
        private final MultiValueMap<String, T> pathLookup = new LinkedMultiValueMap<>();

        private final Map<String, List<HandlerMethod>> nameLookup = new ConcurrentHashMap<>();

        private final Map<HandlerMethod, CorsConfiguration> corsLookup = new ConcurrentHashMap<>();

        private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        /**
         * Return all registrations.
         *
         * @since 5.3
         */
        public Map<T, MappingRegistration<T>> getRegistrations() {
            return this.registry;
        }

        /**
         * Return matches for the given URL path. Not thread-safe.
         *
         * @see #acquireReadLock()
         */
        @Nullable
        public List<T> getMappingsByDirectPath(String urlPath) {
            return this.pathLookup.get(urlPath);
        }

        /**
         * Return handler methods by mapping name. Thread-safe for concurrent use.
         */
        public List<HandlerMethod> getHandlerMethodsByMappingName(String mappingName) {
            return this.nameLookup.get(mappingName);
        }

        /**
         * Return CORS configuration. Thread-safe for concurrent use.
         */
        @Nullable
        public CorsConfiguration getCorsConfiguration(HandlerMethod handlerMethod) {
            HandlerMethod original = handlerMethod.getResolvedFromHandlerMethod();
            return this.corsLookup.get(original != null ? original : handlerMethod);
        }

        /**
         * Acquire the read lock when using getMappings and getMappingsByUrl.
         */
        public void acquireReadLock() {
            this.readWriteLock.readLock()
                    .lock();
        }

        /**
         * Release the read lock after using getMappings and getMappingsByUrl.
         */
        public void releaseReadLock() {
            this.readWriteLock.readLock()
                    .unlock();
        }

        public void register(T mapping, Object handler, Method method) {
            // 上锁
            this.readWriteLock.writeLock()
                    .lock();
            try {
                // 生成 HandlerMethod(Method+beanName+IOC容器)
                HandlerMethod handlerMethod = createHandlerMethod(handler, method);
                /**
                 * 验证 mapping 是否会重复注册了,就是不能写相同的 @RequestMapping
                 * */
                validateMethodMapping(handlerMethod, mapping);

                /**
                 * 获取直接路径。比如 @RequestMapping("/index","/**") 直接路径就是 /index
                 * */
                Set<String> directPaths = AbstractHandlerMethodMapping.this.getDirectPaths(mapping);
                for (String path : directPaths) {
                    /**
                     * 一个路径可以对应多个mapping。
                     * 比如：
                     *  @RequestMapping(value = {"index", "/","/**"},method='post')
                     *  @RequestMapping(value = {"index", "/","/**"},method='get')
                     *
                     * 缓存起来，在根据Request获取Handler的时候尝试通过直接路径，进行匹配，没有匹配的，在进行通配符的路径匹配
                     * {@link org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#lookupHandlerMethod(String, HttpServletRequest)}
                     * */

                    this.pathLookup.add(path, mapping);
                }

                String name = null;
                if (getNamingStrategy() != null) {
                    /**
                     * 生成Name
                     * {@link RequestMappingInfoHandlerMethodMappingNamingStrategy#getName(HandlerMethod, RequestMappingInfo)}
                     * */
                    name = getNamingStrategy().getName(handlerMethod, mapping);
                    /**
                     * 记录到这个属性中
                     * {@link MappingRegistry#nameLookup}
                     * */
                    addMappingName(name, handlerMethod);
                }
                /**
                 * 获取方法或者类上的 @CrossOrigin 映射成 CorsConfiguration 实例
                 * 1. 方法上的@CrossOrigin的注解值 会覆盖 类上的@CrossOrigin的注解值
                 * 2. 如果没有设置注解值，会设置默认值
                 * */
                CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
                if (corsConfig != null) {
                    // 校验参数
                    corsConfig.validateAllowCredentials();
                    /**
                     * 缓存起来
                     * 在 request 匹配 Handler 时，会判断是否有 这个配置信息，用来处理预检请求的。
                     *
                     * {@link org.springframework.web.servlet.handler.AbstractHandlerMapping#getHandler(HttpServletRequest)}
                     * {@link org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#lookupHandlerMethod(String, HttpServletRequest)}
                     * */
                    this.corsLookup.put(handlerMethod, corsConfig);
                }

                /**
                 * 记录起来
                 *
                 * mapping 是 RequestMappingInfo 就是 @RequestMapping 注解映射成的对象
                 * */
                this.registry.put(mapping,
                        new MappingRegistration<>(mapping, handlerMethod, directPaths, name, corsConfig != null)
                );
            } finally {
                // 解锁
                this.readWriteLock.writeLock()
                        .unlock();
            }
        }

        private void validateMethodMapping(HandlerMethod handlerMethod, T mapping) {
            MappingRegistration<T> registration = this.registry.get(mapping);
            HandlerMethod existingHandlerMethod = (registration != null ? registration.getHandlerMethod() : null);
            if (existingHandlerMethod != null && !existingHandlerMethod.equals(handlerMethod)) {
                throw new IllegalStateException(
                        "Ambiguous mapping. Cannot map '" + handlerMethod.getBean() + "' method \n" + handlerMethod + "\nto " + mapping + ": There is already '" + existingHandlerMethod.getBean() + "' bean method\n" + existingHandlerMethod + " mapped.");
            }
        }

        private void addMappingName(String name, HandlerMethod handlerMethod) {
            List<HandlerMethod> oldList = this.nameLookup.get(name);
            if (oldList == null) {
                oldList = Collections.emptyList();
            }

            for (HandlerMethod current : oldList) {
                // 已经就记录过就返回
                if (handlerMethod.equals(current)) {
                    return;
                }
            }

            List<HandlerMethod> newList = new ArrayList<>(oldList.size() + 1);
            newList.addAll(oldList);
            newList.add(handlerMethod);
            // 记录
            this.nameLookup.put(name, newList);
        }

        public void unregister(T mapping) {
            this.readWriteLock.writeLock()
                    .lock();
            try {
                MappingRegistration<T> registration = this.registry.remove(mapping);
                if (registration == null) {
                    return;
                }

                for (String path : registration.getDirectPaths()) {
                    List<T> mappings = this.pathLookup.get(path);
                    if (mappings != null) {
                        mappings.remove(registration.getMapping());
                        if (mappings.isEmpty()) {
                            this.pathLookup.remove(path);
                        }
                    }
                }

                removeMappingName(registration);

                this.corsLookup.remove(registration.getHandlerMethod());
            } finally {
                this.readWriteLock.writeLock()
                        .unlock();
            }
        }

        private void removeMappingName(MappingRegistration<T> definition) {
            String name = definition.getMappingName();
            if (name == null) {
                return;
            }
            HandlerMethod handlerMethod = definition.getHandlerMethod();
            List<HandlerMethod> oldList = this.nameLookup.get(name);
            if (oldList == null) {
                return;
            }
            if (oldList.size() <= 1) {
                this.nameLookup.remove(name);
                return;
            }
            List<HandlerMethod> newList = new ArrayList<>(oldList.size() - 1);
            for (HandlerMethod current : oldList) {
                if (!current.equals(handlerMethod)) {
                    newList.add(current);
                }
            }
            this.nameLookup.put(name, newList);
        }
    }


    static class MappingRegistration<T> {

        private final T mapping;

        private final HandlerMethod handlerMethod;

        private final Set<String> directPaths;

        @Nullable
        private final String mappingName;

        private final boolean corsConfig;

        public MappingRegistration(T mapping, HandlerMethod handlerMethod, @Nullable Set<String> directPaths,
                                   @Nullable String mappingName, boolean corsConfig) {

            Assert.notNull(mapping, "Mapping must not be null");
            Assert.notNull(handlerMethod, "HandlerMethod must not be null");
            this.mapping = mapping;
            this.handlerMethod = handlerMethod;
            this.directPaths = (directPaths != null ? directPaths : Collections.emptySet());
            this.mappingName = mappingName;
            this.corsConfig = corsConfig;
        }

        public T getMapping() {
            return this.mapping;
        }

        public HandlerMethod getHandlerMethod() {
            return this.handlerMethod;
        }

        public Set<String> getDirectPaths() {
            return this.directPaths;
        }

        @Nullable
        public String getMappingName() {
            return this.mappingName;
        }

        public boolean hasCorsConfig() {
            return this.corsConfig;
        }
    }


    /**
     * A thin wrapper around a matched HandlerMethod and its mapping, for the purpose of
     * comparing the best match with a comparator in the context of the current request.
     */
    private class Match {

        private final T mapping;

        private final MappingRegistration<T> registration;

        public Match(T mapping, MappingRegistration<T> registration) {
            this.mapping = mapping;
            this.registration = registration;
        }

        public HandlerMethod getHandlerMethod() {
            return this.registration.getHandlerMethod();
        }

        public boolean hasCorsConfig() {
            return this.registration.hasCorsConfig();
        }

        @Override
        public String toString() {
            return this.mapping.toString();
        }
    }


    private class MatchComparator implements Comparator<Match> {

        private final Comparator<T> comparator;

        public MatchComparator(Comparator<T> comparator) {
            this.comparator = comparator;
        }

        @Override
        public int compare(Match match1, Match match2) {
            return this.comparator.compare(match1.mapping, match2.mapping);
        }
    }


    private static class EmptyHandler {

        @SuppressWarnings("unused")
        public void handle() {
            throw new UnsupportedOperationException("Not implemented");
        }
    }

}
