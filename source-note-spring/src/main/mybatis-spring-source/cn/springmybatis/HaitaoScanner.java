package cn.springmybatis;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import java.util.Set;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-07-31 21:18
 */
public class HaitaoScanner extends ClassPathBeanDefinitionScanner {
    public HaitaoScanner(BeanDefinitionRegistry registry) {
        super(registry);
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return beanDefinition.getMetadata()
                .isInterface();
    }

    @Override
    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitionHolders = super.doScan(basePackages);

        beanDefinitionHolders.forEach(item -> {
            BeanDefinition beanDefinition = item.getBeanDefinition();
            beanDefinition.getConstructorArgumentValues()
                    .addGenericArgumentValue(beanDefinition.getBeanClassName());
            /*try {
                beanDefinition.getConstructorArgumentValues()
                        .addGenericArgumentValue(Class.forName(beanDefinition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }*/
            beanDefinition.setBeanClassName(HaitaoFactoryBean.class.getName());
        });

        return beanDefinitionHolders;
    }
}
