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

package org.springframework.scheduling.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.*;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bean post-processor that registers methods annotated with
 * {@link Scheduled @Scheduled} to be invoked by a
 * {@link org.springframework.scheduling.TaskScheduler} according to the
 * "fixedRate", "fixedDelay", or "cron" expression provided via the annotation.
 *
 * <p>This post-processor is automatically registered by Spring's
 * {@code <task:annotation-driven>} XML element, and also by the
 * {@link EnableScheduling @EnableScheduling} annotation.
 *
 * <p>Autodetects any {@link SchedulingConfigurer} instances in the container,
 * allowing for customization of the scheduler to be used or for fine-grained
 * control over task registration (e.g. registration of {@link Trigger} tasks).
 * See the {@link EnableScheduling @EnableScheduling} javadocs for complete usage
 * details.
 *
 * @author Mark Fisher
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Elizabeth Chatman
 * @author Victor Brown
 * @author Sam Brannen
 * @since 3.0
 * @see Scheduled
 * @see EnableScheduling
 * @see SchedulingConfigurer
 * @see org.springframework.scheduling.TaskScheduler
 * @see org.springframework.scheduling.config.ScheduledTaskRegistrar
 * @see AsyncAnnotationBeanPostProcessor
 */
public class ScheduledAnnotationBeanPostProcessor
        implements ScheduledTaskHolder, MergedBeanDefinitionPostProcessor, DestructionAwareBeanPostProcessor,
        Ordered, EmbeddedValueResolverAware, BeanNameAware, BeanFactoryAware, ApplicationContextAware,
        SmartInitializingSingleton, ApplicationListener<ContextRefreshedEvent>, DisposableBean {

    /**
     * The default name of the {@link TaskScheduler} bean to pick up: {@value}.
     * <p>Note that the initial lookup happens by type; this is just the fallback
     * in case of multiple scheduler beans found in the context.
     * @since 4.2
     */
    public static final String DEFAULT_TASK_SCHEDULER_BEAN_NAME = "taskScheduler";


    protected final Log logger = LogFactory.getLog(getClass());

    private final ScheduledTaskRegistrar registrar;

    @Nullable
    private Object scheduler;

    @Nullable
    private StringValueResolver embeddedValueResolver;

    @Nullable
    private String beanName;

    @Nullable
    private BeanFactory beanFactory;

    @Nullable
    private ApplicationContext applicationContext;

    private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));

    /**
     * 记录bean对应的调度任务,当bean被销毁时应当销毁对应的定时任务
     */
    private final Map<Object, Set<ScheduledTask>> scheduledTasks = new IdentityHashMap<>(16);


    /**
     * Create a default {@code ScheduledAnnotationBeanPostProcessor}.
     */
    public ScheduledAnnotationBeanPostProcessor() {
        this.registrar = new ScheduledTaskRegistrar();
    }

    /**
     * Create a {@code ScheduledAnnotationBeanPostProcessor} delegating to the
     * specified {@link ScheduledTaskRegistrar}.
     * @param registrar the ScheduledTaskRegistrar to register {@code @Scheduled}
     * tasks on
     * @since 5.1
     */
    public ScheduledAnnotationBeanPostProcessor(ScheduledTaskRegistrar registrar) {
        Assert.notNull(registrar, "ScheduledTaskRegistrar is required");
        this.registrar = registrar;
    }


    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    /**
     * Set the {@link org.springframework.scheduling.TaskScheduler} that will invoke
     * the scheduled methods, or a {@link java.util.concurrent.ScheduledExecutorService}
     * to be wrapped as a TaskScheduler.
     * <p>If not specified, default scheduler resolution will apply: searching for a
     * unique {@link TaskScheduler} bean in the context, or for a {@link TaskScheduler}
     * bean named "taskScheduler" otherwise; the same lookup will also be performed for
     * a {@link ScheduledExecutorService} bean. If neither of the two is resolvable,
     * a local single-threaded default scheduler will be created within the registrar.
     * @see #DEFAULT_TASK_SCHEDULER_BEAN_NAME
     */
    public void setScheduler(Object scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.embeddedValueResolver = resolver;
    }

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    /**
     * Making a {@link BeanFactory} available is optional; if not set,
     * {@link SchedulingConfigurer} beans won't get autodetected and
     * a {@link #setScheduler scheduler} has to be explicitly configured.
     */
    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * Setting an {@link ApplicationContext} is optional: If set, registered
     * tasks will be activated in the {@link ContextRefreshedEvent} phase;
     * if not set, it will happen at {@link #afterSingletonsInstantiated} time.
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        if (this.beanFactory == null) {
            this.beanFactory = applicationContext;
        }
    }


    @Override
    public void afterSingletonsInstantiated() {
        // 清除已解析的单例bean信息，因为是单例bean所有不会再创建了，所以这里可以直接情况，省内存
        // Remove resolved singleton classes from cache
        this.nonAnnotatedClasses.clear();

        // 没有 ApplicationContext 提前注册task
        if (this.applicationContext == null) {
            // Not running in an ApplicationContext -> register tasks early...
            finishRegistration();
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() == this.applicationContext) {
            /**
             * 在IOC容器refresh结束时会执行{@link AbstractApplicationContext#finishRefresh()}，从而发布 ContextRefreshedEvent 事件
             * 收到容器刷新完的事件 就触发 task的注册，就是开始调度任务
             * */
            // Running in an ApplicationContext -> register tasks this late...
            // giving other ContextRefreshedEvent listeners a chance to perform
            // their work at the same time (e.g. Spring Batch's job registration).
            finishRegistration();
        }
    }

    private void finishRegistration() {
        // 默认是没得的
        if (this.scheduler != null) {
            this.registrar.setScheduler(this.scheduler);
        }

        if (this.beanFactory instanceof ListableBeanFactory) {
            // 从容器中获取 SchedulingConfigurer
            Map<String, SchedulingConfigurer> beans =
                    ((ListableBeanFactory) this.beanFactory).getBeansOfType(SchedulingConfigurer.class);
            List<SchedulingConfigurer> configurers = new ArrayList<>(beans.values());
            // 升序排列
            AnnotationAwareOrderComparator.sort(configurers);
            for (SchedulingConfigurer configurer : configurers) {
                /**
                 * 遍历 configurers 完成对 registrar 的加工。
                 * 看名字建议是让我们扩展类task的
                 * */
                configurer.configureTasks(this.registrar);
            }
        }

        /**
         * 有task 但是没有 TaskScheduler，所以需要从容器中拿到 TaskScheduler。
         *
         * 查找顺序 ：TaskScheduler -> ScheduledExecutorService
         * 注：都是byName taskScheduler 找，找不到在byType找。找不到或者匹配多个无法确定，也不会报错只会导致 taskScheduler 是 null而已，后面会设置默认值
         *      this.localExecutor = Executors.newSingleThreadScheduledExecutor();
         *      this.taskScheduler = new ConcurrentTaskScheduler(this.localExecutor);
         * */
        if (this.registrar.hasTasks() && this.registrar.getScheduler() == null) {
            Assert.state(this.beanFactory != null, "BeanFactory must be set to find scheduler by type");
            try {
                // Search for TaskScheduler bean...
                this.registrar.setTaskScheduler(resolveSchedulerBean(this.beanFactory, TaskScheduler.class, false));
            } catch (NoUniqueBeanDefinitionException ex) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Could not find unique TaskScheduler bean - attempting to resolve by name: " +
                            ex.getMessage());
                }
                try {
                    this.registrar.setTaskScheduler(resolveSchedulerBean(this.beanFactory, TaskScheduler.class, true));
                } catch (NoSuchBeanDefinitionException ex2) {
                    if (logger.isInfoEnabled()) {
                        logger.info("More than one TaskScheduler bean exists within the context, and " +
                                "none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
                                "(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
                                "ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
                                ex.getBeanNamesFound());
                    }
                }
            } catch (NoSuchBeanDefinitionException ex) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Could not find default TaskScheduler bean - attempting to find ScheduledExecutorService: " +
                            ex.getMessage());
                }
                // Search for ScheduledExecutorService bean next...
                try {
                    this.registrar.setScheduler(resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, false));
                } catch (NoUniqueBeanDefinitionException ex2) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Could not find unique ScheduledExecutorService bean - attempting to resolve by name: " +
                                ex2.getMessage());
                    }
                    try {
                        this.registrar.setScheduler(resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, true));
                    } catch (NoSuchBeanDefinitionException ex3) {
                        if (logger.isInfoEnabled()) {
                            logger.info("More than one ScheduledExecutorService bean exists within the context, and " +
                                    "none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' " +
                                    "(possibly as an alias); or implement the SchedulingConfigurer interface and call " +
                                    "ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: " +
                                    ex2.getBeanNamesFound());
                        }
                    }
                } catch (NoSuchBeanDefinitionException ex2) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Could not find default ScheduledExecutorService bean - falling back to default: " +
                                ex2.getMessage());
                    }
                    // Giving up -> falling back to default scheduler within the registrar...
                    logger.info("No TaskScheduler/ScheduledExecutorService bean found for scheduled processing");
                }
            }
        }
        // 回调接口方法，调度注册的task
        this.registrar.afterPropertiesSet();
    }

    private <T> T resolveSchedulerBean(BeanFactory beanFactory, Class<T> schedulerType, boolean byName) {
        if (byName) {
            // 根据名称+类型获取
            T scheduler = beanFactory.getBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME, schedulerType);
            if (this.beanName != null && this.beanFactory instanceof ConfigurableBeanFactory) {
                // 注册依赖关系
                ((ConfigurableBeanFactory) this.beanFactory).registerDependentBean(
                        DEFAULT_TASK_SCHEDULER_BEAN_NAME, this.beanName);
            }
            return scheduler;
        } else if (beanFactory instanceof AutowireCapableBeanFactory) {
            /**
             * 默认的BeanFactory是DefaultListableBeanFactory，其继承 AutowireCapableBeanFactory
             *
             * 根据类型获取
             * */
            NamedBeanHolder<T> holder = ((AutowireCapableBeanFactory) beanFactory).resolveNamedBean(schedulerType);
            if (this.beanName != null && beanFactory instanceof ConfigurableBeanFactory) {
                // 注册依赖关系
                ((ConfigurableBeanFactory) beanFactory).registerDependentBean(holder.getBeanName(), this.beanName);
            }
            return holder.getBeanInstance();
        } else {
            // 根据类型获取
            return beanFactory.getBean(schedulerType);
        }
    }


    @Override
    public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // 忽略这些类型
        if (bean instanceof AopInfrastructureBean || bean instanceof TaskScheduler ||
                bean instanceof ScheduledExecutorService) {
            // Ignore AOP infrastructure such as scoped proxies.
            return bean;
        }
        /**
         * 拿到代理类 最根本的beanClass。说白了就是拿到被代理类
         * */
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);

        if (!this.nonAnnotatedClasses.contains(targetClass) &&
                AnnotationUtils.isCandidateClass(targetClass, Arrays.asList(Scheduled.class, Schedules.class))) {
            /**
             * 会递归 类的父类，接口的父接口 里面的方法，判断是否有 @Scheduled、@Schedules
             *
             * 返回的就是符合条件的 Method 集合
             * */
            Map<Method, Set<Scheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
                    (MethodIntrospector.MetadataLookup<Set<Scheduled>>) method -> {
                        Set<Scheduled> scheduledAnnotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(
                                method, Scheduled.class, Schedules.class);
                        return (!scheduledAnnotations.isEmpty() ? scheduledAnnotations : null);
                    });
            if (annotatedMethods.isEmpty()) {
                // 记录起来，下次就别解析了
                this.nonAnnotatedClasses.add(targetClass);
                if (logger.isTraceEnabled()) {
                    logger.trace("No @Scheduled annotations found on bean class: " + targetClass);
                }
            } else {
                // 遍历所有的方法
                // Non-empty set of methods
                annotatedMethods.forEach((method, scheduledAnnotations) ->
                        /**
                         * 因为方法可以写多个 @Scheduled ，也就是多个注解对应一个方法，
                         * 所以遍历注解进行解析 {@link ScheduledAnnotationBeanPostProcessor#processScheduled(Scheduled, Method, Object)}
                         *
                         * 就是解析 @Scheduled 成 CronTask、FixedDelayTask、FixedRateTask 注册到  {@link ScheduledAnnotationBeanPostProcessor#registrar} -> ScheduledTaskRegistrar
                         *
                         * 然后再IOC容器refresh完成时 会发布ContextRefreshedEvent事件，从而会回调 {@link ScheduledAnnotationBeanPostProcessor#onApplicationEvent(ContextRefreshedEvent)}
                         *      1. 给 ScheduledTaskRegistrar 设置属性 {@link ScheduledTaskRegistrar#setScheduler(Object)}，这个属性是用来调度Task的
                         *      2. 执行 ScheduledTaskRegistrar 的 {@link ScheduledTaskRegistrar#afterPropertiesSet()}，就是调度记录的Task
                         * */
                        scheduledAnnotations.forEach(scheduled -> processScheduled(scheduled, method, bean)));
                if (logger.isTraceEnabled()) {
                    logger.trace(annotatedMethods.size() + " @Scheduled methods processed on bean '" + beanName +
                            "': " + annotatedMethods);
                }
            }
        }
        // 返回bean对象
        return bean;
    }

    /**
     * Process the given {@code @Scheduled} method declaration on the given bean.
     * @param scheduled the {@code @Scheduled} annotation
     * @param method the method that the annotation has been declared on
     * @param bean the target bean instance
     * @see #createRunnable(Object, Method)
     */
    protected void processScheduled(Scheduled scheduled, Method method, Object bean) {
        try {
            /**
             * 将 Method、Bean 装饰成 ScheduledMethodRunnable 对象。
             * 注意：实例化时会校验方法参数列表必须是0个，不然就报错。也就是说 @Scheduled 不支持 有参数的方法
             * */
            Runnable runnable = createRunnable(bean, method);
            /**
             * 标记@Scheduled是否已经解析成 Task，解析了就是true。
             * 目的是 @Scheduled(fixedDelay = 1, cron = "1 * * * * ?", fixedRate = 1) 这三个只能必须设置一个，否则直接跑出异常
             * */
            boolean processedSchedule = false;
            String errorMessage =
                    "Exactly one of the 'cron', 'fixedDelay(String)', or 'fixedRate(String)' attributes is required";

            Set<ScheduledTask> tasks = new LinkedHashSet<>(4);
            /**
             * 初始延时时间。
             * 默认值 @Scheduled(initialDelay = -1, timeUnit = TimeUnit.MILLISECONDS) 就不会计算，返回的是-1
             * */
            // Determine initial delay
            long initialDelay = convertToMillis(scheduled.initialDelay(), scheduled.timeUnit());
            /**
             * 拿到注解的值 @Scheduled(initialDelayString = "")
             * 支持占位符，如果存在就会覆盖掉 @Scheduled(initialDelay = 1) 设置的值
             * */
            String initialDelayString = scheduled.initialDelayString();
            if (StringUtils.hasText(initialDelayString)) {
                /**
                 * 就是这两个不能同时指定
                 * @Scheduled(initialDelayString = "1", initialDelay = 1)
                 * */
                Assert.isTrue(initialDelay < 0, "Specify 'initialDelay' or 'initialDelayString', not both");
                if (this.embeddedValueResolver != null) {
                    // 解析表达式
                    initialDelayString = this.embeddedValueResolver.resolveStringValue(initialDelayString);
                }
                if (StringUtils.hasLength(initialDelayString)) {
                    try {
                        // 覆盖延时时间参数
                        initialDelay = convertToMillis(initialDelayString, scheduled.timeUnit());
                    } catch (RuntimeException ex) {
                        throw new IllegalArgumentException(
                                "Invalid initialDelayString value \"" + initialDelayString + "\" - cannot parse into long");
                    }
                }
            }

            // 处理 @Scheduled(cron = "1 * * * * ?")
            // Check cron expression
            String cron = scheduled.cron();
            if (StringUtils.hasText(cron)) {
                String zone = scheduled.zone();
                if (this.embeddedValueResolver != null) {
                    // 模板解析
                    cron = this.embeddedValueResolver.resolveStringValue(cron);
                    zone = this.embeddedValueResolver.resolveStringValue(zone);
                }
                if (StringUtils.hasLength(cron)) {
                    /**
                     * @Scheduled(cron = "1 * * * * ?")
                     * 也就是指定 cron 就不能设置 initialDelayString、initialDelay、timeUnit
                     * */
                    Assert.isTrue(initialDelay == -1, "'initialDelay' not supported for cron triggers");
                    // 设置为true
                    processedSchedule = true;
                    // 不是默认值 -，才注册定时任务
                    if (!Scheduled.CRON_DISABLED.equals(cron)) {
                        TimeZone timeZone;
                        if (StringUtils.hasText(zone)) {
                            timeZone = StringUtils.parseTimeZoneString(zone);
                        } else {
                            timeZone = TimeZone.getDefault();
                        }
                        /**
                         * 1. 使用 ScheduledTaskRegistrar 调度 CronTask
                         * 2. 记录 task
                         *
                         * cron表达式的解析 {@link CronExpression#parse(String)}
                         * */
                        tasks.add(this.registrar.scheduleCronTask(new CronTask(runnable, new CronTrigger(cron, timeZone))));
                    }
                }
            }

            // At this point we don't need to differentiate between initial delay set or not anymore
            if (initialDelay < 0) {
                initialDelay = 0;
            }

            // 处理 @Scheduled(fixedDelay = 1)
            // Check fixed delay
            long fixedDelay = convertToMillis(scheduled.fixedDelay(), scheduled.timeUnit());
            if (fixedDelay >= 0) {
                /**
                 * 比如 @Scheduled(fixedDelay = 1,cron = "1 * * * * ?") 就会报错，
                 * 也就是说 fixedDelay 和 cron 只能设置一个
                 * */
                Assert.isTrue(!processedSchedule, errorMessage);
                processedSchedule = true;
                // 注册 FixedDelayTask
                tasks.add(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
            }

            String fixedDelayString = scheduled.fixedDelayString();
            if (StringUtils.hasText(fixedDelayString)) {
                if (this.embeddedValueResolver != null) {
                    fixedDelayString = this.embeddedValueResolver.resolveStringValue(fixedDelayString);
                }
                if (StringUtils.hasLength(fixedDelayString)) {
                    // 保证一个 @Scheduled 解析成一个 Task
                    Assert.isTrue(!processedSchedule, errorMessage);
                    processedSchedule = true;
                    try {
                        fixedDelay = convertToMillis(fixedDelayString, scheduled.timeUnit());
                    } catch (RuntimeException ex) {
                        throw new IllegalArgumentException(
                                "Invalid fixedDelayString value \"" + fixedDelayString + "\" - cannot parse into long");
                    }
                    // 注册 FixedDelayTask
                    tasks.add(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, initialDelay)));
                }
            }

            // 处理 @Scheduled(fixedRate = 1)
            // Check fixed rate
            long fixedRate = convertToMillis(scheduled.fixedRate(), scheduled.timeUnit());
            if (fixedRate >= 0) {
                // 保证一个 @Scheduled 解析成一个 Task
                Assert.isTrue(!processedSchedule, errorMessage);
                processedSchedule = true;
                // 注册 FixedRateTask
                tasks.add(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
            }
            String fixedRateString = scheduled.fixedRateString();
            if (StringUtils.hasText(fixedRateString)) {
                if (this.embeddedValueResolver != null) {
                    fixedRateString = this.embeddedValueResolver.resolveStringValue(fixedRateString);
                }
                if (StringUtils.hasLength(fixedRateString)) {
                    Assert.isTrue(!processedSchedule, errorMessage);
                    processedSchedule = true;
                    try {
                        fixedRate = convertToMillis(fixedRateString, scheduled.timeUnit());
                    } catch (RuntimeException ex) {
                        throw new IllegalArgumentException(
                                "Invalid fixedRateString value \"" + fixedRateString + "\" - cannot parse into long");
                    }
                    // 注册 FixedRateTask
                    tasks.add(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, initialDelay)));
                }
            }

            /**
             * 就是 @Scheduled(fixedDelay = 1, cron = "1 * * * * ?", fixedRate = 1) 必须指定一个，一个都没设置就报错
             * */
            // Check whether we had any attribute set
            Assert.isTrue(processedSchedule, errorMessage);

            /**
             * 记录bean对应的调度任务,当bean被销毁时应当销毁对应的定时任务
             * {@link ScheduledAnnotationBeanPostProcessor#postProcessBeforeDestruction(Object, String)}
             * */
            // Finally register the scheduled tasks
            synchronized (this.scheduledTasks) {
                Set<ScheduledTask> regTasks = this.scheduledTasks.computeIfAbsent(bean, key -> new LinkedHashSet<>(4));
                /**
                 * 记录起来
                 *
                 * 销毁Task对应的bean对象时，会销毁其关联的Task
                 * {@link ScheduledAnnotationBeanPostProcessor#postProcessBeforeDestruction(Object, String)}
                 * */
                regTasks.addAll(tasks);
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Encountered invalid @Scheduled method '" + method.getName() + "': " + ex.getMessage());
        }
    }

    /**
     * Create a {@link Runnable} for the given bean instance,
     * calling the specified scheduled method.
     * <p>The default implementation creates a {@link ScheduledMethodRunnable}.
     * @param target the target bean instance
     * @param method the scheduled method to call
     * @since 5.1
     * @see ScheduledMethodRunnable#ScheduledMethodRunnable(Object, Method)
     */
    protected Runnable createRunnable(Object target, Method method) {
        // 校验方法参数。这你表示不支持方法有参数
        Assert.isTrue(method.getParameterCount() == 0, "Only no-arg methods may be annotated with @Scheduled");
        Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
        return new ScheduledMethodRunnable(target, invocableMethod);
    }

    private static long convertToMillis(long value, TimeUnit timeUnit) {
        return TimeUnit.MILLISECONDS.convert(value, timeUnit);
    }

    private static long convertToMillis(String value, TimeUnit timeUnit) {
        if (isDurationString(value)) {
            return Duration.parse(value).toMillis();
        }
        return convertToMillis(Long.parseLong(value), timeUnit);
    }

    private static boolean isDurationString(String value) {
        return (value.length() > 1 && (isP(value.charAt(0)) || isP(value.charAt(1))));
    }

    private static boolean isP(char ch) {
        return (ch == 'P' || ch == 'p');
    }


    /**
     * Return all currently scheduled tasks, from {@link Scheduled} methods
     * as well as from programmatic {@link SchedulingConfigurer} interaction.
     * @since 5.0.2
     */
    @Override
    public Set<ScheduledTask> getScheduledTasks() {
        Set<ScheduledTask> result = new LinkedHashSet<>();
        synchronized (this.scheduledTasks) {
            Collection<Set<ScheduledTask>> allTasks = this.scheduledTasks.values();
            for (Set<ScheduledTask> tasks : allTasks) {
                result.addAll(tasks);
            }
        }
        result.addAll(this.registrar.getScheduledTasks());
        return result;
    }

    @Override
    public void postProcessBeforeDestruction(Object bean, String beanName) {
        Set<ScheduledTask> tasks;
        synchronized (this.scheduledTasks) {
            // 取出bean对应的task
            tasks = this.scheduledTasks.remove(bean);
        }
        if (tasks != null) {
            for (ScheduledTask task : tasks) {
                // 取消定时任务的执行
                task.cancel();
            }
        }
    }

    @Override
    public boolean requiresDestruction(Object bean) {
        synchronized (this.scheduledTasks) {
            return this.scheduledTasks.containsKey(bean);
        }
    }

    @Override
    public void destroy() {
        synchronized (this.scheduledTasks) {
            Collection<Set<ScheduledTask>> allTasks = this.scheduledTasks.values();
            for (Set<ScheduledTask> tasks : allTasks) {
                for (ScheduledTask task : tasks) {
                    // 中断任务
                    task.cancel();
                }
            }
            // 清空
            this.scheduledTasks.clear();
        }
        /**
         * 1. 停止所有调度的任务
         * 2. 关闭线程池
         * */
        this.registrar.destroy();
    }

}
