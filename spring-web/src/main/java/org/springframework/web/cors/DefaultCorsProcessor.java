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

package org.springframework.web.cors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.web.filter.CorsFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The default implementation of {@link CorsProcessor}, as defined by the
 * <a href="https://www.w3.org/TR/cors/">CORS W3C recommendation</a>.
 *
 * <p>Note that when input {@link CorsConfiguration} is {@code null}, this
 * implementation does not reject simple or actual requests outright but simply
 * avoid adding CORS headers to the response. CORS processing is also skipped
 * if the response already contains CORS headers.
 *
 * @author Sebastien Deleuze
 * @author Rossen Stoyanchev
 * @since 4.2
 */
public class DefaultCorsProcessor implements CorsProcessor {

    private static final Log logger = LogFactory.getLog(DefaultCorsProcessor.class);


    @Override
    @SuppressWarnings("resource")
    public boolean processRequest(@Nullable CorsConfiguration config, HttpServletRequest request,
                                  HttpServletResponse response) throws IOException {

        // 设置响应头
        Collection<String> varyHeaders = response.getHeaders(HttpHeaders.VARY);
        if (!varyHeaders.contains(HttpHeaders.ORIGIN)) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        }
        if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        }
        if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        }

        // 不是预检请求，直接return true
        if (!CorsUtils.isCorsRequest(request)) {
            return true;
        }

        // 响应头有 Access-Control-Allow-Origin 直接是true
        if (response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) != null) {
            logger.trace("Skip: response already contains \"Access-Control-Allow-Origin\"");
            return true;
        }

        boolean preFlightRequest = CorsUtils.isPreFlightRequest(request);
        // 没有 跨域配置
        if (config == null) {
            // 是预检请求(跨域请求)
            if (preFlightRequest) {
                // 直接往Response中设置错误状态码
                rejectRequest(new ServletServerHttpResponse(response));
                /**
                 * 返回false。表示不要回调handleMethod的，直接响应客户端
                 * {@link CorsFilter#doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)}
                 * {@link org.springframework.web.servlet.handler.AbstractHandlerMapping.CorsInterceptor#preHandle(HttpServletRequest, HttpServletResponse, Object)}
                 * */
                return false;
            } else {
                return true;
            }
        }

        return handleInternal(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), config,
                preFlightRequest
        );
    }

    /**
     * Invoked when one of the CORS checks failed.
     * The default implementation sets the response status to 403 and writes
     * "Invalid CORS request" to the response.
     */
    protected void rejectRequest(ServerHttpResponse response) throws IOException {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getBody()
                .write("Invalid CORS request".getBytes(StandardCharsets.UTF_8));
        response.flush();
    }

    /**
     * Handle the given request.
     */
    protected boolean handleInternal(ServerHttpRequest request, ServerHttpResponse response, CorsConfiguration config,
                                     boolean preFlightRequest) throws IOException {
        /*
        浏览器发送的预检请求 Request Headers 示例:
            Access-Control-Request-Headers: content-type
            Access-Control-Request-Method: POST
            Origin: http://localhost:8080

         */
        String requestOrigin = request.getHeaders()
                .getOrigin();
        // 使用 CorsConfiguration 校验 Origin 的值
        String allowOrigin = checkOrigin(config, requestOrigin);
        HttpHeaders responseHeaders = response.getHeaders();

        if (allowOrigin == null) {
            logger.debug("Reject: '" + requestOrigin + "' origin is not allowed");
            // 设置错误状态码
            rejectRequest(response);
            return false;
        }

        // 使用 CorsConfiguration 校验 Access-Control-Request-Method 的值
        HttpMethod requestMethod = getMethodToUse(request, preFlightRequest);
        List<HttpMethod> allowMethods = checkMethods(config, requestMethod);
        if (allowMethods == null) {
            logger.debug("Reject: HTTP '" + requestMethod + "' is not allowed");
            rejectRequest(response);
            return false;
        }
        // 使用 CorsConfiguration 校验 Access-Control-Request-Headers 的值
        List<String> requestHeaders = getHeadersToUse(request, preFlightRequest);
        // 允许的请求头
        List<String> allowHeaders = checkHeaders(config, requestHeaders);
        if (preFlightRequest && allowHeaders == null) {
            logger.debug("Reject: headers '" + requestHeaders + "' are not allowed");
            // 设置错误状态码
            rejectRequest(response);
            return false;
        }

        // 设置允许的跨域信息到响应体中，这样子浏览器才知道具体做啥
        responseHeaders.setAccessControlAllowOrigin(allowOrigin);

        if (preFlightRequest) {
            // 设置到响应头中
            responseHeaders.setAccessControlAllowMethods(allowMethods);
        }

        if (preFlightRequest && !allowHeaders.isEmpty()) {
            // 设置到响应头中
            responseHeaders.setAccessControlAllowHeaders(allowHeaders);
        }

        if (!CollectionUtils.isEmpty(config.getExposedHeaders())) {
            // 设置到响应头中
            responseHeaders.setAccessControlExposeHeaders(config.getExposedHeaders());
        }

        if (Boolean.TRUE.equals(config.getAllowCredentials())) {
            responseHeaders.setAccessControlAllowCredentials(true);
        }

        if (preFlightRequest && config.getMaxAge() != null) {
            responseHeaders.setAccessControlMaxAge(config.getMaxAge());
        }

        response.flush();
        return true;
    }

    /**
     * Check the origin and determine the origin for the response. The default
     * implementation simply delegates to
     * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
     */
    @Nullable
    protected String checkOrigin(CorsConfiguration config, @Nullable String requestOrigin) {
        return config.checkOrigin(requestOrigin);
    }

    /**
     * Check the HTTP method and determine the methods for the response of a
     * pre-flight request. The default implementation simply delegates to
     * {@link org.springframework.web.cors.CorsConfiguration#checkHttpMethod(HttpMethod)}.
     */
    @Nullable
    protected List<HttpMethod> checkMethods(CorsConfiguration config, @Nullable HttpMethod requestMethod) {
        return config.checkHttpMethod(requestMethod);
    }

    @Nullable
    private HttpMethod getMethodToUse(ServerHttpRequest request, boolean isPreFlight) {
        return (isPreFlight ? request.getHeaders()
                .getAccessControlRequestMethod() : request.getMethod());
    }

    /**
     * Check the headers and determine the headers for the response of a
     * pre-flight request. The default implementation simply delegates to
     * {@link org.springframework.web.cors.CorsConfiguration#checkOrigin(String)}.
     */
    @Nullable
    protected List<String> checkHeaders(CorsConfiguration config, List<String> requestHeaders) {
        return config.checkHeaders(requestHeaders);
    }

    private List<String> getHeadersToUse(ServerHttpRequest request, boolean isPreFlight) {
        HttpHeaders headers = request.getHeaders();
        return (isPreFlight ? headers.getAccessControlRequestHeaders() : new ArrayList<>(headers.keySet()));
    }

}
