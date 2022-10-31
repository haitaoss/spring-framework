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

package org.springframework.transaction.support;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.OrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionalApplicationListenerMethodAdapter;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.util.*;

/**
 * 管理每个线程的资源和事务同步的中央委托。供资源管理代码使用，而不供典型应用程序代码使用。
 *
 * 人话就是记录每个线程的事务(简单理解就是数据库连接)。Spring事务依赖这个类记录的状态信息来完成事务的创建、事务的传播行为该怎么处理等
 * 前提是的使用Spring提供的方法获取数据库连接 {@link org.springframework.jdbc.datasource.DataSourceUtils} 才能使用上Spring事务的功能。
 * 也就是持久层框架，要想使用Spring事务，必须的使用DataSourceUtils获取的Connection才能对接上Spring事务
 *
 * Central delegate that manages resources and transaction synchronizations per thread.
 * To be used by resource management code but not by typical application code.
 *
 * <p>Supports one resource per key without overwriting, that is, a resource needs
 * to be removed before a new one can be set for the same key.
 * Supports a list of transaction synchronizations if synchronization is active.
 *
 * <p>Resource management code should check for thread-bound resources, e.g. JDBC
 * Connections or Hibernate Sessions, via {@code getResource}. Such code is
 * normally not supposed to bind resources to threads, as this is the responsibility
 * of transaction managers. A further option is to lazily bind on first use if
 * transaction synchronization is active, for performing transactions that span
 * an arbitrary number of resources.
 *
 * <p>Transaction synchronization must be activated and deactivated by a transaction
 * manager via {@link #initSynchronization()} and {@link #clearSynchronization()}.
 * This is automatically supported by {@link AbstractPlatformTransactionManager},
 * and thus by all standard Spring transaction managers, such as
 * {@link org.springframework.transaction.jta.JtaTransactionManager} and
 * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager}.
 *
 * <p>Resource management code should only register synchronizations when this
 * manager is active, which can be checked via {@link #isSynchronizationActive};
 * it should perform immediate resource cleanup else. If transaction synchronization
 * isn't active, there is either no current transaction, or the transaction manager
 * doesn't support transaction synchronization.
 *
 * <p>Synchronization is for example used to always return the same resources
 * within a JTA transaction, e.g. a JDBC Connection or a Hibernate Session for
 * any given DataSource or SessionFactory, respectively.
 *
 * @author Juergen Hoeller
 * @since 02.06.2003
 * @see #isSynchronizationActive
 * @see #registerSynchronization
 * @see TransactionSynchronization
 * @see AbstractPlatformTransactionManager#setTransactionSynchronization
 * @see org.springframework.transaction.jta.JtaTransactionManager
 * @see org.springframework.jdbc.datasource.DataSourceTransactionManager
 * @see org.springframework.jdbc.datasource.DataSourceUtils#getConnection
 */
public abstract class TransactionSynchronizationManager {

    /**
     * 事务资源，就是当前事物内涉及到的所有资源（数据库连接）
     *
     * 比如数据库连接：
     *      Key：DataSource 对象
     *      Value：ConnectionHolder
     *
     * 什么时候会设置值：
     *      1. 开启新事务时，会通过 DataSource 获取连接，并将连接存到这里
     *      2. 执行 {@link org.springframework.jdbc.datasource.DataSourceUtils#getConnection(DataSource)} 获取连接，使用 DataSource 做为key从 resources中找不到连接，
     *          就会使用 DataSource获取连接，存到 synchronizations 和 这里
     *      注：前提是在事务内执行。简单来说就是Java虚拟机栈中存在@Transactional的方法(就是有{@link TransactionInterceptor#invoke(MethodInvocation)})
     *
     * 什么时候移除key对应属性值：
     *      - 暂停当前事务
     *      - 完成当前事务(rollback或者commit)
     *      Tips：{@link TransactionSynchronizationManager#doUnbindResource(Object)}
     */
    private static final ThreadLocal<Map<Object, Object>> resources =
            new NamedThreadLocal<>("Transactional resources");

