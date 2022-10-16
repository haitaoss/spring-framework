/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.context.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.lang.Nullable;

import java.util.*;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

    private PostProcessorRegistrationDelegate() {
    }


    /**
     * TODOHAITAO
     *
     * @param beanFactory               子类创建的bean 工厂
     * @param beanFactoryPostProcessors 当前bean工厂中的 BeanPostProcessor
     */
    public static void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

        // WARNING: Although it may appear that the body of this method can be easily
        // refactored to avoid the use of multiple loops and multiple lists, the use
        // of multiple lists and multiple passes over the names of processors is
        // intentional. We must ensure that we honor the contracts for PriorityOrdered
        // and Ordered processors. Specifically, we must NOT cause processors to be
        // instantiated (via getBean() invocations) or registered in the ApplicationContext
        // in the wrong order.
        //
        // Before submitting a pull request (PR) to change this method, please review the
        // list of all declined PRs involving changes to PostProcessorRegistrationDelegate
        // to ensure that your proposal does not result in a breaking change:
        // https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

        // 第一步：首先调用 BeanDefinitionRegistryPostProcessor的后置处理器
        // Invoke BeanDefinitionRegistryPostProcessors first, if any.
        Set<String> processedBeans = new HashSet<>();

        // 判断我们的 beanFactory实现了 BeanDefinitionRegistry
        if (beanFactory instanceof BeanDefinitionRegistry) {
            // 强行把我们的bean工厂转为 BeanDefinitionRegistry
            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
            // 保存 BeanFactoryPostProcessor类型的后置
            List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
            // 保存 BeanDefinitionRegistryPostProcessor类型的后置处理器
            List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

            // 循环我们传递进来的 beanFactoryPostProcessors
            for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
                // 判断我们的后置处理器是不是 BeanDefinitionRegistryPostProcessor
                if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                    // 强转
                    BeanDefinitionRegistryPostProcessor registryProcessor = (BeanDefinitionRegistryPostProcessor) postProcessor;
                    // 调用 BeanDefinitionRegistryPostProcessor 的处理器的后置方法
                    registryProcessor.postProcessBeanDefinitionRegistry(registry);
                    // 添加到我们用于保存的 BeanDefinitionRegistryPostProcessor的集合中
                    registryProcessors.add(registryProcessor);
                } else {
                    // 若没有实现 BeanDefinitionRegistryPostProcessor接口，那么他就是 BeanFactoryPostProcessor
                    // 把当前的后置处理器加入到 regularPostProcessors中
                    regularPostProcessors.add(postProcessor);
                }
            }

            // 定义一个集合来保存当前准备创建的 BeanDefinitionRegistryPostProcessor
            // Do not initialize FactoryBeans here: We need to leave all regular beans
            // uninitialized to let the bean factory post-processors apply to them!
            // Separate between BeanDefinitionRegistryPostProcessors that implement
            // PriorityOrdered, Ordered, and the rest.
            List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

            // 第一步：去容器中获取 BeanDefinitionRegistryPostProcessor的bean的处理器名称
            // First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
            String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            // 循环上一步获取的 BeanDefinitionRegistryPostProcessor的类型名称
            for (String ppName : postProcessorNames) {
                // 判断是否实现了 PriorityOrdered 接口的
                if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                    // 显示的调用 getBean() 获取出该对象然后加入到 currentRegistryProcessors集合中去
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    // 同时也加入到 processedBeans 集合中去
                    processedBeans.add(ppName);
                }
            }
            // 对 currentRegistryProcessors集合中 BeanDefinitionRegistryPostProcessor进行排序
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            // 把他加入到用于保存到 registryProcessors中
            registryProcessors.addAll(currentRegistryProcessors);
            /**
             *  在这里典型的 BeanDefinitionRegistryPostProcessor就是 ConfigurationClassPostProcessor
             *  用于进行bean定义的加载，比如我们的包扫描，@import等等。。。。。。。。。
             *  TODOHAITAO BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry。可用来修改和注册 BeanDefinition
             *      注：JavaConfig 就是通过 ConfigurationClassPostProcessor
             */
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
            // 调用完之后，马上clear 掉
            currentRegistryProcessors.clear();

            // 去容器中获取 BeanDefinitionRegistryPostProcessor的bean的处理器名称
            // Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
            postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
            // 循环上一步获取的 BeanDefinitionRegistryPostProcessor的类型名称
            for (String ppName : postProcessorNames) {
                // 表示没有被处理过，且实现了 Ordered接口的
                if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
                    // 显示的调用 getBean()的方式获取出该对象然后加入到 currentRegistryProcessors 集合中去
                    currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                    // 同时也加入到 processedBeans 集合中去
                    processedBeans.add(ppName);
                }
            }
            // 对 currentRegistryProcessors集合中 BeanDefinitionRegistryPostProcessor进行排序
            sortPostProcessors(currentRegistryProcessors, beanFactory);
            // 把他加入到用于保存到 registryProcessors中
            registryProcessors.addAll(currentRegistryProcessors);
            // 调用它的后置处理方法
            invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
            // 调用完之后，马上clear 掉
            currentRegistryProcessors.clear();

            /**
             * TODOHAITAO BeanDefinitionRegistryPostProcessor 的兜底操作
             * 这是是一个兜底操作，因为 有可能会在 BeanDefinitionRegistryPostProcessor#postProcessBeanDefinitionRegistry 里面又注册了 BeanDefinitionRegistryPostProcessor
             * @see cn.haitaoss.javaconfig.beanfactorypostprocessor.MyBeanFactoryPostProcessor3
             */
            // Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
            boolean reiterate = true; //定义一个重复处理的开关变量默认值为true
            // 第一次就可以进来
            while (reiterate) {
                // 进入循环马上把开关变量给改为 false
                reiterate = false;
                postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
                for (String ppName : postProcessorNames) {
                    if (!processedBeans.contains(ppName)) {
                        // 显示的调用 getBean()的方式获取出该对象然后加入到 currentRegistryProcessors 集合中去
                        currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
                        // 同时也加入到 processedBeans 集合中去
                        processedBeans.add(ppName);
                        // 再次设置为true
                        reiterate = true;
                    }
                }
                // 对 currentRegistryProcessors集合中 BeanDefinitionRegistryPostProcessor进行排序
                sortPostProcessors(currentRegistryProcessors, beanFactory);
                // 把他加入到用于保存到 registryProcessors中
                registryProcessors.addAll(currentRegistryProcessors);
                // 调用它的后置处理方法
                invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry, beanFactory.getApplicationStartup());
                // 调用完之后，马上clear 掉
                currentRegistryProcessors.clear();
            }

            // Now, invoke the postProcessBeanFactory callback of all processors handled so far.
            invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
            invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
        } else {
            // Invoke factory processors registered with the context instance.
            invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
        }

        // Do not initialize FactoryBeans here: We need to leave all regular beans
        // uninitialized to let the bean factory post-processors apply to them!
        String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

        // Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        List<String> orderedPostProcessorNames = new ArrayList<>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        for (String ppName : postProcessorNames) {
            if (processedBeans.contains(ppName)) {
                // skip - already processed in first phase above
            } else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        // TODOHAITAO BeanFactoryPostProcessor#postProcessBeanFactory 此时 beanDefinition 都加载完了,可以在这里创建bean 来实现提前创建的目的
        invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

        // Next, invoke the BeanFactoryPostProcessors that implement Ordered.
        List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
        for (String postProcessorName : orderedPostProcessorNames) {
            orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

        // Finally, invoke all other BeanFactoryPostProcessors.
        List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
        for (String postProcessorName : nonOrderedPostProcessorNames) {
            nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
        }
        invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

        // Clear cached merged bean definitions since the post-processors might have
        // modified the original metadata, e.g. replacing placeholders in values...
        beanFactory.clearMetadataCache();
    }

    public static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

        // WARNING: Although it may appear that the body of this method can be easily
        // refactored to avoid the use of multiple loops and multiple lists, the use
        // of multiple lists and multiple passes over the names of processors is
        // intentional. We must ensure that we honor the contracts for PriorityOrdered
        // and Ordered processors. Specifically, we must NOT cause processors to be
        // instantiated (via getBean() invocations) or registered in the ApplicationContext
        // in the wrong order.
        //
        // Before submitting a pull request (PR) to change this method, please review the
        // list of all declined PRs involving changes to PostProcessorRegistrationDelegate
        // to ensure that your proposal does not result in a breaking change:
        // https://github.com/spring-projects/spring-framework/issues?q=PostProcessorRegistrationDelegate+is%3Aclosed+label%3A%22status%3A+declined%22

        String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

        // Register BeanPostProcessorChecker that logs an info message when
        // a bean is created during BeanPostProcessor instantiation, i.e. when
        // a bean is not eligible for getting processed by all BeanPostProcessors.
        int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
        beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

        // Separate between BeanPostProcessors that implement PriorityOrdered,
        // Ordered, and the rest.
        List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
        List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
        List<String> orderedPostProcessorNames = new ArrayList<>();
        List<String> nonOrderedPostProcessorNames = new ArrayList<>();
        for (String ppName : postProcessorNames) {
            if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
                BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
                priorityOrderedPostProcessors.add(pp);
                if (pp instanceof MergedBeanDefinitionPostProcessor) {
                    internalPostProcessors.add(pp);
                }
            } else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
                orderedPostProcessorNames.add(ppName);
            } else {
                nonOrderedPostProcessorNames.add(ppName);
            }
        }

        // First, register the BeanPostProcessors that implement PriorityOrdered.
        sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

        // Next, register the BeanPostProcessors that implement Ordered.
        List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
        for (String ppName : orderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            orderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        sortPostProcessors(orderedPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, orderedPostProcessors);

        // Now, register all regular BeanPostProcessors.
        List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
        for (String ppName : nonOrderedPostProcessorNames) {
            BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
            nonOrderedPostProcessors.add(pp);
            if (pp instanceof MergedBeanDefinitionPostProcessor) {
                internalPostProcessors.add(pp);
            }
        }
        registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

        // Finally, re-register all internal BeanPostProcessors.
        sortPostProcessors(internalPostProcessors, beanFactory);
        registerBeanPostProcessors(beanFactory, internalPostProcessors);

        /**
         * 重新注册ApplicationListenerDetector, 因为在这里会注册一次 {@link AbstractApplicationContext#prepareBeanFactory(ConfigurableListableBeanFactory)}
         *
         * ApplicationListenerDetector 是在 初始化后后置处理阶段 将 ApplicationListener 类型的bean ，将其设置到事件关播器中
         * `this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);`
         * */
        // Re-register post-processor for detecting inner beans as ApplicationListeners,
        // moving it to the end of the processor chain (for picking up proxies etc).
        beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
    }

    private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
        // Nothing to sort?
        if (postProcessors.size() <= 1) {
            return;
        }
        Comparator<Object> comparatorToUse = null;
        if (beanFactory instanceof DefaultListableBeanFactory) {
            comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
        }
        if (comparatorToUse == null) {
            comparatorToUse = OrderComparator.INSTANCE;
        }
        postProcessors.sort(comparatorToUse);
    }

    /**
     * Invoke the given BeanDefinitionRegistryPostProcessor beans.
     */
    private static void invokeBeanDefinitionRegistryPostProcessors(Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry, ApplicationStartup applicationStartup) {

        for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
            StartupStep postProcessBeanDefRegistry = applicationStartup.start("spring.context.beandef-registry.post-process")
                    .tag("postProcessor", postProcessor::toString);
            postProcessor.postProcessBeanDefinitionRegistry(registry);
            postProcessBeanDefRegistry.end();
        }
    }

    /**
     * Invoke the given BeanFactoryPostProcessor beans.
     */
    private static void invokeBeanFactoryPostProcessors(Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

        for (BeanFactoryPostProcessor postProcessor : postProcessors) {
            StartupStep postProcessBeanFactory = beanFactory.getApplicationStartup()
                    .start("spring.context.bean-factory.post-process")
                    .tag("postProcessor", postProcessor::toString);
            postProcessor.postProcessBeanFactory(beanFactory);
            postProcessBeanFactory.end();
        }
    }

    /**
     * Register the given BeanPostProcessor beans.
     */
    private static void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

        if (beanFactory instanceof AbstractBeanFactory) {
            // Bulk addition is more efficient against our CopyOnWriteArrayList there
            ((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
        } else {
            for (BeanPostProcessor postProcessor : postProcessors) {
                beanFactory.addBeanPostProcessor(postProcessor);
            }
        }
    }


    /**
     * BeanPostProcessor that logs an info message when a bean is created during
     * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
     * getting processed by all BeanPostProcessors.
     */
    private static final class BeanPostProcessorChecker implements BeanPostProcessor {

        private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

        private final ConfigurableListableBeanFactory beanFactory;

        private final int beanPostProcessorTargetCount;

        public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
            this.beanFactory = beanFactory;
            this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName)
                    && this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
                if (logger.isInfoEnabled()) {
                    logger.info("Bean '" + beanName + "' of type [" + bean.getClass()
                            .getName() + "] is not eligible for getting processed by all BeanPostProcessors "
                            + "(for example: not eligible for auto-proxying)");
                }
            }
            return bean;
        }

        private boolean isInfrastructureBean(@Nullable String beanName) {
            if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
                BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
                return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
            }
            return false;
        }
    }

}
