package cn.haitaoss.javaconfig.SpringUtils;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.function.Function;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-11-18 11:31
 *
 */
public class Spring_API_ResourceLoader {
    public static void main(String[] args) throws IOException {

        /*ApplicationContext context = new AnnotationConfigApplicationContext();
        context.getResource("classpath:demo.xml");
        context.getResources("classpath*:demo.xml");*/

        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Function<String, Resource> fun = resourceLoader::getResource;

        /**
         * 读类路径下的文件(就是通过ClassLoader获取文件)
         *
         * 注：
         *  1. 都是利用 ClassLoader 加载文件
         *  2. 写不写 classpath: 都一样，写了就会除去 classpath: 前缀，然后在使用ClassLoader加载文件
         *  3. 前缀 / 会被移除，因为 {@link ClassLoader#getResource(String)} 不支持
         * */
        System.out.println(fun.apply("classpath:db.sql").exists());
        System.out.println(fun.apply("classpath:/db.sql").exists());
        System.out.println(fun.apply("db.sql").exists());
        System.out.println(fun.apply("/db.sql").exists());
        System.out.println(fun.apply("classpath*:db.sql").exists()); // 不支持

        // 网络资源
        System.out.println(fun.apply("https://www.baidu.com/").exists());
        // 读本地文件
        System.out.println(fun.apply("file:///Users/haitao/Desktop/1.md").exists()); // 找系统文件

        // 是 ResourceLoader 的实现类，支持写Ant分隔的路径符 返回匹配的资源数组
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        /**
         *  很简单，使用JDK API {@link ClassLoader#getResources(String)} 所以能获取到依赖jar里面的资源
         * */
        Resource[] resources = resolver.getResources("classpath*:org/springframework/core/io/sup*/*.class");
        System.out.println("resources = " + resources.length);

        // 其实就是使用 DefaultResourceLoader 实现的
        System.out.println(resolver.getResource("classpath:db.sql").exists());
    }
}
