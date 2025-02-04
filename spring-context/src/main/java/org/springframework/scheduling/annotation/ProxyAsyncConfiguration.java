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

package org.springframework.scheduling.annotation;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.util.Collection;

/**
 * {@code @Configuration} class that registers the Spring infrastructure beans necessary
 * to enable proxy-based asynchronous method execution.
 *
 * @author Chris Beams
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableAsync
 * @see AsyncConfigurationSelector
 */
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE) // 这个有啥用呢，就单纯一个分类吗（表示而已？）
public class ProxyAsyncConfiguration extends AbstractAsyncConfiguration {

    @Bean(name = TaskManagementConfigUtils.ASYNC_ANNOTATION_PROCESSOR_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public AsyncAnnotationBeanPostProcessor asyncAdvisor() {
        /**
         * enableAsync 这个属性肯定，不是null，因为是 @Bean 所以创建之前会先创建其配置类，也就是创建 ProxyAsyncConfiguration
         *
         * 初始化ProxyAsyncConfiguration时，会进行依赖注入也就是会执行 {@link AbstractAsyncConfiguration#setConfigurers(Collection)}
         *  会从容器中拿到 AsyncConfigurer 类型的bean，给这两个属性赋值
         *      {@link AbstractAsyncConfiguration#exceptionHandler}
         *      {@link AbstractAsyncConfiguration#executor}
         *
         * 又因为 ProxyAsyncConfiguration 实现了 ImportAware 接口，所以 ImportAwareBeanPostProcessor 后置处理器
         * 会回调 {@link AbstractAsyncConfiguration#setImportMetadata(AnnotationMetadata)} ，给 enableAsync 这个字段赋值。
         *
         * enableAsync 就是 @EnableAsync 注解的元数据信息
         * */
        Assert.notNull(this.enableAsync, "@EnableAsync annotation metadata was not injected");
        AsyncAnnotationBeanPostProcessor bpp = new AsyncAnnotationBeanPostProcessor();
        /**
         * 这两个参数的赋值，是在父类通过自动注入实现的
         * {@link AbstractAsyncConfiguration#setConfigurers(Collection)}
         * */
        bpp.configure(this.executor, this.exceptionHandler);
        // 注解的参数值
        Class<? extends Annotation> customAsyncAnnotation = this.enableAsync.getClass("annotation");
        //  有 customAsyncAnnotation 就设置
        if (customAsyncAnnotation != AnnotationUtils.getDefaultValue(EnableAsync.class, "annotation")) {
            bpp.setAsyncAnnotationType(customAsyncAnnotation);
        }
        bpp.setProxyTargetClass(this.enableAsync.getBoolean("proxyTargetClass"));
        bpp.setOrder(this.enableAsync.<Integer>getNumber("order"));
        return bpp;
    }

}
