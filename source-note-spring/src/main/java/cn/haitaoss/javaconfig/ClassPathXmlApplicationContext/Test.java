package cn.haitaoss.javaconfig.ClassPathXmlApplicationContext;

import org.springframework.beans.factory.xml.BeanDefinitionParserDelegate;
import org.springframework.beans.factory.xml.DefaultBeanDefinitionDocumentReader;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.w3c.dom.Element;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-02-01 15:36
 *
 */
public class Test {

    /**
     * 要不讲讲xml的解析流程。大致思路是根据标签名字得到 得到对应的 Parser 然后parser其实就是注册BeanDefinition到容器中
     * {@link DefaultBeanDefinitionDocumentReader#parseBeanDefinitions(Element, BeanDefinitionParserDelegate)}
     * 1. 是默认命名空间
     *      {@link DefaultBeanDefinitionDocumentReader#parseDefaultElement(Element, BeanDefinitionParserDelegate)}
     *
     * 2. 不是默认命名空间，使用 NameSpaceHandler 进行解析
     *
     * */
    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:spring6.xml");
    }
}
