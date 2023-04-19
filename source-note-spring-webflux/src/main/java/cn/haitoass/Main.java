package cn.haitoass;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.adapter.AbstractReactiveWebInitializer;

import java.io.File;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-19 10:08
 *
 */
@EnableWebFlux
@ComponentScan
public class Main extends AbstractReactiveWebInitializer {

    @Override
    protected Class<?>[] getConfigClasses() {
        return new Class[]{Main.class};
    }

    public static void main(String[] args) throws Exception {
        startTomcat();
    }

    public static void startTomcat() throws Exception {
        // 创建内嵌的Tomcat
        Tomcat tomcatServer = new Tomcat();

        Connector connector = new Connector(Http11NioProtocol.class.getName());
        connector.setPort(8080);
        tomcatServer.setConnector(connector);

        // 读取项目路径，加载项目资源
        StandardContext ctx = (StandardContext) tomcatServer.addWebapp(
                "/webflux",
                new File("source-note-spring-webflux/src/main/webapp").getAbsolutePath());

        // 不重新部署加载资源
        ctx.setReloadable(false);

        // 创建 WebRoot
        WebResourceRoot resources = new StandardRoot(ctx);

        // 指定编译后的 class 文件位置
        File additionalWebInfClasses = new File("source-note-spring-webflux/out/production/classes");

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
}
