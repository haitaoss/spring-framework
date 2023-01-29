package cn.haitaoss.javaconfig.EnableLoadTimeWeaving;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.core.annotation.Order;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-12-08 08:49
 *
 */
@Component
@Order(Integer.MAX_VALUE)
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor, LoadTimeWeaverAware {
    private LoadTimeWeaver loadTimeWeaver;
    private String dir = "/Users/haitao/Desktop/";
    //    private String dir = "C:\\Users\\RDS\\Desktop\\code\\";

    @Override
    public void setLoadTimeWeaver(LoadTimeWeaver loadTimeWeaver) {
        this.loadTimeWeaver = loadTimeWeaver;
        extendTransformer();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {}

    private void extendTransformer() {
        // 添加自定义了 ClassFileTransformer
        loadTimeWeaver.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain,
                                    byte[] classfileBuffer) throws IllegalClassFormatException {
                // 包名包含 haitaoss
                if (className.contains("haitaoss")) {
                    File file = new File(dir + className + ".class");
                    new File(file.getParent()).mkdirs();
                    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                        // 输出本地文件中
                        fileOutputStream.write(classfileBuffer);
                        fileOutputStream.flush();
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                    }
                }
                // 表示不转换
                return null;
            }
        });
    }
}
