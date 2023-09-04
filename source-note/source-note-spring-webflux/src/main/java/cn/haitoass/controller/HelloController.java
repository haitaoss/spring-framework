package cn.haitoass.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-19 10:26
 *
 */
@RestController
public class HelloController {
    @RequestMapping("/")
    public Object index() {
        return "ok...";
    }
}
