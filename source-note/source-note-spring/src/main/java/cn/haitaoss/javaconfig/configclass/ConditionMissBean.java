package cn.haitaoss.javaconfig.configclass;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-12 10:40
 *
 */
public class ConditionMissBean implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Object beanName = metadata.getAnnotationAttributes(ConditionalOnMissBean.class.getName())
                .get("value");

        return !context.getBeanFactory()
                .containsBean(beanName + "");
    }
}
