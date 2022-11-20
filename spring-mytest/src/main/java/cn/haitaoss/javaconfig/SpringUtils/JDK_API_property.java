package cn.haitaoss.javaconfig.SpringUtils;

import java.util.Map;
import java.util.Properties;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 16:56
 *
 */
public class JDK_API_property {
    public static void main(String[] args) {
        /**
         * 测试 JDK API,Spring读取属性 会使用到下面两个API来获取系统设置的属性信息
         * */
        // 环境变量
        Map<String, String> envMap = System.getenv();
        System.out.println("envMap = " + envMap);

        System.out.println("========================================");

        // 系统变量
        Properties properties = System.getProperties();
        System.out.println("properties = " + properties);

    }
}
