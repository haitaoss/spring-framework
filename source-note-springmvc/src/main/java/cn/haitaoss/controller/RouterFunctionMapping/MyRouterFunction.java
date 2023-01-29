package cn.haitaoss.controller.RouterFunctionMapping;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Optional;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-25 20:38
 */
@Component
public class MyRouterFunction implements RouterFunction<ServerResponse> {


    @Override
    public Optional<HandlerFunction<ServerResponse>> route(ServerRequest request) {
        if (!request.path()
                .contains("router")) {
            // 这就表示不处理的意思
            return Optional.empty();
        }
        System.out.println("MyRouterFunction.route...");
        HandlerFunction<ServerResponse> handlerFunction = x -> ServerResponse.ok()
                //                .build();
                .body("haitao");
        return Optional.of(handlerFunction);
    }
}
