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

package org.springframework.aop.framework.autoproxy;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.aspectj.annotation.AbstractAspectJAdvisorFactory;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Generic auto proxy creator that builds AOP proxies for specific beans
 * based on detected Advisors for each bean.
 *
 * <p>Subclasses may override the {@link #findCandidateAdvisors()} method to
 * return a custom list of Advisors applying to any object. Subclasses can
 * also override the inherited {@link #shouldSkip} method to exclude certain
 * objects from auto-proxying.
 *
 * <p>Advisors or advices requiring ordering should be annotated with
 * {@link org.springframework.core.annotation.Order @Order} or implement the
 * {@link org.springframework.core.Ordered} interface. This class sorts
 * advisors using the {@link AnnotationAwareOrderComparator}. Advisors that are
 * not annotated with {@code @Order} or don't implement the {@code Ordered}
 * interface will be considered as unordered; they will appear at the end of the
 * advisor chain in an undefined order.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #findCandidateAdvisors
 */
@SuppressWarnings("serial")
public abstract class AbstractAdvisorAutoProxyCreator extends AbstractAutoProxyCreator {

    /**
     * 这是用来检索 BeanFactory 中所有 Advisor 类型的bean
     */
    @Nullable
    private BeanFactoryAdvisorRetrievalHelper advisorRetrievalHelper;


    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        super.setBeanFactory(beanFactory);
        if (!(beanFactory instanceof ConfigurableListableBeanFactory)) {
            throw new IllegalArgumentException(
                    "AdvisorAutoProxyCreator requires a ConfigurableListableBeanFactory: " + beanFactory);
        }
        initBeanFactory((ConfigurableListableBeanFactory) beanFactory);
    }

    protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        this.advisorRetrievalHelper = new BeanFactoryAdvisorRetrievalHelperAdapter(beanFactory);
    }


    /**
     * 1. 第一次会将容器中所有的Advisor 实例化、并缓存 <br/>
     * 1.1 Advisor 类型的bean <br/>
     * 1.2 @Aspect 标注的类，将类里面的每个注解 {@link AbstractAspectJAdvisorFactory#ASPECTJ_ANNOTATION_CLASSES} 都解析成 InstantiationModelAwarePointcutAdvisorImpl <br/>
     * <p>
     * 2. 遍历所有的Advisor <br/>
     * 2.1 是 IntroductionAdvisor 类型<br/>
     * 拿到这个属性 {@link IntroductionAdvisor#getClassFilter()} 进行类匹配<br/>
     * 2.2 是 PointcutAdvisor 类型<br/>
     * 拿到这个属性 {@link PointcutAdvisor#getPointcut()} 进行类匹配 + AspectJ 表达式匹配<br/>
     * <p>
     * <p>
     * 注：InstantiationModelAwarePointcutAdvisorImpl 是 PointcutAdvisor 的子类
     *
     * @param beanClass    the class of the bean to advise
     * @param beanName     the name of the bean
     * @param targetSource the TargetSource returned by the
     *                     {@link #getCustomTargetSource} method: may be ignored.
     *                     Will be {@code null} if no custom target source is in use.
     * @return
     */
    @Override
    @Nullable
    protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
        /**
         * 查找符合条件的 Advisor
         * */
        List<Advisor> advisors = findEligibleAdvisors(beanClass, beanName);
        if (advisors.isEmpty()) {
            return DO_NOT_PROXY;
        }
        return advisors.toArray();
    }

    /**
     * Find all eligible Advisors for auto-proxying this class.
     *
     * @param beanClass the clazz to find advisors for
     * @param beanName  the name of the currently proxied bean
     * @return the empty List, not {@code null},
     * if there are no pointcuts or interceptors
     * @see #findCandidateAdvisors
     * @see #sortAdvisors
     * @see #extendAdvisors
     */
    protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
        /**
         * AnnotationAwareAspectJAutoProxyCreator 对 findCandidateAdvisors 进行了重写
         *
         * 查找候选的Advisors(这里拿到的是容器中所有的)
         * 1. 从BeanFactory中找到 Advisor 类型的bean
         * 2. 从BeanFactory中找到标注了 @Aspect 注解的bean，将标注了 @Around、@Before、@After、@AfterReturning、@AfterThrowing 的方法解析成 InstantiationModelAwarePointcutAdvisorImpl
         * */
        List<Advisor> candidateAdvisors = findCandidateAdvisors();

        /**
         * 查询当前类可以应用的 advisor(这里是筛选可以应用的)
         *
         * 匹配规则：类匹配 + 切入点表达式匹配
         * */
        List<Advisor> eligibleAdvisors = findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
        /**
         * 扩展 Advisor
         *
         * AnnotationAwareAspectJAutoProxyCreator 的父类 AspectJAwareAdvisorAutoProxyCreator 对 extendAdvisors 进行了重写
         * 会添加这个作用第0个Advisor {@link ExposeInvocationInterceptor#INSTANCE}
         * */
        extendAdvisors(eligibleAdvisors);
        if (!eligibleAdvisors.isEmpty()) {
            /**
             * 升序排序
             *
             * 默认的排序规则 {@link AnnotationAwareOrderComparator.sort(advisors);} ，可以通过 Ordered接口、@Order、@Priority 设置排序值
             *
             * AnnotationAwareAspectJAutoProxyCreator 的父类 AspectJAwareAdvisorAutoProxyCreator 对 sortAdvisors 进行了重写
             *  重写的排序规则：使用 {@link AnnotationAwareOrderComparator } 得到排序值，如果compare 返回值是0，在判断是同一个 {@link AbstractAspectJAdvisorFactory#ASPECTJ_ANNOTATION_CLASSES} 注解
             *  那就进行二次排序。
             *
             * 注：二次排序的参数值都是默认值，我看源码也没有特意修改该值，所以可以通过 第一次排序 就能设置 advisor 的顺序
             * */
            eligibleAdvisors = sortAdvisors(eligibleAdvisors);
        }
        return eligibleAdvisors;
    }

    /**
     * Find all candidate Advisors to use in auto-proxying.
     *
     * @return the List of candidate Advisors
     */
    protected List<Advisor> findCandidateAdvisors() {
        Assert.state(this.advisorRetrievalHelper != null, "No BeanFactoryAdvisorRetrievalHelper available");
        return this.advisorRetrievalHelper.findAdvisorBeans();
    }

    /**
     * Search the given candidate Advisors to find all Advisors that
     * can apply to the specified bean.
     *
     * @param candidateAdvisors the candidate Advisors
     * @param beanClass         the target's bean class
     * @param beanName          the target's bean name
     * @return the List of applicable Advisors
     * @see ProxyCreationContext#getCurrentProxiedBeanName()
     */
    protected List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> beanClass, String beanName) {
        // 通过 ThreadLocal 记录 正在代理的beanName
        ProxyCreationContext.setCurrentProxiedBeanName(beanName);
        try {
            /**
             * 就是遍历所有的 candidateAdvisors，能匹配 beanClass 就收集到集合，然后将集合返回
             *
             * 匹配规则：类匹配 + 切入点表达式匹配
             * */
            return AopUtils.findAdvisorsThatCanApply(candidateAdvisors, beanClass);
        } finally {
            // 移除 正在代理的beanName
            ProxyCreationContext.setCurrentProxiedBeanName(null);
        }
    }

    /**
     * Return whether the Advisor bean with the given name is eligible
     * for proxying in the first place.
     *
     * @param beanName the name of the Advisor bean
     * @return whether the bean is eligible
     */
    protected boolean isEligibleAdvisorBean(String beanName) {
        return true;
    }

    /**
     * Sort advisors based on ordering. Subclasses may choose to override this
     * method to customize the sorting strategy.
     *
     * @param advisors the source List of Advisors
     * @return the sorted List of Advisors
     * @see org.springframework.core.Ordered
     * @see org.springframework.core.annotation.Order
     * @see org.springframework.core.annotation.AnnotationAwareOrderComparator
     */
    protected List<Advisor> sortAdvisors(List<Advisor> advisors) {
        AnnotationAwareOrderComparator.sort(advisors);
        return advisors;
    }

    /**
     * Extension hook that subclasses can override to register additional Advisors,
     * given the sorted Advisors obtained to date.
     * <p>The default implementation is empty.
     * <p>Typically used to add Advisors that expose contextual information
     * required by some of the later advisors.
     *
     * @param candidateAdvisors the Advisors that have already been identified as
     *                          applying to a given bean
     */
    protected void extendAdvisors(List<Advisor> candidateAdvisors) {
    }

    /**
     * This auto-proxy creator always returns pre-filtered Advisors.
     */
    @Override
    protected boolean advisorsPreFiltered() {
        return true;
    }


    /**
     * Subclass of BeanFactoryAdvisorRetrievalHelper that delegates to
     * surrounding AbstractAdvisorAutoProxyCreator facilities.
     */
    private class BeanFactoryAdvisorRetrievalHelperAdapter extends BeanFactoryAdvisorRetrievalHelper {

        public BeanFactoryAdvisorRetrievalHelperAdapter(ConfigurableListableBeanFactory beanFactory) {
            super(beanFactory);
        }

        @Override
        protected boolean isEligibleBean(String beanName) {
            return AbstractAdvisorAutoProxyCreator.this.isEligibleAdvisorBean(beanName);
        }
    }

}
