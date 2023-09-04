package cn.haitoass.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.HandlerResultHandler;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-19 14:18
 *
 * 看这里知道 支持那些配置类
 *      {@link WebHttpHandlerBuilder#applicationContext}
 *      WebHandler、WebFilter、WebExceptionHandler、HttpHandlerDecoratorFactory、WebSessionManager、
 *      ServerCodecConfigurer、LocaleContextResolver、ForwardedHeaderTransformer
 *
 * 看这处源码，就知道为啥配置支持 SpringMVC 中的 大部分功能了（不支持@ExceptionHandler）
 *      {@link DispatcherHandler#initStrategies(ApplicationContext)}
 *      HandlerMapping、HandlerAdapter、HandlerResultHandler
 */
@Component
public class WebFluxConfig {
    @Bean
    public WebFilter webFilter() {
        return new WebFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                System.out.println("WebFilter.filter...");
                return chain.filter(exchange);
            }
        };
    }

    @Bean
    public WebExceptionHandler webExceptionHandler() {
        return new WebExceptionHandler() {
            @Override
            public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
                System.out.println("WebExceptionHandler.handle...");
                throw new RuntimeException(ex);
            }
        };
    }

    @Bean
    public HandlerResultHandler handlerResultHandler() {
        return new HandlerResultHandler() {
            @Override
            public boolean supports(HandlerResult result) {
                return false;
            }

            @Override
            public Mono<Void> handleResult(ServerWebExchange exchange, HandlerResult result) {
                return null;
            }
        };
    }

}
