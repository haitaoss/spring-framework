package cn.haitaoss.javaconfig.SpringUtils;

import java.io.IOException;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 16:56
 *
 */
public class JDK_API_Resource {
    public static void main(String[] args) throws IOException {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

        // 使用ClassLoader 获取一个资源
        contextClassLoader.getResource("classpath:db.sql"); // 不支持
        contextClassLoader.getResource("classpath*:db.sql"); // 不支持
        contextClassLoader.getResource("/db.sql"); // 不支持
        contextClassLoader.getResource("db.sql");
        contextClassLoader.getResource("db.sql");
        contextClassLoader.getResource("META-INF");

        // 使用ClassLoader 获取多个资源
        contextClassLoader.getResources("META-INF"); // 返回全部，就是会找到依赖的jar里面的所有资源

    }
}
