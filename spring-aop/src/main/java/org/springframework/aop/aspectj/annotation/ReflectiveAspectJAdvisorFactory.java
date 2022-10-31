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

import org.aopalliance.aop.Advice;
import org.aspectj.lang.annotation.*;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.aspectj.*;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConvertingComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.util.StringUtils;
import org.springframework.util.comparator.InstanceComparator;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factory that can create Spring AOP Advisors given AspectJ classes from
 * classes honoring AspectJ's annotation syntax, using reflection to invoke the
 * corresponding advice methods.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @author Juergen Hoeller
 * @author Ramnivas Laddad
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.0
 */
@SuppressWarnings("serial")
public class ReflectiveAspectJAdvisorFactory extends AbstractAspectJAdvisorFactory implements Serializable {

    /**
     * method 是 class中定义的 且 method 没有 @Pointcut 注解 就返回true
     */
    // Exclude @Pointcut methods
    private static final MethodFilter adviceMethodFilter = ReflectionUtils.USER_DECLARED_METHODS.and(method -> (
            AnnotationUtils.getAnnotation(method, Pointcut.class) == null));

    /**
     * 先按照 method 上面的注解排序(Around、Before、After、AfterReturning、AfterThrowing)，如果相同在按照 methodName 排序
     */
    private static final Comparator<Method> adviceMethodComparator;

    static {
        // Note: although @After is ordered before @AfterReturning and @AfterThrowing,
        // an @After advice method will actually be invoked after @AfterReturning and
        // @AfterThrowing methods due to the fact that AspectJAfterAdvice.invoke(MethodInvocation)
        // invokes proceed() in a `try` block and only invokes the @After advice method
        // in a corresponding `finally` block.
        Comparator<Method> adviceKindComparator = new ConvertingComparator<>(new InstanceComparator<>(Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class), (Converter<Method, Annotation>) method -> {
            AspectJAnnotation<?> ann = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
            return (ann != null ? ann.getAnnotation() : null);
        });
        Comparator<Method> methodNameComparator = new ConvertingComparator<>(Method::getName);
        adviceMethodComparator = adviceKindComparator.thenComparing(methodNameComparator);
    }


    @Nullable
    private final BeanFactory beanFactory;


    /**
     * Create a new {@code ReflectiveAspectJAdvisorFactory}.
     */
    public ReflectiveAspectJAdvisorFactory() {
        this(null);
    }

    /**
     * Create a new {@code ReflectiveAspectJAdvisorFactory}, propagating the given
     * {@link BeanFactory} to the created {@link AspectJExpressionPointcut} instances,
     * for bean pointcut handling as well as consistent {@link ClassLoader} resolution.
     *
     * @param beanFactory the BeanFactory to propagate (may be {@code null}}
     * @see AspectJExpressionPointcut#setBeanFactory
     * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#getBeanClassLoader()
     * @since 4.3.6
     */
    public ReflectiveAspectJAdvisorFactory(@Nullable BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }


    /**
     * 处理有 @Aspect 的类
     * 然后将类里面 Around、Before、After、AfterReturning、AfterThrowing 的方法解析成 InstantiationModelAwarePointcutAdvisorImpl
     * 另外还会根据需要 添加 SyntheticInstantiationAdvisor、DeclareParentsAdvisor 的 advisor
     *
     * @param aspectInstanceFactory the aspect instance factory
     *                              (not the aspect instance itself in order to avoid eager instantiation)
     * @return
     */
    @Override
    public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
        /**
         * @Aspect
         * class A{}
         * @Component
         * class B extends A{}
         *
         * aspectClass 是 A.class
         * aspectName 是 b
         * */
        Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata()
                .getAspectClass();
        String aspectName = aspectInstanceFactory.getAspectMetadata()
                .getAspectName();

        // 校验一下，简单来说就是类得有 @Aspect 注解
        validate(aspectClass);

