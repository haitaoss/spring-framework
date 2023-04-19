package cn.haitoass.config;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-19 14:18
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

}
