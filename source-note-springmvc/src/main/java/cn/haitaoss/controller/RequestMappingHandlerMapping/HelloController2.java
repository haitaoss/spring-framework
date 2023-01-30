package cn.haitaoss.controller.RequestMappingHandlerMapping;

import cn.haitaoss.entity.Person;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-15 21:11
 */
@Controller
@RequestMapping("2")
@SessionAttributes(value = "desc")
@Validated
public class HelloController2 {

    @ModelAttribute("desc")
    public String init_desc(@Value("haitao") String name, @Value("23") String age) {
        System.out.println("init_name...");
        return name + age;
    }

    // @InitBinder 不指定 name，说明可以应用于当前类的 所有 @ModelAttribute 的方法被回调了
    @InitBinder("desc")
    public void init_binder(WebDataBinder webDataBinder) {
        System.out.println("init_binder...");
        /*webDataBinder.setConversionService(null); // 做类型转换的
        webDataBinder.setValidator(null); // 进行JSR303校验的*/
    }

    /**
     * http://127.0.0.1/2/test_model_attribute?name=haitao&age=23
     *
     * 比如 index() 方法，期望拿到 desc 的值，但是这个值是需要加工的，期望是拼接上name和age两个参数的值作为desc的值，
     * 但是不太方便在前端加工、也不方便在 index() 方法内加工，就想直接作为入参拿到。
     *
     * 这时候就可使用 @ModelAttribute("desc") 标注在方法上，在调用 index 之前，会先回调 init_desc() 方法，将方法的返回值，存到
     * ModelAndViewContainer(方法执行的上下文对象)，存的key就是 desc
     *
     * 所以 index( @ModelAttribute("desc") String desc ) 就可以拿到我们拼接的结果。
     *
     * 再来一个需求，一个session中 desc只需要加工一次，整个会话共享。这个时候就可以使用 @SessionAttributes(value = "desc") 标注在类上，
     * 作用一：多一步校验，若 @ModelAttribute("desc") 的值在session域中存在了，那就不需要 回调 init_desc() 方法了，直接返回session域中的值，
     * 作用二：在 index() 方法执行完后，会从 ModelAndViewContainer 拿到 desc 的值，存到session域中
     *
     * 注：
     * 1. @ModelAttribute、@InitBinder 写在标注了 @Controller 的类中，表示只可以作用于当前类的方法
     * 2. @ModelAttribute、@InitBinder 写在标注了 @ControllerAdvice 的类中，表示是全局的
     * 3. @ModelAttribute("desc") 准备回调方法时，会使用HandlerMethodArgumentResolver解析参数列表的每个参数，参数解析完后会 new WebDataBinder() 对参数值进行类型转换
     * 在 WebDataBinder 的实例化时，会回调 @InitBinder 标注的方法，做些初始化操作
     */
    @RequestMapping("/test_model_attribute")
    public String index(Model model, @ModelAttribute("desc") String desc) {
        model.addAttribute("data", desc);
        return "index";
    }


    @RequestMapping("index2")
    @ResponseBody
    public String test_requestBody(@RequestBody /*@Valid*/ /*@Validated*/ @NotNull Person person,
                                   @NotNull @Value("haitao") String name) {
        System.out.println("person = " + person);
        return "ok";
    }

    @RequestMapping("index3")
    @ResponseBody
    public String index3(@RequestParam(value = "name",
            required = false) Object obj, Integer age) {
        System.out.println("index3");
        return "ok";
    }


    /**
     * 已知, consumes 匹配的是 请求头中的 Content-Type 的值,produces 匹配的是 请求头中的 Accept 的值
     *
     * 问题：都是从请求头中获取的，为啥一个叫 consume(消费) 一个叫produce(生产)？
     *
     * 请求体映解析成JavaBean , JavaBean输出到响应体 这两种转换是使用 HttpMessageConverter 接口来处理的
     * {@link HttpMessageConverter#canRead(Class, MediaType)}   ---> 用来判断是否支持这个 MediaType
     * {@link HttpMessageConverter#canWrite(Class, MediaType)}  ---> 用来判断是否支持这个 MediaType
     *
     * 站在程序的角度，来看 read 就是读取请求体，write 就是将内容输出到响应体。
     *
     * canRead 方法的MediaType参数，是读取请求头的 Content-Type 得到的
     * canWrite 方法的MediaType参数，是读取响应体的 Content-Type 得到的，或者是请求头的 Accept 得到
     * 所以才命名为 consumes 和 produces！！！
     *
     * 比如发送的请求的请求头是：
     *      Content-Type: text/plain
     *      Accept: application/json
     *
     * 那么解析@RequestBody 用的 HttpMessageConverter 应当是直接将请求体内容转换成String即可
     * 解析@ResponseBody 用的 HttpMessageConverter 应当是直接将返回值转成JSON字符串，然后将字符串写入到响应体即可
     * */
    @RequestMapping(value = "index4", consumes = MediaType.TEXT_PLAIN_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String index4() {
        System.out.println("index4");
        return "ok";
    }
}
