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

package org.springframework.beans.factory.annotation;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Internal class for managing injection metadata.
 * Not intended for direct use in applications.
 *
 * <p>Used by {@link AutowiredAnnotationBeanPostProcessor},
 * {@link org.springframework.context.annotation.CommonAnnotationBeanPostProcessor} and
 * {@link org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor}.
 *
 * @author Juergen Hoeller
 * @since 2.5
 */
public class InjectionMetadata {

    /**
     * An empty {@code InjectionMetadata} instance with no-op callbacks.
     * @since 5.2
     */
    public static final InjectionMetadata EMPTY = new InjectionMetadata(Object.class, Collections.emptyList()) {
        @Override
        protected boolean needsRefresh(Class<?> clazz) {
            return false;
        }

        @Override
        public void checkConfigMembers(RootBeanDefinition beanDefinition) {
        }

        @Override
        public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) {
        }

        @Override
        public void clear(@Nullable PropertyValues pvs) {
        }
    };


    /**
     * bean的class
     */
    private final Class<?> targetClass;

    /**
     * 要注入的元素。比如@Autowired修饰的方法、字段
     */
    private final Collection<InjectedElement> injectedElements;

    /**
     * 检查过的元素
     * {@link InjectionMetadata#checkConfigMembers(RootBeanDefinition)}
     */
    @Nullable
    private volatile Set<InjectedElement> checkedElements;


    /**
     * Create a new {@code InjectionMetadata instance}.
     * <p>Preferably use {@link #forElements} for reusing the {@link #EMPTY}
     * instance in case of no elements.
     * @param targetClass the target class
     * @param elements the associated elements to inject
     * @see #forElements
     */
    public InjectionMetadata(Class<?> targetClass, Collection<InjectedElement> elements) {
        this.targetClass = targetClass;
        this.injectedElements = elements;
    }


    /**
     * Determine whether this metadata instance needs to be refreshed.
     * @param clazz the current target class
     * @return {@code true} indicating a refresh, {@code false} otherwise
     * @since 5.2.4
     */
    protected boolean needsRefresh(Class<?> clazz) {
        return this.targetClass != clazz;
    }

    public void checkConfigMembers(RootBeanDefinition beanDefinition) {
        // 需要注入的元素信息
        Set<InjectedElement> checkedElements = new LinkedHashSet<>(this.injectedElements.size());
        for (InjectedElement element : this.injectedElements) {
            // member 其实就是 Field、Method
            Member member = element.getMember();
            /**
             * 啥也没干，只是将 member 添加到 {@link RootBeanDefinition#externallyManagedConfigMembers} 就是记录到BeanDefinition中
             * 中`this.externallyManagedConfigMembers.add(configMember);`
             * */
            if (!beanDefinition.isExternallyManagedConfigMember(member)) {
                beanDefinition.registerExternallyManagedConfigMember(member);
                // 添加到 checked集合
                checkedElements.add(element);
            }
        }
        // 设置检查过的元素。
        this.checkedElements = checkedElements;
    }

    /**
     * 就是遍历其 injectedElements 属性，执行 {@link InjectedElement#inject(Object, String, PropertyValues)}
     * @param target
     * @param beanName
     * @param pvs
     * @throws Throwable
     */
    public void inject(Object target, @Nullable String beanName, @Nullable PropertyValues pvs) throws Throwable {
        Collection<InjectedElement> checkedElements = this.checkedElements;
        Collection<InjectedElement> elementsToIterate =
                (checkedElements != null ? checkedElements : this.injectedElements);
        if (!elementsToIterate.isEmpty()) {
            /**
             * 遍历 elementsToIterate。
             *
             * 比如 AutowiredAnnotationBeanPostProcessor 生成的 InjectionMetadata，其中的
             * injectedElements 属性的值，其实就是 标注了@Autowired、@Value注解的字段和方法
             * */
            for (InjectedElement element : elementsToIterate) {
                /**
                 * target 是 bean对象
                 * Field 的注入  {@link AutowiredAnnotationBeanPostProcessor.AutowiredFieldElement#inject(Object, String, PropertyValues)}
                 * Method 的注入 {@link AutowiredAnnotationBeanPostProcessor.AutowiredMethodElement#inject(Object, String, PropertyValues)}
                 *
                 * 如果是 ResourceElement 执行的是 {@link InjectedElement#inject(Object, String, PropertyValues)}
                 * */
                element.inject(target, beanName, pvs);
            }
        }
    }

    /**
     * Clear property skipping for the contained elements.
     * @since 3.2.13
     */
    public void clear(@Nullable PropertyValues pvs) {
        Collection<InjectedElement> checkedElements = this.checkedElements;
        Collection<InjectedElement> elementsToIterate =
                (checkedElements != null ? checkedElements : this.injectedElements);
        if (!elementsToIterate.isEmpty()) {
            for (InjectedElement element : elementsToIterate) {
                // 清除属性
                element.clearPropertySkipping(pvs);
            }
        }
    }


    /**
     * Return an {@code InjectionMetadata} instance, possibly for empty elements.
     * @param elements the elements to inject (possibly empty)
     * @param clazz the target class
     * @return a new {@link #InjectionMetadata(Class, Collection)} instance
     * @since 5.2
     */
    public static InjectionMetadata forElements(Collection<InjectedElement> elements, Class<?> clazz) {
        return (elements.isEmpty() ? new InjectionMetadata(clazz, Collections.emptyList()) :
                new InjectionMetadata(clazz, elements));
    }

    /**
     * metadata 是空 或者 metadata的属性{@link InjectionMetadata#targetClass} != clazz。就return true
     *
     * Check whether the given injection metadata needs to be refreshed.
     * @param metadata the existing metadata instance
     * @param clazz the current target class
     * @return {@code true} indicating a refresh, {@code false} otherwise
     * @see #needsRefresh(Class)
     */
    public static boolean needsRefresh(@Nullable InjectionMetadata metadata, Class<?> clazz) {
        return (metadata == null || metadata.needsRefresh(clazz));
    }


    /**
     * A single injected element.
     */
    public abstract static class InjectedElement {

        protected final Member member;

        protected final boolean isField;

        @Nullable
        protected final PropertyDescriptor pd;

        @Nullable
        protected volatile Boolean skip;

        protected InjectedElement(Member member, @Nullable PropertyDescriptor pd) {
            this.member = member;
            this.isField = (member instanceof Field);
            this.pd = pd;
        }

        public final Member getMember() {
            return this.member;
        }

        protected final Class<?> getResourceType() {
            if (this.isField) {
                return ((Field) this.member).getType();
            } else if (this.pd != null) {
                return this.pd.getPropertyType();
            } else {
                return ((Method) this.member).getParameterTypes()[0];
            }
        }

        protected final void checkResourceType(Class<?> resourceType) {
            if (this.isField) {
                Class<?> fieldType = ((Field) this.member).getType();
                if (!(resourceType.isAssignableFrom(fieldType) || fieldType.isAssignableFrom(resourceType))) {
                    throw new IllegalStateException("Specified field type [" + fieldType +
                            "] is incompatible with resource type [" + resourceType.getName() + "]");
                }
            } else {
                Class<?> paramType =
                        (this.pd != null ? this.pd.getPropertyType() : ((Method) this.member).getParameterTypes()[0]);
                if (!(resourceType.isAssignableFrom(paramType) || paramType.isAssignableFrom(resourceType))) {
                    throw new IllegalStateException("Specified parameter type [" + paramType +
                            "] is incompatible with resource type [" + resourceType.getName() + "]");
                }
            }
        }

        /**
         * Either this or {@link #getResourceToInject} needs to be overridden.
         */
        protected void
        inject(Object target, @Nullable String requestingBeanName, @Nullable PropertyValues pvs)
                throws Throwable {

            if (this.isField) {
                Field field = (Field) this.member;
                ReflectionUtils.makeAccessible(field);
                /**
                 * 是字段直接反射设置值
                 * {@link org.springframework.context.annotation.CommonAnnotationBeanPostProcessor.ResourceElement#getResourceToInject(Object, String)}
                 * */
                field.set(target, getResourceToInject(target, requestingBeanName));
            } else {
                // 是否需要跳过方法注入
                if (checkPropertySkipping(pvs)) {
                    return;
                }
                try {
                    Method method = (Method) this.member;
                    ReflectionUtils.makeAccessible(method);
                    // 是方法直接反射调用
                    method.invoke(target, getResourceToInject(target, requestingBeanName));
                } catch (InvocationTargetException ex) {
                    throw ex.getTargetException();
                }
            }
        }

        /**
         * 1. 如果 PropertyValues 有这个属性的记录，那就不应该注入。 <br/>
         * 2. 设置好 {@link InjectedElement#skip} 的值，表面后面重复判断 <br/>
         * 检查是否因为指定了显式的属性值而需要跳过该注入器的属性。还将受影响的属性标记为已处理，以便其他处理器忽略它。<br/>
         * Check whether this injector's property needs to be skipped due to
         * an explicit property value having been specified. Also marks the
         * affected property as processed for other processors to ignore it.
         */
        protected boolean checkPropertySkipping(@Nullable PropertyValues pvs) {
            // 该属性默认是null的，所以第一次执行肯定是往下走的
            Boolean skip = this.skip;
            if (skip != null) {
                return skip;
            }
            // 没有 pvs，那就不需要跳过了，给skip赋值为false
            if (pvs == null) {
                this.skip = false;
                return false;
            }
            synchronized (pvs) {
                skip = this.skip;
                if (skip != null) {
                    return skip;
                }
                if (this.pd != null) {
                    // 要注入的方法在 pvs 中有，那就应该跳过
                    if (pvs.contains(this.pd.getName())) {
                        // Explicit value provided as part of the bean definition.
                        this.skip = true;
                        return true;
                    } else if (pvs instanceof MutablePropertyValues) {
                        ((MutablePropertyValues) pvs).registerProcessedProperty(this.pd.getName());
                    }
                }
                this.skip = false;
                return false;
            }
        }

        /**
         * 清除属性，如果当前属性是需要跳过的 就不清除
         * Clear property skipping for this element.
         * @since 3.2.13
         */
        protected void clearPropertySkipping(@Nullable PropertyValues pvs) {
            if (pvs == null) {
                return;
            }
            synchronized (pvs) {
                // 当前属性 不需要跳过  且 pd(熟悉描述) 不是 null
                if (Boolean.FALSE.equals(this.skip) && this.pd != null && pvs instanceof MutablePropertyValues) {
                    // 删除这个属性
                    ((MutablePropertyValues) pvs).clearProcessedProperty(this.pd.getName());
                }
            }
        }

        /**
         * Either this or {@link #inject} needs to be overridden.
         */
        @Nullable
        protected Object getResourceToInject(Object target, @Nullable String requestingBeanName) {
            return null;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InjectedElement)) {
                return false;
            }
            InjectedElement otherElement = (InjectedElement) other;
            return this.member.equals(otherElement.member);
        }

        @Override
        public int hashCode() {
            return this.member.getClass().hashCode() * 29 + this.member.getName().hashCode();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " for " + this.member;
        }
    }

}
