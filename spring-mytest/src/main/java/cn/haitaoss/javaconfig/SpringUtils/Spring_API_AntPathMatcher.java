package cn.haitaoss.javaconfig.SpringUtils;

import org.springframework.util.AntPathMatcher;

import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 11:31
 *
 */
public class Spring_API_AntPathMatcher {

    public static void main(String[] args) throws IOException {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        System.out.println(antPathMatcher.isPattern("**/"));
    }
}
