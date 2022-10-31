/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.transaction.annotation;

import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.autoproxy.BeanFactoryAdvisorRetrievalHelper;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.lang.reflect.Method;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans
 * necessary to enable proxy-based annotation-driven transaction management.
 *
 * @author Chris Beams
 * @author Sebastien Deleuze
 * @since 3.1
 * @see EnableTransactionManagement
 * @see TransactionManagementConfigurationSelector
 */
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProxyTransactionManagementConfiguration extends AbstractTransactionManagementConfiguration {
    /**
     * transactionAdvisor 就是 @EnableTransactionManagement 的增强器，而 @Role(BeanDefinition.ROLE_INFRASTRUCTURE) 也是有用的
     * 因为 InfrastructureAdvisorAutoProxyCreator 只会使用Role的值是 {@link BeanDefinition.ROLE_INFRASTRUCTURE} 的 Advisor 来判断后置处理的bean是否需要代理
     *        {@link BeanFactoryAdvisorRetrievalHelper#findAdvisorBeans()}
     * */
    @Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor(
            TransactionAttributeSource transactionAttributeSource, TransactionInterceptor transactionInterceptor) {
        /**
         * 实现 PointcutAdvisor 接口，所以是否要进行代理得看 {@link PointcutAdvisor#getPointcut()}
         * 而 BeanFactoryTransactionAttributeSourceAdvisor 的 Pointcut是这个 {@link TransactionAttributeSourcePointcut}
         * 而 TransactionAttributeSourcePointcut 类匹配和方法匹配是使用 transactionAttributeSource 来解析注解的
         *      - ClassFilter {@link TransactionAttributeSourcePointcut.TransactionAttributeSourceClassFilter}
         *          类不是java包下的 不是 Ordered类 就是匹配
         *
         *      - MethodMatcher {@link TransactionAttributeSourcePointcut#matches(Method, Class)}
         *          查找 方法->方法声明的类 有@Transactional 就是匹配
         * */
        BeanFactoryTransactionAttributeSourceAdvisor advisor = new BeanFactoryTransactionAttributeSourceAdvisor();

        // 默认是注册的这个类型 AnnotationTransactionAttributeSource
        advisor.setTransactionAttributeSource(transactionAttributeSource);
        /**
         * Advice, 也就是具体的增强逻辑
         * */
        advisor.setAdvice(transactionInterceptor);
        if (this.enableTx != null) {
            /**
             * 设置排序值
             * */
            advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
        }
        return advisor;
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TransactionAttributeSource transactionAttributeSource() {
        /**
         * Advisor 和 Advice 都依赖了这个bean。
         * TransactionAttributeSource 该对象很简单，就是解析 @Transactional 注解，解析成 RuleBasedTransactionAttribute 对象
         * */
        return new AnnotationTransactionAttributeSource();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TransactionInterceptor transactionInterceptor(TransactionAttributeSource transactionAttributeSource) {
        /**
         * 就是 BeanFactoryTransactionAttributeSourceAdvisor 的 advice
         * */
        TransactionInterceptor interceptor = new TransactionInterceptor();
        /**
         * 依赖了 transactionAttributeSource，这东西是用来拿到方法、类上的 @Transactional注解，解析成 RuleBasedTransactionAttribute 对象
         * */
        interceptor.setTransactionAttributeSource(transactionAttributeSource);
        if (this.txManager != null) {
            /**
             * 设置事务管理器，该属性值是父类依赖注入 TransactionManagementConfigurer 类型的bean设置的
             * */
            interceptor.setTransactionManager(this.txManager);
        }
        return interceptor;
    }

}
