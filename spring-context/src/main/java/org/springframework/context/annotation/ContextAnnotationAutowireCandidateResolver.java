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

package org.springframework.context.annotation;

import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Complete implementation of the
 * {@link org.springframework.beans.factory.support.AutowireCandidateResolver} strategy
 * interface, providing support for qualifier annotations as well as for lazy resolution
 * driven by the {@link Lazy} annotation in the {@code context.annotation} package.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
public class ContextAnnotationAutowireCandidateResolver extends QualifierAnnotationAutowireCandidateResolver {

    @Override
    @Nullable
    public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
        // 有@Lazy(true) 就 创建代理对象
        return (isLazy(descriptor) ? buildLazyResolutionProxy(descriptor, beanName) : null);
    }

    /**
     * 有@Lazy(true)才返回true。
     *
     * @param descriptor
     * @return
     */
    protected boolean isLazy(DependencyDescriptor descriptor) {
        /**
         * Field 上的注解
         * 方法参数 上的注解，因为
         * */
        for (Annotation ann : descriptor.getAnnotations()) {
            Lazy lazy = AnnotationUtils.getAnnotation(ann, Lazy.class);
            if (lazy != null && lazy.value()) {
                return true;
            }
        }
        // 解析 Method
        MethodParameter methodParam = descriptor.getMethodParameter();
        if (methodParam != null) {
            Method method = methodParam.getMethod();
            if (method == null || void.class == method.getReturnType()) {
                Lazy lazy = AnnotationUtils.getAnnotation(methodParam.getAnnotatedElement(), Lazy.class);
                if (lazy != null && lazy.value()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected Object buildLazyResolutionProxy(final DependencyDescriptor descriptor, final @Nullable String beanName) {
        BeanFactory beanFactory = getBeanFactory();
        Assert.state(beanFactory instanceof DefaultListableBeanFactory,
                "BeanFactory needs to be a DefaultListableBeanFactory");
        final DefaultListableBeanFactory dlbf = (DefaultListableBeanFactory) beanFactory;

        TargetSource ts = new TargetSource() {
            @Override
            public Class<?> getTargetClass() {
                return descriptor.getDependencyType();
            }

            @Override
            public boolean isStatic() {
                return false;
            }

            @Override
            public Object getTarget() {
                Set<String> autowiredBeanNames = (beanName != null ? new LinkedHashSet<>(1) : null);
                Object target = dlbf.doResolveDependency(descriptor, beanName, autowiredBeanNames, null);
                if (target == null) {
                    Class<?> type = getTargetClass();
                    if (Map.class == type) {
                        return Collections.emptyMap();
                    } else if (List.class == type) {
                        return Collections.emptyList();
                    } else if (Set.class == type || Collection.class == type) {
                        return Collections.emptySet();
                    }
                    throw new NoSuchBeanDefinitionException(descriptor.getResolvableType(),
                            "Optional dependency not present for lazy injection point");
                }
                if (autowiredBeanNames != null) {
                    for (String autowiredBeanName : autowiredBeanNames) {
                        if (dlbf.containsBean(autowiredBeanName)) {
                            dlbf.registerDependentBean(autowiredBeanName, beanName);
                        }
                    }
                }
                return target;
            }

            @Override
            public void releaseTarget(Object target) {
            }
        };

        ProxyFactory pf = new ProxyFactory();
        pf.setTargetSource(ts);
        Class<?> dependencyType = descriptor.getDependencyType();
        if (dependencyType.isInterface()) {
            pf.addInterface(dependencyType);
        }
        return pf.getProxy(dlbf.getBeanClassLoader());
    }

}
