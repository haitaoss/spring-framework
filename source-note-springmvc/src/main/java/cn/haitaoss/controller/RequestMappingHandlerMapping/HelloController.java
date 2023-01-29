package cn.haitaoss.controller.RequestMappingHandlerMapping;

import cn.haitaoss.scope.ApplicationBean;
import cn.haitaoss.scope.RequestBean;
import cn.haitaoss.scope.SessionBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.annotation.ModelFactory;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import java.util.Date;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-15 21:11
 */
@Controller
//@RequestMapping("")
@SessionAttributes(value = "SESSIONID")
public class HelloController {
    @Autowired
    private AnnotationConfigWebApplicationContext applicationContext;


    /**
     * 需要在一个请求中才能使用，用的时候在获取值，所以得使用得使用 @Lazy或者@Lookup，注入个代理对象，
     * 真正执行方法的时候再从容器中获取依赖注入的值
     *
     * 注：假设 不使用 懒加载的方式 处理这两个依赖，当还没有收到请求(ThreadLocal中没记录请求信息)，就实例化 HelloController
     * 这直接就报错
     */
    @Autowired
    @Lazy
    private RequestBean requestBean;
    @Autowired
    @Lazy
    private SessionBean sessionBean;

    // 可以直接使用，不需要在一个请求中就能使用
    @Autowired
    private ApplicationBean applicationBean;

    //    @RequestMapping("/index")
    //    @RequestMapping("${data:/index}")
    @RequestMapping(value = {"index", "/"})
    public String index(Model model) {
        model.addAttribute("data", "haitao");
        return "index";
    }

    @RequestMapping(value = {"index2"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            //            headers = "haitao",
            produces = MediaType.TEXT_HTML_VALUE)
    public String index2(Model model) {
        model.addAttribute("data", "haitao");
        return "index";
    }

    @RequestMapping(value = {"index/**"})
    public String index3(Model model) {
        model.addAttribute("data", "index3");
        return "index";
    }

    @RequestMapping(value = {"index4"})
    public String index4(String data, @Value("20000105") Date date,
                         @Value("${undefine:haitaoss}") ApplicationContext ioc, ModelAndView modelAndView) {
        System.out.println(String.format("index4...data is : %s, date is : %s, ioc is : %s", data, date, ioc));
        return "index";
    }

    @RequestMapping(value = {"index5"})
    @ResponseBody
    public String index5() {
        System.out.println("index5...");
        return "index";
    }

    @RequestMapping(value = {"index6"})
    public String index6(RedirectAttributes redirectAttributes) {
        System.out.println("index6...");
        redirectAttributes.addFlashAttribute("data", "哈哈");
        return "index";
    }

    @RequestMapping(value = {"index7/{id}"})
    public String index7(Model model, @PathVariable String id) {
        System.out.println("index7...");
        model.addAttribute("test_updateBindingResult", new DispatcherServlet());
        /**
         * {@link ModelFactory#updateBindingResult(NativeWebRequest, ModelMap)}
         * */
        return "index";
    }

    @RequestMapping(value = {"test_default_view"})
    public void test_default_view() {
        System.out.println("test_default_view...");
    }

    @RequestMapping(value = {"test_exception"})
    public void test_exception() {
        System.out.println("test_exception...");
        throw new RuntimeException("测试全局异常");
    }

    @RequestMapping(value = {"test_redirect"})
    public String test_redirect(Model model) {
        System.out.println("test_redirect...");
        model.addAttribute("path", "index");
        model.addAttribute("q1", "haitao");
        return "redirect:/{path}";
    }

    @RequestMapping(value = {"test_forward"})
    public String test_forward(Model model) {
        System.out.println("test_forward...");
        model.addAttribute("path", "index");
        // 不支持占位符
        return "forward:index";
    }

    @RequestMapping(value = {"test_view_resolver"})
    public String test_view_resolver(Model model) {
        return "HTML:index";
    }

    @RequestMapping(value = {"test_validated"})
    public String test_validated(@Value("15") @Valid @Max(10) Integer age) {
        return "HTML:index";
    }
}
