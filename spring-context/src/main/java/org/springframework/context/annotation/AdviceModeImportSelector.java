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

package org.springframework.context.annotation;

import org.springframework.cache.annotation.CachingConfigurationSelector;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.AsyncConfigurationSelector;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;

/**
 * Convenient base class for {@link ImportSelector} implementations that select imports
 * based on an {@link AdviceMode} value from an annotation (such as the {@code @Enable*}
 * annotations).
 *
 * @author Chris Beams
 * @since 3.1
 * @param <A> annotation containing {@linkplain #getAdviceModeAttributeName() AdviceMode attribute}
 */
public abstract class AdviceModeImportSelector<A extends Annotation> implements ImportSelector {

    /**
     * The default advice mode attribute name.
     */
    public static final String DEFAULT_ADVICE_MODE_ATTRIBUTE_NAME = "mode";


    /**
     * The name of the {@link AdviceMode} attribute for the annotation specified by the
     * generic type {@code A}. The default is {@value #DEFAULT_ADVICE_MODE_ATTRIBUTE_NAME},
     * but subclasses may override in order to customize.
     */
    protected String getAdviceModeAttributeName() {
        return DEFAULT_ADVICE_MODE_ATTRIBUTE_NAME;
    }

    /**
     * This implementation resolves the type of annotation from generic metadata and
     * validates that (a) the annotation is in fact present on the importing
     * {@code @Configuration} class and (b) that the given annotation has an
     * {@linkplain #getAdviceModeAttributeName() advice mode attribute} of type
     * {@link AdviceMode}.
     * <p>The {@link #selectImports(AdviceMode)} method is then invoked, allowing the
     * concrete implementation to choose imports in a safe and convenient fashion.
     * @throws IllegalArgumentException if expected annotation {@code A} is not present
     * on the importing {@code @Configuration} class or if {@link #selectImports(AdviceMode)}
     * returns {@code null}
     */
    @Override
    public final String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // 拿到 AdviceModeImportSelector 的泛型参数类型
        /**
         * 查找当前类的 AdviceModeImportSelector 的泛型参数
         * */
        Class<?> annType = GenericTypeResolver.resolveTypeArgument(getClass(), AdviceModeImportSelector.class);
        Assert.state(annType != null, "Unresolvable type argument for AdviceModeImportSelector");

        // 拿到类上注解的元数据
        AnnotationAttributes attributes = AnnotationConfigUtils.attributesFor(importingClassMetadata, annType);
        if (attributes == null) {
            throw new IllegalArgumentException(String.format(
                    "@%s is not present on importing class '%s' as expected",
                    annType.getSimpleName(), importingClassMetadata.getClassName()));
        }

        //
        /**
         * 获取注解的属性值,根据默认名字`mode`
         * */
        AdviceMode adviceMode = attributes.getEnum(getAdviceModeAttributeName());
        /**
         * 这个才是大招，子类需要实现这个抽象方法 返回类全名数组，会将数组的内容注册到BeanFactory中
         *
         * 有三个子类：
         *  - 异步的 {@link AsyncConfigurationSelector#selectImports(AdviceMode)}
         *  - 缓存的 {@link CachingConfigurationSelector#selectImports(AdviceMode)}
         *  - 事务的 {@link org.springframework.transaction.annotation.TransactionManagementConfigurationSelector#selectImports(AdviceMode)}
         * */
        String[] imports = selectImports(adviceMode);
        if (imports == null) {
            throw new IllegalArgumentException("Unknown AdviceMode: " + adviceMode);
        }
        return imports;
    }

    /**
     * Determine which classes should be imported based on the given {@code AdviceMode}.
     * <p>Returning {@code null} from this method indicates that the {@code AdviceMode}
     * could not be handled or was unknown and that an {@code IllegalArgumentException}
     * should be thrown.
     * @param adviceMode the value of the {@linkplain #getAdviceModeAttributeName()
     * advice mode attribute} for the annotation specified via generics.
     * @return array containing classes to import (empty array if none;
     * {@code null} if the given {@code AdviceMode} is unknown)
     */
    @Nullable
    protected abstract String[] selectImports(AdviceMode adviceMode);

}
