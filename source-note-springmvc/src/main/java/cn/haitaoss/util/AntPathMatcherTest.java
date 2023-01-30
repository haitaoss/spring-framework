package cn.haitaoss.util;

import org.springframework.util.AntPathMatcher;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-30 10:50
 *
 */
public class AntPathMatcherTest {
    public static void main(String[] args) {
        AntPathMatcher antPathMatcher = new AntPathMatcher();

        System.out.println("校验路径匹配");
        System.out.println(antPathMatcher.isPattern("/index"));
        System.out.println(antPathMatcher.isPattern("/*"));

        System.out.println("提取占位符");
        System.out.println(antPathMatcher.extractUriTemplateVariables("/user/9{id}", "/user/9527"));


    }
}
