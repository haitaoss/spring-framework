# 资料

[ Spring WebFlux 官方文档](https://docs.spring.io/spring-framework/docs/5.3.10/reference/html/web-reactive.html)

[响应式API官方文档](https://projectreactor.io/docs/core/release/reference/)

[示例代码](../source-note-spring-webflux/src/main/java/cn/haitoass/Main.java)

# 源码分析

## @EnableWebFlux

```java
/**
 * 思路 SpringMVC 可以说是类似，只不过注册的是 ServletHttpHandlerAdapter 而不是 DispatcherServlet
 *
 * @EnableWebFlux 主要是通过 @Bean 注册了 RequestMappingHandlerAdapter、RequestMappingHandlerMapping、DispatcherHandler
 *      最主要是 DispatcherHandler ，它是 WebHandler 类型的 bean ，是 webflux 处理请求的入口（ Servlet 会将请求委托给 WebHandler 处理）。
 *      DispatcherHandler 会依赖 HandlerAdapter、HandlerMapping 处理收到的请求，和 DispatcherServlet 的处理逻辑一致，只不过是少了
 *      HandlerExceptionResolver 和 ViewResolver 而已。
 *
 *      注：是支持 @Controller、@RestController、@RequestMapping 来定义请求的处理逻辑
 *
 * */
```

## AbstractReactiveWebInitializer

[建议先看 SpringMVC 一下独立web应用的启动流程](springmvc-source-note.md#嵌入式Tomcat启动流程) 

```java
/**
 * 对于独立Web应用的场景，spring-web.jar 中提供了 AbstractReactiveWebInitializer 这个抽象类，定义了将 Servlet 注册到 web 容器的逻辑
 *      {@link AbstractReactiveWebInitializer#onStartup(ServletContext)}
 *
 *      // 对 WebHandler 层层装饰
 *      FilteringWebHandler 代理 WebHandler (默认配置的是 DispatcherHandler), 增加了使用  WebFilter 的逻辑
 *      ExceptionHandlingWebHandler 代理 FilteringWebHandler, 增加了使用 WebExceptionHandler 的逻辑
 *      HttpWebHandlerAdapter 代理 ExceptionHandlingWebHandler, 增加了 自定义 request、装饰request和response 的逻辑
 *      最终暴露出去的是 HttpWebHandlerAdapter
 *          
 *      WebHandler decorated = new FilteringWebHandler( DispatcherHandler, List<WebFilter> );
 *      decorated = new ExceptionHandlingWebHandler(decorated,  List<WebExceptionHandler> );
 *      HttpWebHandlerAdapter adapted = new HttpWebHandlerAdapter(decorated);
 *
 *      // 依赖 WebHandler 生成 Servlet
 *      ServletHttpHandlerAdapter servlet = new ServletHttpHandlerAdapter(adapted);
 *
 *      // 将 servlet 注册到 web容器中
 *      servletContext.addServlet(servletName, servlet)
 *
 *      PS: 很简单吧。有了 SpringMVC 的基础再看 Spring-WebFlux 实在是太轻松了，只不过使用的 响应式API不大懂
 *
 * 注：用到了响应式的API，我也没接触过，用到在了解吧，暂时弄明白流程就好了。
 * */
```

## ServletHttpHandlerAdapter#service

```java
/**
 * ServletHttpHandlerAdapter 的执行流程
 * {@link ServletHttpHandlerAdapter#service(ServletRequest, ServletResponse)}
 *  1. 开启异步
 *      AsyncContext asyncContext = request.startAsync();
 *
 *  2. 注册 监听器。完成请求时会回调监听器的生命周期方法
 *      asyncContext.addListener(new HttpHandlerAsyncListener(...))
 *
 *  3. 委托给 HttpWebHandlerAdapter 执行
 *      httpHandler.handle(httpRequest, httpResponse).subscribe(subscriber);
 *
 * HttpWebHandlerAdapter 大致的执行逻辑如下：
 * {@link HttpWebHandlerAdapter#handle(ServerHttpRequest, ServerHttpResponse)}
 *    1. 使用 ForwardedHeaderTransformer 用来对 request 进行自定义
 *       request = this.forwardedHeaderTransformer.apply(request);
 *
 *    2. 构造出 ServerWebExchange
 *       ServerWebExchange exchange = createExchange(request, response);
 *
 *    3. 委托给 ExceptionHandlingWebHandler 执行
 *       {@link ExceptionHandlingWebHandler#handle(ServerWebExchange)}
 *       
 *       3.1 委托给 FilteringWebHandler 执行拿到返回值
 *            {@link FilteringWebHandler#handle(ServerWebExchange)}
 *           其实就是迭代执行 WebFilter，然后再委托给 DispatcherHandler 执行
 *
 *       3.2 处理异常信息
 *          // 遍历 exceptionHandlers 处理异常
 *          for (WebExceptionHandler handler : this.exceptionHandlers) {
 *              completion = completion.onErrorResume(ex -> handler.handle(exchange, ex));
 *          }
 * */
```

## DispatcherHandler#handle

```java
@Override
public Mono<Void> handle(ServerWebExchange exchange) {
   if (this.handlerMappings == null) {
      return createNotFoundError();
   }

   // 针对 预检 请求的处理
   if (CorsUtils.isPreFlightRequest(exchange.getRequest())) {
      return handlePreFlight(exchange);
   }

   /**
    * 和 SpringMVC 是类似的。
    *
    * 1. 使用 {@link HandlerMapping#getHandler(ServerWebExchange)} 得到 handler
    * 2. 遍历 {@link HandlerAdapter#supports(Object)} 找到适配的，就用来执行 handler
    * 3. 遍历 {@link HandlerResultHandler#supports(HandlerResult)} 找到适配的，就用来处理 返回结果
    * 
    * Tips：
    *     - 使用 @EnableWebFlux 会注册 RequestMappingHandlerMapping、RequestMappingHandlerAdapter 是用来处理
    *        @Controller、@RequestMapping、@InitBinder、@SessionAttribute、@ModelAttribute 的,功能和SpringMVC是一致的
    *     
    *     - 会注册 ViewResolutionResultHandler 是用来支持 视图的
    * */
   return Flux.fromIterable(this.handlerMappings)
         // concatMap 会收集不是 Mono.Empty() 的内容 组成 Flux
         .concatMap(mapping -> mapping.getHandler(exchange))
         .next()
         .switchIfEmpty(createNotFoundError())
         .flatMap(handler -> invokeHandler(exchange, handler))
         .flatMap(result -> handleResult(exchange, result));
}
```