    /**
     * 事务同步资源，就是在事务中产生的 非事务管理器数据源生成的连接或者是用于在事务完成时(rollback或者commit)要触发事件，都算是事务同步资源
     *
     * 比如：
     *      - ConnectionSynchronization
     *          使用工具类获取连接会设置 {@link org.springframework.jdbc.datasource.DataSourceUtils#doGetConnection(DataSource)}
     *          这种连接和事务连接不一样，是直接通过数据源获取的连接，不会配置自动提交、超时时间、隔离级别，默认是啥就是啥，
     *          唯一的作用就是在事务完成时(rollback或者commit) 释放掉这些连接
     *          比如：获取连接用的数据源(d1)和事务管理的数据源(d2)不是同一个时，就会将d1创建的连接装饰成 ConnectionSynchronization，
     *              并将该对象存到 synchronizations 属性中，然后对应的连接也会存到注册到 resources 中
     *
     *      - TransactionalApplicationListenerSynchronization
     *          发布事务事件时会设置 {@link TransactionalApplicationListenerMethodAdapter#onApplicationEvent(ApplicationEvent)}
     *          作用就是在事务完成时(rollback或者commit) 回调其 TransactionalApplicationListener、SynchronizationCallback
     *
     * 什么时候会设置值：简单来说就是Java虚拟机栈中存在@Transactional的方法(就是有{@link TransactionInterceptor#invoke(MethodInvocation)})
     *      具体一点就是执行完 {@link TransactionAspectSupport#createTransactionIfNecessary(PlatformTransactionManager, TransactionAttribute, String)}
     *      该属性就会被初始化，在使用过程中会根据 `synchronizations.get() != null ` 来判断是激活了事务同步 {@link TransactionSynchronizationManager#isSynchronizationActive()}
     *
     * 什么时候清空该属性值：
     *      1. 暂停当前事务
     *      2. 完成当前事务(rollback或者commit)
     *      Tips：{@link TransactionSynchronizationManager#clear()}
     */
    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations =
            new NamedThreadLocal<>("Transaction synchronizations");

    private static final ThreadLocal<String> currentTransactionName =
            new NamedThreadLocal<>("Current transaction name");

    private static final ThreadLocal<Boolean> currentTransactionReadOnly =
            new NamedThreadLocal<>("Current transaction read-only status");

    private static final ThreadLocal<Integer> currentTransactionIsolationLevel =
            new NamedThreadLocal<>("Current transaction isolation level");

    /**
     * 实际激活的事务。
     *  - 当前线程的事务不是空事务 就是 true
     *  - 空事务 或者 执行非事务方法就是 false
     */
    private static final ThreadLocal<Boolean> actualTransactionActive =
            new NamedThreadLocal<>("Actual transaction active");


    //-------------------------------------------------------------------------
    // Management of transaction-associated resource handles
    //-------------------------------------------------------------------------

    /**
     * Return all resources that are bound to the current thread.
     * <p>Mainly for debugging purposes. Resource managers should always invoke
     * {@code hasResource} for a specific resource key that they are interested in.
     * @return a Map with resource keys (usually the resource factory) and resource
     * values (usually the active resource object), or an empty Map if there are
     * currently no resources bound
     * @see #hasResource
     */
    public static Map<Object, Object> getResourceMap() {
        Map<Object, Object> map = resources.get();
        return (map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap());
    }

    /**
     * Check if there is a resource for the given key bound to the current thread.
     * @param key the key to check (usually the resource factory)
     * @return if there is a value bound to the current thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static boolean hasResource(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doGetResource(actualKey);
        return (value != null);
    }

    /**
     * Retrieve a resource for the given key that is bound to the current thread.
     * @param key the key to check (usually the resource factory)
     * @return a value bound to the current thread (usually the active
     * resource object), or {@code null} if none
     * @see ResourceTransactionManager#getResourceFactory()
     */
    @Nullable
    public static Object getResource(Object key) {
        /**
         * key如果是代理对象，就返回 被代理对象
         * */
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        /**
         * 从ThreadLocal中使用 actualKey 获取 ResourceHolder，如果 {@link ResourceHolder#isVoid()} 就会从缓存中删掉，返回null，
         * 否则就返回 缓存的值
         * */
        return doGetResource(actualKey);
    }