        // 记录当前切面类的元数据。后面排序Advisor的时候 是根据@Aspect这个切面类来排序的
        // We need to wrap the MetadataAwareAspectInstanceFactory with a decorator
        // so that it will only instantiate once.
        MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory = new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);

        List<Advisor> advisors = new ArrayList<>();
        /**
         * 拿到 aspectClass 中 除了 @Pointcut 注解的方法，按照 Around、Before、After、AfterReturning、AfterThrowing 进行排序
         * */
        for (Method method : getAdvisorMethods(aspectClass)) {
            /**
             * 标注了 Pointcut、Around、Before、After、AfterReturning、AfterThrowing 注解的方法不会返回 null
             * 但是因为 遍历的method 是不包含 @Pointcut 的，所以只是将 Around、Before、After、AfterReturning、AfterThrowing 解析成 Advisor
             *
             * 返回的是是这个实现类 InstantiationModelAwarePointcutAdvisorImpl
             * */
            // Prior to Spring Framework 5.2.7, advisors.size() was supplied as the declarationOrderInAspect
            // to getAdvisor(...) to represent the "current position" in the declared methods list.
            // However, since Java 7 the "current position" is not valid since the JDK no longer
            // returns declared methods in the order in which they are declared in the source code.
            // Thus, we now hard code the declarationOrderInAspect to 0 for all advice methods
            // discovered via reflection in order to support reliable advice ordering across JVM launches.
            // Specifically, a value of 0 aligns with the default value used in
            // AspectJPrecedenceComparator.getAspectDeclarationOrder(Advisor).
            Advisor advisor = getAdvisor(method, lazySingletonAspectInstanceFactory, 0, aspectName);
            if (advisor != null) {
                advisors.add(advisor);
            }
        }

        // If it's a per target aspect, emit the dummy instantiating aspect.
        if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata()
                .isLazilyInstantiated()) {
            // TODOHAITAO: 2022/10/3 这是干啥用的，但是 @Aspect 是不会满足这个条件的
            Advisor instantiationAdvisor = new SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
            advisors.add(0, instantiationAdvisor);
        }

        // 查找引入的字段。
        // Find introduction fields.
        for (Field field : aspectClass.getDeclaredFields()) {
            /**
             * 就是字段上有这个 @DeclareParents(value="",defaultImpl=A.class)  会解析成 DeclareParentsAdvisor
             * */
            Advisor advisor = getDeclareParentsAdvisor(field);
            if (advisor != null) {
                advisors.add(advisor);
            }
        }

        return advisors;
    }

    private List<Method> getAdvisorMethods(Class<?> aspectClass) {
        List<Method> methods = new ArrayList<>();
        /**
         * adviceMethodFilter 逻辑：method 是 class中定义的 且 method 没有 @Pointcut 注解 就返回true
         *
         * 说白了就是将 类里面除了@Pointcut 标注的方法添加到 methods 集合中(会递归处理类的父类和接口里面的method)
         * */
        ReflectionUtils.doWithMethods(aspectClass, methods::add, adviceMethodFilter);
        if (methods.size() > 1) {
            /**
             * 升序排序：先按照 method 上面的注解排序(Around、Before、After、AfterReturning、AfterThrowing)，如果相同在按照 methodName 排序。
             * 注：没有这些注解的方法，的排序是最大的，所以会在最后面
             * */
            methods.sort(adviceMethodComparator);
        }
        return methods;
    }

    /**
     * Build a {@link org.springframework.aop.aspectj.DeclareParentsAdvisor}
     * for the given introduction field.
     * <p>Resulting Advisors will need to be evaluated for targets.
     *
     * @param introductionField the field to introspect
     * @return the Advisor instance, or {@code null} if not an Advisor
     */
    @Nullable
    private Advisor getDeclareParentsAdvisor(Field introductionField) {
        DeclareParents declareParents = introductionField.getAnnotation(DeclareParents.class);
        if (declareParents == null) {
            // Not an introduction field
            return null;
        }

        if (DeclareParents.class == declareParents.defaultImpl()) {
            throw new IllegalStateException("'defaultImpl' attribute must be set on DeclareParents");
        }

        return new DeclareParentsAdvisor(introductionField.getType(), declareParents.value(), declareParents.defaultImpl());
    }


    @Override
    @Nullable
    public Advisor getAdvisor(Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrderInAspect, String aspectName) {

        // 校验一下，简单来说就是类得有 @Aspect 注解
        validate(aspectInstanceFactory.getAspectMetadata()
                .getAspectClass());

        /**
         * 有 {@link AbstractAspectJAdvisorFactory#ASPECTJ_ANNOTATION_CLASSES} 注解才不会返回 null
         * */
        AspectJExpressionPointcut expressionPointcut = getPointcut(candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata()
                .getAspectClass());
        if (expressionPointcut == null) {
            return null;
        }

        return new InstantiationModelAwarePointcutAdvisorImpl(expressionPointcut, candidateAdviceMethod, this, aspectInstanceFactory, declarationOrderInAspect, aspectName);
    }

    /**
     * 就是拿到这些注解 {@link AbstractAspectJAdvisorFactory#ASPECTJ_ANNOTATION_CLASSES} 对应的属性值
     * 装饰成 AspectJExpressionPointcut 对象返回
     * <p>
     * 没有 {@link AbstractAspectJAdvisorFactory#ASPECTJ_ANNOTATION_CLASSES} 直接 就返回null
     *
     * @param candidateAdviceMethod
     * @param candidateAspectClass
     * @return
     */
    @Nullable
    private AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {
        AspectJAnnotation<?> aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
        if (aspectJAnnotation == null) {
            return null;
        }

        AspectJExpressionPointcut ajexp = new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class<?>[0]);
        ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
        if (this.beanFactory != null) {
            ajexp.setBeanFactory(this.beanFactory);
        }
        return ajexp;
    }


    @Override
    @Nullable
    public Advice getAdvice(Method candidateAdviceMethod, AspectJExpressionPointcut expressionPointcut, MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrder, String aspectName) {

        Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata()
                .getAspectClass();
        validate(candidateAspectClass);

        AspectJAnnotation<?> aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
        if (aspectJAnnotation == null) {
            return null;
        }

        // If we get here, we know we have an AspectJ method.
        // Check that it's an AspectJ-annotated class
        if (!isAspect(candidateAspectClass)) {
            throw new AopConfigException(
                    "Advice must be declared inside an aspect type: " + "Offending method '" + candidateAdviceMethod
                            + "' in class [" + candidateAspectClass.getName() + "]");
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Found AspectJ method: " + candidateAdviceMethod);
        }

        AbstractAspectJAdvice springAdvice;

        switch (aspectJAnnotation.getAnnotationType()) {
            case AtPointcut:
                if (logger.isDebugEnabled()) {
                    logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
                }
                return null;
            case AtAround:
                springAdvice = new AspectJAroundAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                break;
            case AtBefore:
                /**
                 * 使用的时候会通过适配器装成MethodInterceptor {@link MethodBeforeAdviceAdapter}
                 * */
                springAdvice = new AspectJMethodBeforeAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                break;
            case AtAfter:
                springAdvice = new AspectJAfterAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                break;
            case AtAfterReturning:
                /**
                 * 使用的时候会通过适配器装成MethodInterceptor {@link AfterReturningAdviceAdapter}
                 * */
                springAdvice = new AspectJAfterReturningAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                AfterReturning afterReturningAnnotation = (AfterReturning) aspectJAnnotation.getAnnotation();
                if (StringUtils.hasText(afterReturningAnnotation.returning())) {
                    springAdvice.setReturningName(afterReturningAnnotation.returning());
                }
                break;
            case AtAfterThrowing:
                springAdvice = new AspectJAfterThrowingAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
                AfterThrowing afterThrowingAnnotation = (AfterThrowing) aspectJAnnotation.getAnnotation();
                if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
                    springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported advice type on method: " + candidateAdviceMethod);
        }

        // Now to configure the advice...
        springAdvice.setAspectName(aspectName);
        springAdvice.setDeclarationOrder(declarationOrder);
        /**
         * 拿到方法的参数名称列表
         * @Before(value = "execution(* test(..))", argNames = "A,B")
         * public void enhanceBefore(ProceedingJoinPoint proceedingJoinPoint, String A, String B) {}
         * 就是拿到 argNames 这个属性
         * */
        String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
        if (argNames != null) {
            // 设置名字
            /**
             * 当 argNames.length == 方法参数列表-1时，且方法的第一个参数是 JoinPoint、ProceedingJoinPoint、StaticPart 类型时
             * 会补上第一个参数的名字。
             * argNames["THIS_JOIN_POINT"，"A","B"]
             * */
            springAdvice.setArgumentNamesFromStringArray(argNames);
        }
        // 参数绑定
        springAdvice.calculateArgumentBindings();

        return springAdvice;
    }


    /**
     * Synthetic advisor that instantiates the aspect.
     * Triggered by per-clause pointcut on non-singleton aspect.
     * The advice has no effect.
     */
    @SuppressWarnings("serial")
    protected static class SyntheticInstantiationAdvisor extends DefaultPointcutAdvisor {

        public SyntheticInstantiationAdvisor(final MetadataAwareAspectInstanceFactory aif) {
            super(aif.getAspectMetadata()
                    .getPerClausePointcut(), (MethodBeforeAdvice) (method, args, target) -> aif.getAspectInstance());
        }
    }

}
