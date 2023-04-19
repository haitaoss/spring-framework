# 资料

[ Spring WebFlux 官方文档](https://docs.spring.io/spring-framework/docs/5.3.10/reference/html/web-reactive.html)

[响应式API官方文档](https://projectreactor.io/docs/core/release/reference/)

# 源码分析

[示例代码](../source-note-spring-webflux/src/main/java/cn/haitoass/Main.java)

```java
/**
 * 思路 SpringMVC 可以说是类似，只不过注册的是 ServletHttpHandlerAdapter 而不是 DispatcherServlet
 *
 * 1. @EnableWebFlux 主要是通过 @Bean 注册了 RequestMappingHandlerAdapter、RequestMappingHandlerMapping、DispatcherHandler
 *      最主要是 DispatcherHandler ，它是 WebHandler 类型的 bean ，是 webflux 处理请求的入口（ Servlet 会将请求委托给 WebHandler 处理）。
 *      DispatcherHandler 会依赖 HandlerAdapter、HandlerMapping 处理收到的请求，和 DispatcherServlet 的处理逻辑一致，只不过是少了
 *      HandlerExceptionResolver 和 ViewResolver 而已。
 *
 *      注：是支持 @Controller、@RestController、@RequestMapping 来定义请求的处理逻辑
 *
 * 2. 对于独立Web应用的场景，spring-web.jar 中提供了 AbstractReactiveWebInitializer 这个抽象类，定义了将 Servlet 注册到 web 容器的逻辑
 *
 *      // 对 WebHandler 层层装饰
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

大致的调用链路

```java
/**
 * {@link ServletHttpHandlerAdapter#service(ServletRequest, ServletResponse)}
 * {@link HttpWebHandlerAdapter#handle(ServerHttpRequest, ServerHttpResponse)}
 * {@link ExceptionHandlingWebHandler#handle(ServerWebExchange)}
 * {@link FilteringWebHandler#handle(ServerWebExchange)}
 * {@link DispatcherHandler#handle(ServerWebExchange)}
 *      到了这里就是使用 HandlerMapping、HandlerAdapter、HandlerResultHandler 处理逻辑，
 *      逻辑和 DispatcherServlet 类似的
 * */
```