    /**
     * Actually check the value of the resource that is bound for the given key.
     */
    @Nullable
    private static Object doGetResource(Object actualKey) {
        // 从ThreadLocal中获取
        Map<Object, Object> map = resources.get();
        if (map == null) {
            return null;
        }
        Object value = map.get(actualKey);
        /**
         * 比如数据库连接 ConnectionHolder 就是实现了 ResourceHolder 接口
         * 当 ConnectionHolder {@link ResourceHolderSupport#unbound()} 是 isVoid 就是true，也就是说 ConnectionHolder 不再使用了，执行该方法可以
         * 删除 ConnectionHolder 的缓存
         * */
        // Transparently remove ResourceHolder that was marked as void...
        if (value instanceof ResourceHolder && ((ResourceHolder) value).isVoid()) {
            // 删除缓存
            map.remove(actualKey);
            // Remove entire ThreadLocal if empty...
            if (map.isEmpty()) {
                resources.remove();
            }
            value = null;
        }
        return value;
    }

    /**
     * 绑定资源，同一线程同一key只能绑定一个资源，如果key存在资源 必须是无效的资源否则 报错
     * Bind the given resource for the given key to the current thread.
     * @param key the key to bind the value to (usually the resource factory)
     * @param value the value to bind (usually the active resource object)
     * @throws IllegalStateException if there is already a value bound to the thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static void bindResource(Object key, Object value) throws IllegalStateException {
        // 拿到key
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Assert.notNull(value, "Value must not be null");
        Map<Object, Object> map = resources.get();
        // set ThreadLocal Map if none found
        if (map == null) {
            // 初始化
            map = new HashMap<>();
            // 保存到 ThreadLocal
            resources.set(map);
        }
        Object oldValue = map.put(actualKey, value);
        // Transparently suppress a ResourceHolder that was marked as void...
        if (oldValue instanceof ResourceHolder && ((ResourceHolder) oldValue).isVoid()) {
            // 无效的资源 就置为null
            oldValue = null;
        }
        /**
         * 这里的校验，就确保了，同一线程同一个key只能绑定一个资源。
         * */
        if (oldValue != null) {
            throw new IllegalStateException(
                    "Already value [" + oldValue + "] for key [" + actualKey + "] bound to thread");
        }
    }

    /**
     * Unbind a resource for the given key from the current thread.
     * @param key the key to unbind (usually the resource factory)
     * @return the previously bound value (usually the active resource object)
     * @throws IllegalStateException if there is no value bound to the thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static Object unbindResource(Object key) throws IllegalStateException {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doUnbindResource(actualKey);
        if (value == null) {
            throw new IllegalStateException("No value for key [" + actualKey + "] bound to thread");
        }
        return value;
    }

    /**
     * Unbind a resource for the given key from the current thread.
     * @param key the key to unbind (usually the resource factory)
     * @return the previously bound value, or {@code null} if none bound
     */
    @Nullable
    public static Object unbindResourceIfPossible(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        return doUnbindResource(actualKey);
    }

    /**
     * 取消线程绑定的资源
     * Actually remove the value of the resource that is bound for the given key.
     */
    @Nullable
    private static Object doUnbindResource(Object actualKey) {
        Map<Object, Object> map = resources.get();
        if (map == null) {
            return null;
        }
        Object value = map.remove(actualKey);
        // Remove entire ThreadLocal if empty...
        if (map.isEmpty()) {
            resources.remove();
        }
        // Transparently suppress a ResourceHolder that was marked as void...
        if (value instanceof ResourceHolder && ((ResourceHolder) value).isVoid()) {
            value = null;
        }
        return value;
    }


    //-------------------------------------------------------------------------
    // Management of transaction synchronizations
    //-------------------------------------------------------------------------

    /**
     * 可以这里理解，只要Java虚拟机栈中存在@Transactional的方法，这个判断就是true
     * Return if transaction synchronization is active for the current thread.
     * Can be called before register to avoid unnecessary instance creation.
     * @see #registerSynchronization
     */
    public static boolean isSynchronizationActive() {
        return (synchronizations.get() != null);
    }

    /**
     * Activate transaction synchronization for the current thread.
     * Called by a transaction manager on transaction begin.
     * @throws IllegalStateException if synchronization is already active
     */
    public static void initSynchronization() throws IllegalStateException {
        if (isSynchronizationActive()) {
            throw new IllegalStateException("Cannot activate transaction synchronization - already active");
        }
        synchronizations.set(new LinkedHashSet<>());
    }

    /**
     * Register a new transaction synchronization for the current thread.
     * Typically called by resource management code.
     * <p>Note that synchronizations can implement the
     * {@link org.springframework.core.Ordered} interface.
     * They will be executed in an order according to their order value (if any).
     * @param synchronization the synchronization object to register
     * @throws IllegalStateException if transaction synchronization is not active
     * @see org.springframework.core.Ordered
     */
    public static void registerSynchronization(TransactionSynchronization synchronization)
            throws IllegalStateException {

        Assert.notNull(synchronization, "TransactionSynchronization must not be null");
        Set<TransactionSynchronization> synchs = synchronizations.get();
        if (synchs == null) {
            throw new IllegalStateException("Transaction synchronization is not active");
        }
        synchs.add(synchronization);
    }

    /**
     * Return an unmodifiable snapshot list of all registered synchronizations
     * for the current thread.
     * @return unmodifiable List of TransactionSynchronization instances
     * @throws IllegalStateException if synchronization is not active
     * @see TransactionSynchronization
     */
    public static List<TransactionSynchronization> getSynchronizations() throws IllegalStateException {
        Set<TransactionSynchronization> synchs = synchronizations.get();
        if (synchs == null) {
            throw new IllegalStateException("Transaction synchronization is not active");
        }
        // Return unmodifiable snapshot, to avoid ConcurrentModificationExceptions
        // while iterating and invoking synchronization callbacks that in turn
        // might register further synchronizations.
        if (synchs.isEmpty()) {
            return Collections.emptyList();
        } else {
            // Sort lazily here, not in registerSynchronization.
            List<TransactionSynchronization> sortedSynchs = new ArrayList<>(synchs);
            OrderComparator.sort(sortedSynchs);
            return Collections.unmodifiableList(sortedSynchs);
        }
    }

    /**
     * Deactivate transaction synchronization for the current thread.
     * Called by the transaction manager on transaction cleanup.
     * @throws IllegalStateException if synchronization is not active
     */
    public static void clearSynchronization() throws IllegalStateException {
        if (!isSynchronizationActive()) {
            throw new IllegalStateException("Cannot deactivate transaction synchronization - not active");
        }
        synchronizations.remove();
    }


    //-------------------------------------------------------------------------
    // Exposure of transaction characteristics
    //-------------------------------------------------------------------------

    /**
     * Expose the name of the current transaction, if any.
     * Called by the transaction manager on transaction begin and on cleanup.
     * @param name the name of the transaction, or {@code null} to reset it
     * @see org.springframework.transaction.TransactionDefinition#getName()
     */
    public static void setCurrentTransactionName(@Nullable String name) {
        currentTransactionName.set(name);
    }

    /**
     * Return the name of the current transaction, or {@code null} if none set.
     * To be called by resource management code for optimizations per use case,
     * for example to optimize fetch strategies for specific named transactions.
     * @see org.springframework.transaction.TransactionDefinition#getName()
     */
    @Nullable
    public static String getCurrentTransactionName() {
        return currentTransactionName.get();
    }

    /**
     * Expose a read-only flag for the current transaction.
     * Called by the transaction manager on transaction begin and on cleanup.
     * @param readOnly {@code true} to mark the current transaction
     * as read-only; {@code false} to reset such a read-only marker
     * @see org.springframework.transaction.TransactionDefinition#isReadOnly()
     */
    public static void setCurrentTransactionReadOnly(boolean readOnly) {
        currentTransactionReadOnly.set(readOnly ? Boolean.TRUE : null);
    }

    /**
     * Return whether the current transaction is marked as read-only.
     * To be called by resource management code when preparing a newly
     * created resource (for example, a Hibernate Session).
     * <p>Note that transaction synchronizations receive the read-only flag
     * as argument for the {@code beforeCommit} callback, to be able
     * to suppress change detection on commit. The present method is meant
     * to be used for earlier read-only checks, for example to set the
     * flush mode of a Hibernate Session to "FlushMode.MANUAL" upfront.
     * @see org.springframework.transaction.TransactionDefinition#isReadOnly()
     * @see TransactionSynchronization#beforeCommit(boolean)
     */
    public static boolean isCurrentTransactionReadOnly() {
        return (currentTransactionReadOnly.get() != null);
    }

    /**
     * Expose an isolation level for the current transaction.
     * Called by the transaction manager on transaction begin and on cleanup.
     * @param isolationLevel the isolation level to expose, according to the
     * JDBC Connection constants (equivalent to the corresponding Spring
     * TransactionDefinition constants), or {@code null} to reset it
     * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
     * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
     * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
     * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_READ_UNCOMMITTED
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_READ_COMMITTED
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_REPEATABLE_READ
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_SERIALIZABLE
     * @see org.springframework.transaction.TransactionDefinition#getIsolationLevel()
     */
    public static void setCurrentTransactionIsolationLevel(@Nullable Integer isolationLevel) {
        currentTransactionIsolationLevel.set(isolationLevel);
    }

    /**
     * Return the isolation level for the current transaction, if any.
     * To be called by resource management code when preparing a newly
     * created resource (for example, a JDBC Connection).
     * @return the currently exposed isolation level, according to the
     * JDBC Connection constants (equivalent to the corresponding Spring
     * TransactionDefinition constants), or {@code null} if none
     * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
     * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
     * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
     * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_READ_UNCOMMITTED
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_READ_COMMITTED
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_REPEATABLE_READ
     * @see org.springframework.transaction.TransactionDefinition#ISOLATION_SERIALIZABLE
     * @see org.springframework.transaction.TransactionDefinition#getIsolationLevel()
     */
    @Nullable
    public static Integer getCurrentTransactionIsolationLevel() {
        return currentTransactionIsolationLevel.get();
    }

    /**
     * Expose whether there currently is an actual transaction active.
     * Called by the transaction manager on transaction begin and on cleanup.
     * @param active {@code true} to mark the current thread as being associated
     * with an actual transaction; {@code false} to reset that marker
     */
    public static void setActualTransactionActive(boolean active) {
        actualTransactionActive.set(active ? Boolean.TRUE : null);
    }

    /**
     * Return whether there currently is an actual transaction active.
     * This indicates whether the current thread is associated with an actual
     * transaction rather than just with active transaction synchronization.
     * <p>To be called by resource management code that wants to discriminate
     * between active transaction synchronization (with or without backing
     * resource transaction; also on PROPAGATION_SUPPORTS) and an actual
     * transaction being active (with backing resource transaction;
     * on PROPAGATION_REQUIRED, PROPAGATION_REQUIRES_NEW, etc).
     * @see #isSynchronizationActive()
     */
    public static boolean isActualTransactionActive() {
        return (actualTransactionActive.get() != null);
    }


    /**
     * Clear the entire transaction synchronization state for the current thread:
     * registered synchronizations as well as the various transaction characteristics.
     * @see #clearSynchronization()
     * @see #setCurrentTransactionName
     * @see #setCurrentTransactionReadOnly
     * @see #setCurrentTransactionIsolationLevel
     * @see #setActualTransactionActive
     */
    public static void clear() {
        synchronizations.remove();
        currentTransactionName.remove();
        currentTransactionReadOnly.remove();
        currentTransactionIsolationLevel.remove();
        actualTransactionActive.remove();
    }

}
