package cn.haitaoss;

import cn.haitaoss.config.WebServletConfig;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.FrameworkServlet;

import javax.servlet.ServletContext;
import java.io.File;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-15 20:06
 */
public class Main {
    public static void main(String[] args) throws Exception {
       /* AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.refresh();
        System.out.println(Arrays.toString(context.getBeanDefinitionNames()));*/
        /**
         * mvc官方教程 https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc
         * */
        /**
         * 启动 Tomcat
         *
         * 参考资料:
         *  {@link org.springframework.web.socket.TomcatWebSocketTestServer#setup()}
         *  http://home.apache.org/~markt/presentations/2010-11-04-Embedding-Tomcat.pdf
         * */
        startTomcat();

        test_code();
    }


    public static void startTomcat() throws Exception {
        // 创建内嵌的Tomcat
        Tomcat tomcatServer = new Tomcat();

        // 设置Tomcat端口
        tomcatServer.setPort(8080);

        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(8080);
        tomcatServer.getService()
                .addConnector(connector);
        tomcatServer.setConnector(connector);

        // 读取项目路径，加载项目资源
        StandardContext ctx = (StandardContext) tomcatServer.addWebapp(
                "/mvc", new File("source-note-springmvc/src/main/webapp").getAbsolutePath());

        // 不重新部署加载资源
        ctx.setReloadable(false);

        // 创建 WebRoot
        WebResourceRoot resources = new StandardRoot(ctx);

        // 指定编译后的 class 文件位置
        File additionalWebInfClasses = new File("source-note-springmvc/out/production/classes");

        // 添加web资源
        resources.addPreResources(new DirResourceSet(resources, "/", additionalWebInfClasses.getAbsolutePath(), "/"));

        // 启动内嵌的Tomcat
        tomcatServer.start();

        Thread thread = new Thread(() -> {
            // 堵塞，不退出程序
            tomcatServer.getServer()
                    .await();
        });
        thread.setDaemon(false);
        thread.start();
    }

    public static void test_code() {
        /**
         *  父子容器的好处？
         *      1. 对象隔离
         *      2. 扩展容器的功能？
         * */
        ApplicationContext son = WebServletConfig.webApplicationContext;
        WebApplicationContext parent = (WebApplicationContext) son.getParent();
        System.out.println("Parent-Context: " + parent);
        ServletContext servletContext = parent.getServletContext();
        System.out.println(
                "Parent-Context: " + servletContext.getAttribute(WebApplicationContext.class.getName() + ".ROOT"));
        System.out.println(
                "Son-Context: " + servletContext.getAttribute(FrameworkServlet.SERVLET_CONTEXT_PREFIX + "dispatcher"));

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        // 执行bean的初始化操作(XxxAware、初始化前后置处理器、初始化方法、初始化后后置处理器)
        context.getBeanFactory()
                .initializeBean(new Object(), "index");

    }
}