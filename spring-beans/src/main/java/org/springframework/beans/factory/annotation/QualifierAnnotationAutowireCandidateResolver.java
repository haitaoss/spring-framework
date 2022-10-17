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

import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.*;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link AutowireCandidateResolver} implementation that matches bean definition qualifiers
 * against {@link Qualifier qualifier annotations} on the field or parameter to be autowired.
 * Also supports suggested expression values through a {@link Value value} annotation.
 *
 * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 2.5
 * @see AutowireCandidateQualifier
 * @see Qualifier
 * @see Value
 */
public class QualifierAnnotationAutowireCandidateResolver extends GenericTypeAwareAutowireCandidateResolver {

    private final Set<Class<? extends Annotation>> qualifierTypes = new LinkedHashSet<>(2);

    private Class<? extends Annotation> valueAnnotationType = Value.class;


    /**
     * Create a new QualifierAnnotationAutowireCandidateResolver
     * for Spring's standard {@link Qualifier} annotation.
     * <p>Also supports JSR-330's {@link javax.inject.Qualifier} annotation, if available.
     */
    @SuppressWarnings("unchecked")
    public QualifierAnnotationAutowireCandidateResolver() {
        this.qualifierTypes.add(Qualifier.class);
        try {
            this.qualifierTypes.add((Class<? extends Annotation>) ClassUtils.forName("javax.inject.Qualifier",
                    QualifierAnnotationAutowireCandidateResolver.class.getClassLoader()));
        } catch (ClassNotFoundException ex) {
            // JSR-330 API not available - simply skip.
        }
    }

    /**
     * Create a new QualifierAnnotationAutowireCandidateResolver
     * for the given qualifier annotation type.
     * @param qualifierType the qualifier annotation to look for
     */
    public QualifierAnnotationAutowireCandidateResolver(Class<? extends Annotation> qualifierType) {
        Assert.notNull(qualifierType, "'qualifierType' must not be null");
        this.qualifierTypes.add(qualifierType);
    }

    /**
     * Create a new QualifierAnnotationAutowireCandidateResolver
     * for the given qualifier annotation types.
     * @param qualifierTypes the qualifier annotations to look for
     */
    public QualifierAnnotationAutowireCandidateResolver(Set<Class<? extends Annotation>> qualifierTypes) {
        Assert.notNull(qualifierTypes, "'qualifierTypes' must not be null");
        this.qualifierTypes.addAll(qualifierTypes);
    }


    /**
     * Register the given type to be used as a qualifier when autowiring.
     * <p>This identifies qualifier annotations for direct use (on fields,
     * method parameters and constructor parameters) as well as meta
     * annotations that in turn identify actual qualifier annotations.
     * <p>This implementation only supports annotations as qualifier types.
     * The default is Spring's {@link Qualifier} annotation which serves
     * as a qualifier for direct use and also as a meta annotation.
     * @param qualifierType the annotation type to register
     */
    public void addQualifierType(Class<? extends Annotation> qualifierType) {
        this.qualifierTypes.add(qualifierType);
    }

    /**
     * Set the 'value' annotation type, to be used on fields, method parameters
     * and constructor parameters.
     * <p>The default value annotation type is the Spring-provided
     * {@link Value} annotation.
     * <p>This setter property exists so that developers can provide their own
     * (non-Spring-specific) annotation type to indicate a default value
     * expression for a specific argument.
     */
    public void setValueAnnotationType(Class<? extends Annotation> valueAnnotationType) {
        this.valueAnnotationType = valueAnnotationType;
    }


