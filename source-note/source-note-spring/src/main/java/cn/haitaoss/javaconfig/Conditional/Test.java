package cn.haitaoss.javaconfig.Conditional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-09-29 21:08
 */

/**
 * 注意：
 * {@link ClassPathScanningCandidateComponentProvider#isConditionMatch(MetadataReader)}
 * {@link ClassPathScanningCandidateComponentProvider#isConditionMatch(MetadataReader)}
 * {@link ConditionEvaluator#shouldSkip(AnnotatedTypeMetadata, *  ConfigurationCondition.ConfigurationPhase)}
 * {@link Condition#matches(ConditionContext, AnnotatedTypeMetadata)}
 *      第一个参数是当前的BeanFactory，也就是BeanFactory并不完整，要想保证 @Conditional 能正确判断，应当保证 bean 注册到 BeanFactory 的先后顺序
 */
@Component
public class Test {
    static class A {}

    static class MyCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return true;
        }
    }

    @Bean
    @Conditional(MyCondition.class)
    public A a() {
        return new A();
    }
}


