/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.context.annotation;

import org.springframework.aop.config.AopConfigUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Registers an {@link org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator
 * AnnotationAwareAspectJAutoProxyCreator} against the current {@link BeanDefinitionRegistry}
 * as appropriate based on a given @{@link EnableAspectJAutoProxy} annotation.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableAspectJAutoProxy
 */
class AspectJAutoProxyRegistrar implements ImportBeanDefinitionRegistrar {

    /**
     * Register, escalate, and configure the AspectJ auto proxy creator based on the value
     * of the @{@link EnableAspectJAutoProxy#proxyTargetClass()} attribute on the importing
     * {@code @Configuration} class.
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        /**
         * 默认注册 AnnotationAwareAspectJAutoProxyCreator
         *
         * 具体的注册逻辑 {@link AopConfigUtils#registerOrEscalateApcAsRequired(Class, BeanDefinitionRegistry, Object)}
         *  如果发现容器中已经注册了 {@link AopConfigUtils#AUTO_PROXY_CREATOR_BEAN_NAME} 这个名字的bean，那就看看优先级，新注册的优先级大就覆盖bean的定义
         *  而优先级是这个 {@link AopConfigUtils#APC_PRIORITY_LIST} 属性的索引值
         *     这里面注册了三个 AbstractAdvisorAutoProxyCreator [InfrastructureAdvisorAutoProxyCreator、AspectJAwareAdvisorAutoProxyCreator、AnnotationAwareAspectJAutoProxyCreator]
         *
         *      继承树
         *      SmartInstantiationAwareBeanPostProcessor
         *         AbstractAutoProxyCreator (org.springframework.aop.framework.autoproxy)
         *             AbstractAdvisorAutoProxyCreator (org.springframework.aop.framework.autoproxy)
         *                 AspectJAwareAdvisorAutoProxyCreator (org.springframework.aop.aspectj.autoproxy)
         *                     AnnotationAwareAspectJAutoProxyCreator (org.springframework.aop.aspectj.annotation)
         *                 DefaultAdvisorAutoProxyCreator (org.springframework.aop.framework.autoproxy)
         *                 InfrastructureAdvisorAutoProxyCreator (org.springframework.aop.framework.autoproxy)
         *
         *
         * */
        AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(registry);

        AnnotationAttributes enableAspectJAutoProxy = AnnotationConfigUtils.attributesFor(importingClassMetadata, EnableAspectJAutoProxy.class);
        // 将注解的属性值 解析设置到 BeanDefinition 中
        if (enableAspectJAutoProxy != null) {
            if (enableAspectJAutoProxy.getBoolean("proxyTargetClass")) {
                AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
            }
            if (enableAspectJAutoProxy.getBoolean("exposeProxy")) {
                AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
            }
        }
    }

}