    /**
     * Determine whether the provided bean definition is an autowire candidate.
     * <p>To be considered a candidate the bean's <em>autowire-candidate</em>
     * attribute must not have been set to 'false'. Also, if an annotation on
     * the field or parameter to be autowired is recognized by this bean factory
     * as a <em>qualifier</em>, the bean must 'match' against the annotation as
     * well as any attributes it may contain. The bean definition must contain
     * the same qualifier or match by meta attributes. A "value" attribute will
     * fallback to match against the bean name or an alias if a qualifier or
     * attribute does not match.
     * @see Qualifier
     */
    @Override
    public boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
        /**
         * 这个就是检查BeanDefinition的属性值
         * {@link AbstractBeanDefinition#isAutowireCandidate()}
         * */
        boolean match = super.isAutowireCandidate(bdHolder, descriptor);
        if (match) {
            /**
             * 字段依赖@Qualifier校验,和方法参数依赖@Qualifier校验
             *
             * 对依赖的所有注解进行匹配，是@Qualifier就匹配注解的值和BeanDefinition中的值是否一致，如果不一致 或者 没有@Qualifier，会有补偿策略，
             * 拿到注解上的注解,遍历 是@Qualifier就进行匹配
             * */
            match = checkQualifiers(bdHolder, descriptor.getAnnotations());
            if (match) {
                /**
                 * 方法参数匹配了，在匹配其方法上的@Qualifier。
                 * 怎么感觉是一个bug呀，既然方法参数的@Qualifier匹配了，干嘛还得看其方法的@Qualifier是否匹配。
                 * 不是bug，因为 match 如果没有@Qualifier就是true，有@Qualifier才会判断，所以这里无法知道上一步match
                 * 是否有匹配了@Qualifier，所以才会接着判断方法。
                 *
                 * 比如，下面这种写法是一直匹配不到的，因为 参数Qualifier 和方法Qualifier 不一致。可以修改方法返回值，为void，就不会进行方法Qualifier的匹配
                 * public class Test {
                 *     @Autowired @Qualifier("a")
                 *     public void x(@Qualifier("a2") A a) {
                 *         System.out.println(a);
                 *     }
                 * }
                 * */
                MethodParameter methodParam = descriptor.getMethodParameter();
                if (methodParam != null) {
                    Method method = methodParam.getMethod();
                    /**
                     * 方法返回值是null，才进行方法Qualifier的匹配
                     * */
                    if (method == null || void.class == method.getReturnType()) {
                        /**
                         * 有@Qualifier 就会校验，不然就是true咯
                         * */
                        match = checkQualifiers(bdHolder, methodParam.getMethodAnnotations());
                    }
                }
            }
        }
        return match;
    }

    /**
     * Match the given qualifier annotations against the candidate bean definition.
     */
    protected boolean checkQualifiers(BeanDefinitionHolder bdHolder, Annotation[] annotationsToSearch) {
        /**
         * 是空就没必要校验了
         * */
        if (ObjectUtils.isEmpty(annotationsToSearch)) {
            return true;
        }
        SimpleTypeConverter typeConverter = new SimpleTypeConverter();
        for (Annotation annotation : annotationsToSearch) {
            Class<? extends Annotation> type = annotation.annotationType();
            /**
             * 检查元数据，就是注解上面的注解。如果当前注解@Qualifier与BeanDefinition匹配了，那就不需要检查元数据咯，
             * 默认是true
             * */
            boolean checkMeta = true;
            /**
             * 应急检查，就是当前注解是@Qualifier但是匹配失败，那就应该尝试检查注解的元数据是否能匹配
             * */
            boolean fallbackToMeta = false;
            /**
             * 是 @Qualifier注解就是true,才会进一步校验
             * */
            if (isQualifier(type)) {
                /**
                 * 就是看 BeanDefinition 记录的信息 是否和 @Qualifier 的值一致返回true
                 * */
                if (!checkQualifier(bdHolder, annotation, typeConverter)) {
                    // 需要应急检查
                    fallbackToMeta = true;
                } else {
                    /**
                     * 已经匹配了@Qualifier，就不需要检查注解的元数据了，所以置为false
                     * */
                    checkMeta = false;
                }
            }
            /**
             * 补偿策略，就是注解没检验通过 就看看注解上有没有 @Qualifier 用来判断
             * */
            if (checkMeta) {
                /**
                 * 注解上是否有 @Qualifier
                 *
                 * 比如
                 * @Autowired
                 * @base
                 * public void x3(A xx) {}
                 *
                 * @Qualifier("a")
                 * @Target({ElementType.METHOD})
                 * @Retention(RetentionPolicy.RUNTIME)
                 * public static @interface base {}
                 * */
                boolean foundMeta = false;
                for (Annotation metaAnn : type.getAnnotations()) {
                    Class<? extends Annotation> metaType = metaAnn.annotationType();
                    if (isQualifier(metaType)) {
                        foundMeta = true;
                        // Only accept fallback match if @Qualifier annotation has a value...
                        // Otherwise it is just a marker for a custom qualifier annotation.
                        if ((fallbackToMeta && ObjectUtils.isEmpty(AnnotationUtils.getValue(metaAnn))) ||
                            !checkQualifier(bdHolder, metaAnn, typeConverter)) {
                            return false;
                        }
                    }
                }
                if (fallbackToMeta && !foundMeta) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 是@Qualifier 或者 有@Qualifier 就返回true
     * Checks whether the given annotation type is a recognized qualifier type.
     */
    protected boolean isQualifier(Class<? extends Annotation> annotationType) {
        /**
         * qualifierTypes 这就是 {@Qualifier}
         * */
        for (Class<? extends Annotation> qualifierType : this.qualifierTypes) {
            if (annotationType.equals(qualifierType) || annotationType.isAnnotationPresent(qualifierType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Match the given qualifier annotation against the candidate bean definition.
     */
    protected boolean checkQualifier(
            BeanDefinitionHolder bdHolder, Annotation annotation, TypeConverter typeConverter) {

        Class<? extends Annotation> type = annotation.annotationType();
        RootBeanDefinition bd = (RootBeanDefinition) bdHolder.getBeanDefinition();

        /**
         * 一大堆操作 其实就是从 BeanDefinition 中找到Qualifier的值
         * */
        AutowireCandidateQualifier qualifier = bd.getQualifier(type.getName());
        if (qualifier == null) {
            qualifier = bd.getQualifier(ClassUtils.getShortName(type));
        }
        if (qualifier == null) {
            // First, check annotation on qualified element, if any
            Annotation targetAnnotation = getQualifiedElementAnnotation(bd, type);
            // Then, check annotation on factory method, if applicable
            if (targetAnnotation == null) {
                targetAnnotation = getFactoryMethodAnnotation(bd, type);
            }
            if (targetAnnotation == null) {
                RootBeanDefinition dbd = getResolvedDecoratedDefinition(bd);
                if (dbd != null) {
                    targetAnnotation = getFactoryMethodAnnotation(dbd, type);
                }
            }
            if (targetAnnotation == null) {
                // Look for matching annotation on the target class
                if (getBeanFactory() != null) {
                    try {
                        // 拿到 class
                        Class<?> beanType = getBeanFactory().getType(bdHolder.getBeanName());
                        if (beanType != null) {
                            // 拿到Class上的 @Qualifier 注解
                            targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(beanType), type);
                        }
                    } catch (NoSuchBeanDefinitionException ex) {
                        // Not the usual case - simply forget about the type check...
                    }
                }
                if (targetAnnotation == null && bd.hasBeanClass()) {
                    // 拿到Class上的 @Qualifier 注解
                    targetAnnotation = AnnotationUtils.getAnnotation(ClassUtils.getUserClass(bd.getBeanClass()), type);
                }
            }
            if (targetAnnotation != null && targetAnnotation.equals(annotation)) {
                return true;
            }
        }

        Map<String, Object> attributes = AnnotationUtils.getAnnotationAttributes(annotation);
        if (attributes.isEmpty() && qualifier == null) {
            // If no attributes, the qualifier must be present
            return false;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String attributeName = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = null;
            // Check qualifier first
            if (qualifier != null) {
                actualValue = qualifier.getAttribute(attributeName);
            }
            if (actualValue == null) {
                // Fall back on bean definition attribute
                actualValue = bd.getAttribute(attributeName);
            }
            /**
             *  bdHolder.matchesName((String) expectedValue) 就是匹配 beanName 和 @Qualifier("x")
             * */
            if (actualValue == null && attributeName.equals(AutowireCandidateQualifier.VALUE_KEY) &&
                expectedValue instanceof String &&
                bdHolder.matchesName((String) expectedValue)) {
                // Fall back on bean name (or alias) match
                continue;
            }
            if (actualValue == null && qualifier != null) {
                // Fall back on default, but only if the qualifier is present
                actualValue = AnnotationUtils.getDefaultValue(annotation, attributeName);
            }
            if (actualValue != null) {
                actualValue = typeConverter.convertIfNecessary(actualValue, expectedValue.getClass());
            }
            if (!expectedValue.equals(actualValue)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    protected Annotation getQualifiedElementAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
        AnnotatedElement qualifiedElement = bd.getQualifiedElement();
        return (qualifiedElement != null ? AnnotationUtils.getAnnotation(qualifiedElement, type) : null);
    }

    @Nullable
    protected Annotation getFactoryMethodAnnotation(RootBeanDefinition bd, Class<? extends Annotation> type) {
        Method resolvedFactoryMethod = bd.getResolvedFactoryMethod();
        return (resolvedFactoryMethod != null ? AnnotationUtils.getAnnotation(resolvedFactoryMethod, type) : null);
    }


    /**
     * Determine whether the given dependency declares an autowired annotation,
     * checking its required flag.
     * @see Autowired#required()
     */
    @Override
    public boolean isRequired(DependencyDescriptor descriptor) {
        if (!super.isRequired(descriptor)) {
            return false;
        }
        Autowired autowired = descriptor.getAnnotation(Autowired.class);
        return (autowired == null || autowired.required());
    }

    /**
     * Determine whether the given dependency declares a qualifier annotation.
     * @see #isQualifier(Class)
     * @see Qualifier
     */
    @Override
    public boolean hasQualifier(DependencyDescriptor descriptor) {
        for (Annotation ann : descriptor.getAnnotations()) {
            if (isQualifier(ann.annotationType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 两种情况：
     *  - Field：拿到字段的注解，有@Value注解就返回@Value的值
     *  - 方法参数：先拿参数上的注解，拿不到在拿其方法的注解，有@Value注解就返回@Value的值
     * Determine whether the given dependency declares a value annotation.
     * @see Value
     */
    @Override
    @Nullable
    public Object getSuggestedValue(DependencyDescriptor descriptor) {
        /**
         * 拿到注解的@Value属性值。
         *
         * `descriptor.getAnnotations()`两种情况：
         *  - Field 上的注解
         *  - 方法参数的第几个参数的注解
         * */
        Object value = findValue(descriptor.getAnnotations());
        if (value == null) {
            MethodParameter methodParam = descriptor.getMethodParameter();
            if (methodParam != null) {
                /**
                 * 方法上的注解，
                 * */
                value = findValue(methodParam.getMethodAnnotations());
            }
        }
        return value;
    }

    /**
     * 拿到@Value注解的属性值，如果没有@Value注解就返回null
     * Determine a suggested value from any of the given candidate annotations.
     */
    @Nullable
    protected Object findValue(Annotation[] annotationsToSearch) {
        if (annotationsToSearch.length > 0) {   // qualifier annotations have to be local
            // 只拿到 @Value 注解
            AnnotationAttributes attr = AnnotatedElementUtils.getMergedAnnotationAttributes(
                    AnnotatedElementUtils.forAnnotations(annotationsToSearch), this.valueAnnotationType);
            if (attr != null) {
                return extractValue(attr);
            }
        }
        return null;
    }

    /**
     * Extract the value attribute from the given annotation.
     * @since 4.3
     */
    protected Object extractValue(AnnotationAttributes attr) {
        Object value = attr.get(AnnotationUtils.VALUE);
        if (value == null) {
            throw new IllegalStateException("Value annotation must have a value attribute");
        }
        return value;
    }

}
