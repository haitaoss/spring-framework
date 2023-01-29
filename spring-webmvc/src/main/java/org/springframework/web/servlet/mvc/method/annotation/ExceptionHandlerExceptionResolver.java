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

package org.springframework.web.servlet.mvc.method.annotation;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.SpringProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.*;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.handler.AbstractHandlerMethodExceptionResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link AbstractHandlerMethodExceptionResolver} that resolves exceptions
 * through {@code @ExceptionHandler} methods.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers}.
 * Or alternatively to re-configure all argument and return value types use
 * {@link #setArgumentResolvers} and {@link #setReturnValueHandlers(List)}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @since 3.1
 */
public class ExceptionHandlerExceptionResolver extends AbstractHandlerMethodExceptionResolver implements ApplicationContextAware, InitializingBean {

    /**
     * Boolean flag controlled by a {@code spring.xml.ignore} system property that instructs Spring to
     * ignore XML, i.e. to not initialize the XML-related infrastructure.
     * <p>The default is "false".
     */
    private static final boolean shouldIgnoreXml = SpringProperties.getFlag("spring.xml.ignore");


    @Nullable
    private List<HandlerMethodArgumentResolver> customArgumentResolvers;

    @Nullable
    private HandlerMethodArgumentResolverComposite argumentResolvers;

    @Nullable
    private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

    @Nullable
    private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

    private List<HttpMessageConverter<?>> messageConverters;

    private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

    /**
     * 记录在 响应体通知 中
     */
    private final List<Object> responseBodyAdvice = new ArrayList<>();

    @Nullable
    private ApplicationContext applicationContext;

    private final Map<Class<?>, ExceptionHandlerMethodResolver> exceptionHandlerCache = new ConcurrentHashMap<>(64);

    /**
     * 记录在 异常处理通知 中 全局的
     */
    private final Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> exceptionHandlerAdviceCache = new LinkedHashMap<>();


    public ExceptionHandlerExceptionResolver() {
        this.messageConverters = new ArrayList<>();
        this.messageConverters.add(new ByteArrayHttpMessageConverter());
        this.messageConverters.add(new StringHttpMessageConverter());
        if (!shouldIgnoreXml) {
            try {
                this.messageConverters.add(new SourceHttpMessageConverter<>());
            } catch (Error err) {
                // Ignore when no TransformerFactory implementation is available
            }
        }
        this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
    }


    /**
     * Provide resolvers for custom argument types. Custom resolvers are ordered
     * after built-in ones. To override the built-in support for argument
     * resolution use {@link #setArgumentResolvers} instead.
     */
    public void setCustomArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
        this.customArgumentResolvers = argumentResolvers;
    }

    /**
     * Return the custom argument resolvers, or {@code null}.
     */
    @Nullable
    public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
        return this.customArgumentResolvers;
    }

    /**
     * Configure the complete list of supported argument types thus overriding
     * the resolvers that would otherwise be configured by default.
     */
    public void setArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
        if (argumentResolvers == null) {
            this.argumentResolvers = null;
        } else {
            this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
            this.argumentResolvers.addResolvers(argumentResolvers);
        }
    }

    /**
     * Return the configured argument resolvers, or possibly {@code null} if
     * not initialized yet via {@link #afterPropertiesSet()}.
     */
    @Nullable
    public HandlerMethodArgumentResolverComposite getArgumentResolvers() {
        return this.argumentResolvers;
    }

    /**
     * Provide handlers for custom return value types. Custom handlers are
     * ordered after built-in ones. To override the built-in support for
     * return value handling use {@link #setReturnValueHandlers}.
     */
    public void setCustomReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
        this.customReturnValueHandlers = returnValueHandlers;
    }

    /**
     * Return the custom return value handlers, or {@code null}.
     */
    @Nullable
    public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
        return this.customReturnValueHandlers;
    }

    /**
     * Configure the complete list of supported return value types thus
     * overriding handlers that would otherwise be configured by default.
     */
    public void setReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
        if (returnValueHandlers == null) {
            this.returnValueHandlers = null;
        } else {
            this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
            this.returnValueHandlers.addHandlers(returnValueHandlers);
        }
    }

    /**
     * Return the configured handlers, or possibly {@code null} if not
     * initialized yet via {@link #afterPropertiesSet()}.
     */
    @Nullable
    public HandlerMethodReturnValueHandlerComposite getReturnValueHandlers() {
        return this.returnValueHandlers;
    }

    /**
     * Set the message body converters to use.
     * <p>These converters are used to convert from and to HTTP requests and responses.
     */
    public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
        this.messageConverters = messageConverters;
    }

    /**
     * Return the configured message body converters.
     */
    public List<HttpMessageConverter<?>> getMessageConverters() {
        return this.messageConverters;
    }

    /**
     * Set the {@link ContentNegotiationManager} to use to determine requested media types.
     * If not set, the default constructor is used.
     */
    public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
        this.contentNegotiationManager = contentNegotiationManager;
    }

    /**
     * Return the configured {@link ContentNegotiationManager}.
     */
    public ContentNegotiationManager getContentNegotiationManager() {
        return this.contentNegotiationManager;
    }

    /**
     * Add one or more components to be invoked after the execution of a controller
     * method annotated with {@code @ResponseBody} or returning {@code ResponseEntity}
     * but before the body is written to the response with the selected
     * {@code HttpMessageConverter}.
     */
    public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
        if (responseBodyAdvice != null) {
            this.responseBodyAdvice.addAll(responseBodyAdvice);
        }
    }

    @Override
    public void setApplicationContext(@Nullable ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Nullable
    public ApplicationContext getApplicationContext() {
        return this.applicationContext;
    }


    @Override
    public void afterPropertiesSet() {
        // 处理 @ControllerAdvice 注解的bean，解析里面的 @ExceptionHandler
        // Do this first, it may add ResponseBodyAdvice beans
        initExceptionHandlerAdviceCache();

        if (this.argumentResolvers == null) {
            List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
            // 参数解析器
            this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
        }
        if (this.returnValueHandlers == null) {
            List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
            // 返回值处理器
            this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
        }
    }

    private void initExceptionHandlerAdviceCache() {
        if (getApplicationContext() == null) {
            return;
        }

        // 找到有 @ControllerAdvice 的bean，然后排序返回
        List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
        // 遍历
        for (ControllerAdviceBean adviceBean : adviceBeans) {
            Class<?> beanType = adviceBean.getBeanType();
            if (beanType == null) {
                throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
            }
            /**
             * 装饰成 ExceptionHandlerMethodResolver。
             * 实例化中，会解析类中的 @ExceptionHandler 方法，收集到Map<Throwable,Method>中
             * */
            ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(beanType);
            // 就是 beanType 中有 @ExceptionHandler 注解的方法
            if (resolver.hasExceptionMappings()) {
                // 记录在 异常处理通知 中
                this.exceptionHandlerAdviceCache.put(adviceBean, resolver);
            }
            if (ResponseBodyAdvice.class.isAssignableFrom(beanType)) {
                // 记录在 响应体通知 中
                this.responseBodyAdvice.add(adviceBean);
            }
        }

        if (logger.isDebugEnabled()) {
            int handlerSize = this.exceptionHandlerAdviceCache.size();
            int adviceSize = this.responseBodyAdvice.size();
            if (handlerSize == 0 && adviceSize == 0) {
                logger.debug("ControllerAdvice beans: none");
            } else {
                logger.debug(
                        "ControllerAdvice beans: " + handlerSize + " @ExceptionHandler, " + adviceSize + " ResponseBodyAdvice");
            }
        }
    }

    /**
     * Return an unmodifiable Map with the {@link ControllerAdvice @ControllerAdvice}
     * beans discovered in the ApplicationContext. The returned map will be empty if
     * the method is invoked before the bean has been initialized via
     * {@link #afterPropertiesSet()}.
     */
    public Map<ControllerAdviceBean, ExceptionHandlerMethodResolver> getExceptionHandlerAdviceCache() {
        return Collections.unmodifiableMap(this.exceptionHandlerAdviceCache);
    }

    /**
     * Return the list of argument resolvers to use including built-in resolvers
     * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
     */
    protected List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        // Annotation-based argument resolution
        resolvers.add(new SessionAttributeMethodArgumentResolver());
        resolvers.add(new RequestAttributeMethodArgumentResolver());

        // Type-based argument resolution
        resolvers.add(new ServletRequestMethodArgumentResolver());
        resolvers.add(new ServletResponseMethodArgumentResolver());
        resolvers.add(new RedirectAttributesMethodArgumentResolver());
        resolvers.add(new ModelMethodProcessor());

        // Custom arguments
        if (getCustomArgumentResolvers() != null) {
            resolvers.addAll(getCustomArgumentResolvers());
        }

        // Catch-all
        resolvers.add(new PrincipalMethodArgumentResolver());

        return resolvers;
    }

    /**
     * Return the list of return value handlers to use including built-in and
     * custom handlers provided via {@link #setReturnValueHandlers}.
     */
    protected List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
        List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

        // Single-purpose return value types
        handlers.add(new ModelAndViewMethodReturnValueHandler());
        handlers.add(new ModelMethodProcessor());
        handlers.add(new ViewMethodReturnValueHandler());
        handlers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.contentNegotiationManager,
                this.responseBodyAdvice
        ));

        // Annotation-based return value types
        handlers.add(new ServletModelAttributeMethodProcessor(false));
        handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.contentNegotiationManager,
                this.responseBodyAdvice
        ));

        // Multi-purpose return value types
        handlers.add(new ViewNameMethodReturnValueHandler());
        handlers.add(new MapMethodProcessor());

        // Custom return value types
        if (getCustomReturnValueHandlers() != null) {
            handlers.addAll(getCustomReturnValueHandlers());
        }

        // Catch-all
        handlers.add(new ServletModelAttributeMethodProcessor(true));

        return handlers;
    }

    @Override
    protected boolean hasGlobalExceptionHandlers() {
        return !this.exceptionHandlerAdviceCache.isEmpty();
    }

    /**
     * Find an {@code @ExceptionHandler} method and invoke it to handle the raised exception.
     */
    @Override
    @Nullable
    protected ModelAndView doResolveHandlerMethodException(HttpServletRequest request, HttpServletResponse response,
                                                           @Nullable HandlerMethod handlerMethod, Exception exception) {

        // 找到能处理这个异常的方法@ExceptionHandler，然后构造出 ServletInvocableHandlerMethod
        ServletInvocableHandlerMethod exceptionHandlerMethod = getExceptionHandlerMethod(handlerMethod, exception);
        // 为null说明没有处理这个异常的方法
        if (exceptionHandlerMethod == null) {
            return null;
        }

        if (this.argumentResolvers != null) {
            // 设置参数解析器
            exceptionHandlerMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
        }
        if (this.returnValueHandlers != null) {
            // 设置返回值处理器
            exceptionHandlerMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
        }

        ServletWebRequest webRequest = new ServletWebRequest(request, response);
        // 初始化上下文对象
        ModelAndViewContainer mavContainer = new ModelAndViewContainer();

        // 记录抛出的异常 第一个元素是抛出异常的方法 最后一个元素是方法的入口
        ArrayList<Throwable> exceptions = new ArrayList<>();
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Using @ExceptionHandler " + exceptionHandlerMethod);
            }
            // Expose causes as provided arguments as well
            Throwable exToExpose = exception;
            while (exToExpose != null) {
                exceptions.add(exToExpose);
                Throwable cause = exToExpose.getCause();
                exToExpose = (cause != exToExpose ? cause : null);
            }
            Object[] arguments = new Object[exceptions.size() + 1];
            exceptions.toArray(arguments);  // efficient arraycopy call in ArrayList
            // 最后一个尝试是 handlerMethod
            arguments[arguments.length - 1] = handlerMethod;

            // 执行方法并处理返回值
            exceptionHandlerMethod.invokeAndHandle(webRequest, mavContainer, arguments);
        } catch (Throwable invocationEx) {
            // Any other than the original exception (or a cause) is unintended here,
            // probably an accident (e.g. failed assertion or the like).
            if (!exceptions.contains(invocationEx) && logger.isWarnEnabled()) {
                logger.warn("Failure in @ExceptionHandler " + exceptionHandlerMethod, invocationEx);
            }
            // 为null 就交个下一个 HandlerExceptionResolver 处理
            // Continue with default processing of the original exception...
            return null;
        }

        /**
         * 请求已经处理了，默认是false。
         *
         * 不返回null而是返回一个空对象的目的是 后面判断的时候 不为null 说明异常已经被处理了，为null就交个下一个 HandlerExceptionResolver 处理
         *
         * */
        if (mavContainer.isRequestHandled()) {
            /**
             * 没有指定视图，后面判断的时候就不会进行视图的解析，因为请求已经处理了(比如@ResponseBody)
             *
             * {@link DispatcherServlet#processHandlerException(HttpServletRequest, HttpServletResponse, Object, Exception)}
             * */
            return new ModelAndView();
        } else {
            // 构造一个 ModelAndView
            ModelMap model = mavContainer.getModel();
            HttpStatus status = mavContainer.getStatus();
            ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, status);
            mav.setViewName(mavContainer.getViewName());
            if (!mavContainer.isViewReference()) {
                mav.setView((View) mavContainer.getView());
            }
            // 回调的方法参数有 RedirectAttributes 类型的
            if (model instanceof RedirectAttributes) {
                // 拿出参数值
                Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
                // 存到request域中
                RequestContextUtils.getOutputFlashMap(request)
                        .putAll(flashAttributes);
            }
            return mav;
        }
    }

    /**
     * Find an {@code @ExceptionHandler} method for the given exception. The default
     * implementation searches methods in the class hierarchy of the controller first
     * and if not found, it continues searching for additional {@code @ExceptionHandler}
     * methods assuming some {@linkplain ControllerAdvice @ControllerAdvice}
     * Spring-managed beans were detected.
     *
     * @param handlerMethod the method where the exception was raised (may be {@code null})
     * @param exception     the raised exception
     * @return a method to handle the exception, or {@code null} if none
     */
    @Nullable
    protected ServletInvocableHandlerMethod getExceptionHandlerMethod(@Nullable HandlerMethod handlerMethod,
                                                                      Exception exception) {

        Class<?> handlerType = null;

        if (handlerMethod != null) {
            // Local exception handler methods on the controller class itself.
            // To be invoked through the proxy, even in case of an interface-based proxy.
            handlerType = handlerMethod.getBeanType();
            // 装饰成ExceptionHandlerMethodResolver，实例化的过程会判断是否存在@ExceptionHandler标注的方法
            ExceptionHandlerMethodResolver resolver = this.exceptionHandlerCache.get(handlerType);
            if (resolver == null) {
                resolver = new ExceptionHandlerMethodResolver(handlerType);
                // 存到局部变量
                this.exceptionHandlerCache.put(handlerType, resolver);
            }
            // 找到处理这个异常的方法
            Method method = resolver.resolveMethod(exception);
            if (method != null) {
                // 找到就快速返回
                return new ServletInvocableHandlerMethod(handlerMethod.getBean(), method, this.applicationContext);
            }
            // For advice applicability check below (involving base packages, assignable types
            // and annotation presence), use target class instead of interface-based proxy.
            if (Proxy.isProxyClass(handlerType)) {
                handlerType = AopUtils.getTargetClass(handlerMethod.getBean());
            }
        }

        // 遍历全局异常处理通知，找到适配的 就立即返回
        for (Map.Entry<ControllerAdviceBean, ExceptionHandlerMethodResolver> entry : this.exceptionHandlerAdviceCache.entrySet()) {
            ControllerAdviceBean advice = entry.getKey();
            // 校验是否符合 @ControllerAdvice(basePackages,basePackageClasses,assignableTypes,annotations)
            if (advice.isApplicableToBeanType(handlerType)) {
                ExceptionHandlerMethodResolver resolver = entry.getValue();
                // 找到解析这个异常的方法
                Method method = resolver.resolveMethod(exception);
                if (method != null) {
                    // 找到就返回
                    return new ServletInvocableHandlerMethod(advice.resolveBean(), method, this.applicationContext);
                }
            }
        }

        return null;
    }

}
