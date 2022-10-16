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

package org.springframework.aop.aspectj.annotation;

import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @see AnnotationAwareAspectJAutoProxyCreator
 * @since 2.0.2
 */
public class BeanFactoryAspectJAdvisorsBuilder {

    private final ListableBeanFactory beanFactory;

    private final AspectJAdvisorFactory advisorFactory;

    @Nullable
    private volatile List<String> aspectBeanNames;

    private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

    private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


    /**
     * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
     *
     * @param beanFactory the ListableBeanFactory to scan
     */
    public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
        this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
    }

    /**
     * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
     *
     * @param beanFactory    the ListableBeanFactory to scan
     * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
     */
    public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
        Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
        Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
        this.beanFactory = beanFactory;
        this.advisorFactory = advisorFactory;
    }


    /**
     * 拿到BeanFactory中标注了@Aspect 的bean
     * 将标注了 @Around、@Before、@After、@AfterReturning、@AfterThrowing 的方法解析成 InstantiationModelAwarePointcutAdvisorImpl
     * <p>
     * Look for AspectJ-annotated aspect beans in the current bean factory,
     * and return to a list of Spring AOP Advisors representing them.
     * <p>Creates a Spring Advisor for each AspectJ advice method.
     *
     * @return the list of {@link org.springframework.aop.Advisor} beans
     * @see #isEligibleBean
     */
    public List<Advisor> buildAspectJAdvisors() {
        List<String> aspectNames = this.aspectBeanNames;

        if (aspectNames == null) {
            synchronized (this) {
                aspectNames = this.aspectBeanNames;
                if (aspectNames == null) {
                    List<Advisor> advisors = new ArrayList<>();
                    aspectNames = new ArrayList<>();
                    String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, Object.class, true, false);
                    for (String beanName : beanNames) {
                        /**
                         * 就是 beanName 是否符合正则匹配规则 {@link AnnotationAwareAspectJAutoProxyCreator#isEligibleAspectBean(String)}
                         * 会判断这个属性 {@link AnnotationAwareAspectJAutoProxyCreator#includePatterns} 是否为空，为空就直接返回true，不为空就遍历属性 进行正则匹配，匹配了才返回true
                         *
                         * 属性默认值就是null，所有 这里一直返回true
                         * */
                        if (!isEligibleBean(beanName)) {
                            continue;
                        }
                        // We must be careful not to instantiate beans eagerly as in this case they
                        // would be cached by the Spring container but would not have been weaved.
                        Class<?> beanType = this.beanFactory.getType(beanName, false);
                        if (beanType == null) {
                            continue;
                        }
                        // 进行 Aspect 判断
                        /**
                         * advisorFactory 是 ReflectiveAspectJAdvisorFactory
                         * 有 @Aspect 的类 就是 true
                         * */
                        if (this.advisorFactory.isAspect(beanType)) {
                            aspectNames.add(beanName);
                            AspectMetadata amd = new AspectMetadata(beanType, beanName);
                            // 如果是 @Aspect("") 这样子使用，就满足if
                            if (amd.getAjType()
                                    .getPerClause()
                                    .getKind() == PerClauseKind.SINGLETON) {
                                // 这个也是有用的，后面排序Advisor的时候 是根据@Aspect这个切面类来排序的
                                MetadataAwareAspectInstanceFactory factory = new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);

                                /**
                                 * 处理有 @Aspect 的类
                                 * 然后将类里面 @Around、@Before、@After、@AfterReturning、@AfterThrowing 的方法解析成 InstantiationModelAwarePointcutAdvisorImpl
                                 * 另外还会根 据需要 添加 SyntheticInstantiationAdvisor、DeclareParentsAdvisor 的 advisor
                                 * */
                                List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
                                if (this.beanFactory.isSingleton(beanName)) {
                                    // 缓存起来。这个是可以直接返回的
                                    this.advisorsCache.put(beanName, classAdvisors);
                                } else {
                                    // 缓存起来。这个是后面还得重新创建  `this.advisorFactory.getAdvisors(factory))`
                                    this.aspectFactoryCache.put(beanName, factory);
                                }
                                advisors.addAll(classAdvisors);
                            } else {
                                // Per target or per this.
                                if (this.beanFactory.isSingleton(beanName)) {
                                    throw new IllegalArgumentException("Bean with name '" + beanName
                                            + "' is a singleton, but aspect instantiation model is not singleton");
                                }
                                // 这个也是有用的，后面排序Advisor的时候 是根据@Aspect这个切面类来排序的
                                MetadataAwareAspectInstanceFactory factory = new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
                                this.aspectFactoryCache.put(beanName, factory);
                                advisors.addAll(this.advisorFactory.getAdvisors(factory));
                            }
                        }
                    }
                    this.aspectBeanNames = aspectNames;
                    return advisors;
                }
            }
        }

        if (aspectNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Advisor> advisors = new ArrayList<>();
        for (String aspectName : aspectNames) {
            List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
            if (cachedAdvisors != null) {
                advisors.addAll(cachedAdvisors);
            } else {
                MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
                advisors.addAll(this.advisorFactory.getAdvisors(factory));
            }
        }
        return advisors;
    }

    /**
     * Return whether the aspect bean with the given name is eligible.
     *
     * @param beanName the name of the aspect bean
     * @return whether the bean is eligible
     */
    protected boolean isEligibleBean(String beanName) {
        return true;
    }

}
