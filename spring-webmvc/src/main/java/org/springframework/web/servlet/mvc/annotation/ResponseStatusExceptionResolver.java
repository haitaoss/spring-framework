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

package org.springframework.web.servlet.mvc.annotation;

import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * A {@link org.springframework.web.servlet.HandlerExceptionResolver
 * HandlerExceptionResolver} that uses the {@link ResponseStatus @ResponseStatus}
 * annotation to map exceptions to HTTP status codes.
 *
 * <p>This exception resolver is enabled by default in the
 * {@link org.springframework.web.servlet.DispatcherServlet DispatcherServlet}
 * and the MVC Java config and the MVC namespace.
 *
 * <p>As of 4.2 this resolver also looks recursively for {@code @ResponseStatus}
 * present on cause exceptions, and as of 4.2.2 this resolver supports
 * attribute overrides for {@code @ResponseStatus} in custom composed annotations.
 *
 * <p>As of 5.0 this resolver also supports {@link ResponseStatusException}.
 *
 * @author Arjen Poutsma
 * @author Rossen Stoyanchev
 * @author Sam Brannen
 * @see ResponseStatus
 * @see ResponseStatusException
 * @since 3.0
 */
public class ResponseStatusExceptionResolver extends AbstractHandlerExceptionResolver implements MessageSourceAware {

    @Nullable
    private MessageSource messageSource;


    @Override
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }


    @Override
    @Nullable
    protected ModelAndView doResolveException(HttpServletRequest request, HttpServletResponse response,
                                              @Nullable Object handler, Exception ex) {

        try {
            if (ex instanceof ResponseStatusException) {
                /**
                 * 和下面的类似。ResponseStatusException 中定义了 异常状态码和异常信息
                 * */
                return resolveResponseStatusException((ResponseStatusException) ex, request, response, handler);
            }

            ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
            if (status != null) {
                /**
                 * 1. 根据注解值，设置响应体的错误消息，错误消息支持使用占位符，会使用 messageSource#getMessage 进行解析
                 *      比如：@ResponseStatus(code = HttpStatus.BAD_REQUEST,reason = "i18n_501_code")
                 * 2. 返回对象。表示异常被当前 HandlerExceptionResolver 处理了，不需要迭代后面的HandlerExceptionResolver判断是否要处理异常了
                 *      return new ModelAndView();
                 * */
                return resolveResponseStatus(status, request, response, handler, ex);
            }

            // 递归，找到可以处理异常位置
            if (ex.getCause() instanceof Exception) {
                return doResolveException(request, response, handler, (Exception) ex.getCause());
            }
        } catch (Exception resolveEx) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failure while trying to resolve exception [" + ex.getClass()
                        .getName() + "]", resolveEx);
            }
        }
        return null;
    }

    /**
     * Template method that handles the {@link ResponseStatus @ResponseStatus} annotation.
     * <p>The default implementation delegates to {@link #applyStatusAndReason}
     * with the status code and reason from the annotation.
     *
     * @param responseStatus the {@code @ResponseStatus} annotation
     * @param request        current HTTP request
     * @param response       current HTTP response
     * @param handler        the executed handler, or {@code null} if none chosen at the
     *                       time of the exception, e.g. if multipart resolution failed
     * @param ex             the exception
     * @return an empty ModelAndView, i.e. exception resolved
     */
    protected ModelAndView resolveResponseStatus(ResponseStatus responseStatus, HttpServletRequest request,
                                                 HttpServletResponse response, @Nullable Object handler,
                                                 Exception ex) throws Exception {

        int statusCode = responseStatus.code()
                .value();
        String reason = responseStatus.reason();
        return applyStatusAndReason(statusCode, reason, response);
    }

    /**
     * Template method that handles an {@link ResponseStatusException}.
     * <p>The default implementation applies the headers from
     * {@link ResponseStatusException#getResponseHeaders()} and delegates to
     * {@link #applyStatusAndReason} with the status code and reason from the
     * exception.
     *
     * @param ex       the exception
     * @param request  current HTTP request
     * @param response current HTTP response
     * @param handler  the executed handler, or {@code null} if none chosen at the
     *                 time of the exception, e.g. if multipart resolution failed
     * @return an empty ModelAndView, i.e. exception resolved
     * @since 5.0
     */
    protected ModelAndView resolveResponseStatusException(ResponseStatusException ex, HttpServletRequest request,
                                                          HttpServletResponse response,
                                                          @Nullable Object handler) throws Exception {

        // 添加响应头
        ex.getResponseHeaders()
                .forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        // 往response中设置异常状态码和异常信息
        return applyStatusAndReason(ex.getRawStatusCode(), ex.getReason(), response);
    }

    /**
     * Apply the resolved status code and reason to the response.
     * <p>The default implementation sends a response error using
     * {@link HttpServletResponse#sendError(int)} or
     * {@link HttpServletResponse#sendError(int, String)} if there is a reason
     * and then returns an empty ModelAndView.
     *
     * @param statusCode the HTTP status code
     * @param reason     the associated reason (may be {@code null} or empty)
     * @param response   current HTTP response
     * @since 5.0
     */
    protected ModelAndView applyStatusAndReason(int statusCode, @Nullable String reason,
                                                HttpServletResponse response) throws IOException {

        if (!StringUtils.hasLength(reason)) {
            response.sendError(statusCode);
        } else {
            String resolvedReason = (this.messageSource != null ? this.messageSource.getMessage(
                    reason, null, reason, LocaleContextHolder.getLocale()) : reason);
            response.sendError(statusCode, resolvedReason);
        }
        return new ModelAndView();
    }

}
