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

package org.springframework.web.filter;

import org.springframework.util.Assert;
import org.springframework.web.cors.*;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * {@link javax.servlet.Filter} to handle CORS pre-flight requests and intercept
 * CORS simple and actual requests with a {@link CorsProcessor}, and to update
 * the response, e.g. with CORS response headers, based on the policy matched
 * through the provided {@link CorsConfigurationSource}.
 *
 * <p>This is an alternative to configuring CORS in the Spring MVC Java config
 * and the Spring MVC XML namespace. It is useful for applications depending
 * only on spring-web (not on spring-webmvc) or for security constraints that
 * require CORS checks to be performed at {@link javax.servlet.Filter} level.
 *
 * <p>This filter could be used in conjunction with {@link DelegatingFilterProxy}
 * in order to help with its initialization.
 *
 * @author Sebastien Deleuze
 * @see <a href="https://www.w3.org/TR/cors/">CORS W3C recommendation</a>
 * @see UrlBasedCorsConfigurationSource
 * @since 4.2
 */
public class CorsFilter extends OncePerRequestFilter {

    private final CorsConfigurationSource configSource;

    private CorsProcessor processor = new DefaultCorsProcessor();


    /**
     * Constructor accepting a {@link CorsConfigurationSource} used by the filter
     * to find the {@link CorsConfiguration} to use for each incoming request.
     *
     * @see UrlBasedCorsConfigurationSource
     */
    public CorsFilter(CorsConfigurationSource configSource) {
        Assert.notNull(configSource, "CorsConfigurationSource must not be null");
        this.configSource = configSource;
    }


    /**
     * Configure a custom {@link CorsProcessor} to use to apply the matched
     * {@link CorsConfiguration} for a request.
     * <p>By default {@link DefaultCorsProcessor} is used.
     */
    public void setCorsProcessor(CorsProcessor processor) {
        Assert.notNull(processor, "CorsProcessor must not be null");
        this.processor = processor;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        /**
         * configSource 是构造器设置的属性，注册了 <Request,CorsConfiguration> 的信息
         *
         * 比如使用这个 UrlBasedCorsConfigurationSource 的注册信息是 Map<PathPattern, CorsConfiguration>
         * 可使用 key与请求路径，进行Ant风格的路径匹配
         * {@link UrlBasedCorsConfigurationSource#getCorsConfiguration(HttpServletRequest)}
         * */
        CorsConfiguration corsConfiguration = this.configSource.getCorsConfiguration(request);
        /**
         * 处理预检请求，若是预检请求 就根据 corsConfiguration 的信息匹配 request
         *
         * true 表示合法的
         * */
        boolean isValid = this.processor.processRequest(corsConfiguration, request, response);
        /**
         * 请求信息不满足跨域配置 或者 是预检请求就直接return
         *
         * 注：processRequest 处理的逻辑中会设置好响应体，所以这里可以直接return
         * */
        if (!isValid || CorsUtils.isPreFlightRequest(request)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

}
