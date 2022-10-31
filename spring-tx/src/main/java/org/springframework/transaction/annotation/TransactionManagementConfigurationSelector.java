/*
 * Copyright 2002-2018 the original author or authors.
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

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.ClassUtils;

/**
 * Selects which implementation of {@link AbstractTransactionManagementConfiguration}
 * should be used based on the value of {@link EnableTransactionManagement#mode} on the
 * importing {@code @Configuration} class.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableTransactionManagement
 * @see ProxyTransactionManagementConfiguration
 * @see TransactionManagementConfigUtils#TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME
 * @see TransactionManagementConfigUtils#JTA_TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME
 */
public class TransactionManagementConfigurationSelector extends AdviceModeImportSelector<EnableTransactionManagement> {

    /**
     * Returns {@link ProxyTransactionManagementConfiguration} or
     * {@code AspectJ(Jta)TransactionManagementConfiguration} for {@code PROXY}
     * and {@code ASPECTJ} values of {@link EnableTransactionManagement#mode()},
     * respectively.
     */
    @Override
    protected String[] selectImports(AdviceMode adviceMode) {
        switch (adviceMode) {
            case PROXY:
                /**
                 * AutoProxyRegistrar 会注册 ProxyTransactionManagementConfiguration。
                 * 而 ProxyTransactionManagementConfiguration ，会拿到容器中 Advisor 类型的bean 且 Role=={@link BeanDefinition#ROLE_INFRASTRUCTURE}} 才作为 Advisor，
                 * 然后使用Advisor判断执行后置处理器的bean，是否创建代理对象。所以具体的逻辑还得看 Advisor 怎么写的
                 *
                 * ProxyTransactionManagementConfiguration 会注册 Advisor。注册的是这个 {@link BeanFactoryTransactionAttributeSourceAdvisor}
                 * 而Advisor的具体增强逻辑是看其 {@link Advisor#getAdvice()}，而这个类会注册这个 {@link TransactionInterceptor} 作为其Advice，
                 * 所以具体的增强逻辑看这个 {@link TransactionInterceptor#invoke(MethodInvocation)}
                 * */
                return new String[]{AutoProxyRegistrar.class.getName(),
                        ProxyTransactionManagementConfiguration.class.getName()};
            case ASPECTJ:
                return new String[]{determineTransactionAspectClass()};
            default:
                return null;
        }
    }

    private String determineTransactionAspectClass() {
        return (ClassUtils.isPresent("javax.transaction.Transactional", getClass().getClassLoader()) ?
                TransactionManagementConfigUtils.JTA_TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME :
                TransactionManagementConfigUtils.TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME);
    }

}
