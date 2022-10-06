package cn.haitaoss.javaconfig.aop;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.adapter.AdvisorAdapter;
import org.springframework.stereotype.Component;


/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-10-06 15:21
 *
 */

/*
// AdvisorAdapterRegistrationManager 是用来将 AdvisorAdapter 注册到 {@link AbstractAutoProxyCreator#advisorAdapterRegistry}
@Import(AdvisorAdapterRegistrationManager.class)
// 要保证myAdvisorAdapter优先于切面类之前创建，否则没用
@DependsOn("myAdvisorAdapter")
@EnableAspectJAutoProxy
@Component*/
public class AopTest6 {
    @Component
    @SuppressWarnings("serial")
    class MyAdvice2 implements Advice {
    }

    @Component
    @SuppressWarnings("serial")
    class MyAdvisorAdapter implements AdvisorAdapter {

        @Override
        public boolean supportsAdvice(Advice advice) {
            return advice instanceof MyAdvice2;
        }

        @Override
        public MethodInterceptor getInterceptor(Advisor advisor) {
            return MethodInvocation::proceed;
        }

    }
}
