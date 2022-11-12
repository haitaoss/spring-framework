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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Constants;
import org.springframework.lang.Nullable;
import org.springframework.transaction.*;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;

/**
 * Abstract base class that implements Spring's standard transaction workflow,
 * serving as basis for concrete platform transaction managers like
 * {@link org.springframework.transaction.jta.JtaTransactionManager}.
 *
 * <p>This base class provides the following workflow handling:
 * <ul>
 * <li>determines if there is an existing transaction;
 * <li>applies the appropriate propagation behavior;
 * <li>suspends and resumes transactions if necessary;
 * <li>checks the rollback-only flag on commit;
 * <li>applies the appropriate modification on rollback
 * (actual rollback or setting rollback-only);
 * <li>triggers registered synchronization callbacks
 * (if transaction synchronization is active).
 * </ul>
 *
 * <p>Subclasses have to implement specific template methods for specific
 * states of a transaction, e.g.: begin, suspend, resume, commit, rollback.
 * The most important of them are abstract and must be provided by a concrete
 * implementation; for the rest, defaults are provided, so overriding is optional.
 *
 * <p>Transaction synchronization is a generic mechanism for registering callbacks
 * that get invoked at transaction completion time. This is mainly used internally
 * by the data access support classes for JDBC, Hibernate, JPA, etc when running
 * within a JTA transaction: They register resources that are opened within the
 * transaction for closing at transaction completion time, allowing e.g. for reuse
 * of the same Hibernate Session within the transaction. The same mechanism can
 * also be leveraged for custom synchronization needs in an application.
 *
 * <p>The state of this class is serializable, to allow for serializing the
 * transaction strategy along with proxies that carry a transaction interceptor.
 * It is up to subclasses if they wish to make their state to be serializable too.
 * They should implement the {@code java.io.Serializable} marker interface in
 * that case, and potentially a private {@code readObject()} method (according
 * to Java serialization rules) if they need to restore any transient state.
 *
 * @author Juergen Hoeller
 * @since 28.03.2003
 * @see #setTransactionSynchronization
 * @see TransactionSynchronizationManager
 * @see org.springframework.transaction.jta.JtaTransactionManager
 */
@SuppressWarnings("serial")
public abstract class AbstractPlatformTransactionManager implements PlatformTransactionManager, Serializable {

    /**
     * 始终激活事务同步，即使对于“空”事务也是如此
     * Always activate transaction synchronization, even for "empty" transactions
     * that result from PROPAGATION_SUPPORTS with no existing backend transaction.
     * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_SUPPORTS
     * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_NOT_SUPPORTED
     * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_NEVER
     */
    public static final int SYNCHRONIZATION_ALWAYS = 0;

    /**
     * Activate transaction synchronization only for actual transactions,
     * that is, not for empty ones that result from PROPAGATION_SUPPORTS with
     * no existing backend transaction.
     * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRED
     * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_MANDATORY
     * @see org.springframework.transaction.TransactionDefinition#PROPAGATION_REQUIRES_NEW
     */
    public static final int SYNCHRONIZATION_ON_ACTUAL_TRANSACTION = 1;

    /**
     * 永远不要激活事务同步，即使对于实际的事务也不要
     * Never active transaction synchronization, not even for actual transactions.
     */
    public static final int SYNCHRONIZATION_NEVER = 2;


    /** Constants instance for AbstractPlatformTransactionManager. */
    private static final Constants constants = new Constants(AbstractPlatformTransactionManager.class);


    protected transient Log logger = LogFactory.getLog(getClass());

    /**
     * 事务同步，会在准备事务状态的时候，使用这个值
     * {@link AbstractPlatformTransactionManager#handleExistingTransaction(TransactionDefinition, Object, boolean)}
     */
    private int transactionSynchronization = SYNCHRONIZATION_ALWAYS;

    private int defaultTimeout = TransactionDefinition.TIMEOUT_DEFAULT;

    private boolean nestedTransactionAllowed = false;

    private boolean validateExistingTransaction = false;

    private boolean globalRollbackOnParticipationFailure = true;

    private boolean failEarlyOnGlobalRollbackOnly = false;

    private boolean rollbackOnCommitFailure = false;


    /**
     * Set the transaction synchronization by the name of the corresponding constant
     * in this class, e.g. "SYNCHRONIZATION_ALWAYS".
     * @param constantName name of the constant
     * @see #SYNCHRONIZATION_ALWAYS
     */
    public final void setTransactionSynchronizationName(String constantName) {
        setTransactionSynchronization(constants.asNumber(constantName).intValue());
    }

    /**
     * Set when this transaction manager should activate the thread-bound
     * transaction synchronization support. Default is "always".
     * <p>Note that transaction synchronization isn't supported for
     * multiple concurrent transactions by different transaction managers.
     * Only one transaction manager is allowed to activate it at any time.
     * @see #SYNCHRONIZATION_ALWAYS
     * @see #SYNCHRONIZATION_ON_ACTUAL_TRANSACTION
     * @see #SYNCHRONIZATION_NEVER
     * @see TransactionSynchronizationManager
     * @see TransactionSynchronization
     */
    public final void setTransactionSynchronization(int transactionSynchronization) {
        this.transactionSynchronization = transactionSynchronization;
    }

    /**
     * Return if this transaction manager should activate the thread-bound
     * transaction synchronization support.
     */
    public final int getTransactionSynchronization() {
        return this.transactionSynchronization;
    }

    /**
     * Specify the default timeout that this transaction manager should apply
     * if there is no timeout specified at the transaction level, in seconds.
     * <p>Default is the underlying transaction infrastructure's default timeout,
     * e.g. typically 30 seconds in case of a JTA provider, indicated by the
     * {@code TransactionDefinition.TIMEOUT_DEFAULT} value.
     * @see org.springframework.transaction.TransactionDefinition#TIMEOUT_DEFAULT
     */
    public final void setDefaultTimeout(int defaultTimeout) {
        if (defaultTimeout < TransactionDefinition.TIMEOUT_DEFAULT) {
            throw new InvalidTimeoutException("Invalid default timeout", defaultTimeout);
        }
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Return the default timeout that this transaction manager should apply
     * if there is no timeout specified at the transaction level, in seconds.
     * <p>Returns {@code TransactionDefinition.TIMEOUT_DEFAULT} to indicate
     * the underlying transaction infrastructure's default timeout.
     */
    public final int getDefaultTimeout() {
        return this.defaultTimeout;
    }

    /**
     * Set whether nested transactions are allowed. Default is "false".
     * <p>Typically initialized with an appropriate default by the
     * concrete transaction manager subclass.
     */
    public final void setNestedTransactionAllowed(boolean nestedTransactionAllowed) {
        this.nestedTransactionAllowed = nestedTransactionAllowed;
    }

    /**
     * Return whether nested transactions are allowed.
     */
    public final boolean isNestedTransactionAllowed() {
        return this.nestedTransactionAllowed;
    }

    /**
     * Set whether existing transactions should be validated before participating
     * in them.
     * <p>When participating in an existing transaction (e.g. with
     * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
     * transaction), this outer transaction's characteristics will apply even
     * to the inner transaction scope. Validation will detect incompatible
     * isolation level and read-only settings on the inner transaction definition
     * and reject participation accordingly through throwing a corresponding exception.
     * <p>Default is "false", leniently ignoring inner transaction settings,
     * simply overriding them with the outer transaction's characteristics.
     * Switch this flag to "true" in order to enforce strict validation.
     * @since 2.5.1
     */
    public final void setValidateExistingTransaction(boolean validateExistingTransaction) {
        this.validateExistingTransaction = validateExistingTransaction;
    }

    /**
     * Return whether existing transactions should be validated before participating
     * in them.
     * @since 2.5.1
     */
    public final boolean isValidateExistingTransaction() {
        return this.validateExistingTransaction;
    }

    /**
     * Set whether to globally mark an existing transaction as rollback-only
     * after a participating transaction failed.
     * <p>Default is "true": If a participating transaction (e.g. with
     * PROPAGATION_REQUIRED or PROPAGATION_SUPPORTS encountering an existing
     * transaction) fails, the transaction will be globally marked as rollback-only.
     * The only possible outcome of such a transaction is a rollback: The
     * transaction originator <i>cannot</i> make the transaction commit anymore.
     * <p>Switch this to "false" to let the transaction originator make the rollback
     * decision. If a participating transaction fails with an exception, the caller
     * can still decide to continue with a different path within the transaction.
     * However, note that this will only work as long as all participating resources
     * are capable of continuing towards a transaction commit even after a data access
     * failure: This is generally not the case for a Hibernate Session, for example;
     * neither is it for a sequence of JDBC insert/update/delete operations.
     * <p><b>Note:</b>This flag only applies to an explicit rollback attempt for a
     * subtransaction, typically caused by an exception thrown by a data access operation
     * (where TransactionInterceptor will trigger a {@code PlatformTransactionManager.rollback()}
     * call according to a rollback rule). If the flag is off, the caller can handle the exception
     * and decide on a rollback, independent of the rollback rules of the subtransaction.
     * This flag does, however, <i>not</i> apply to explicit {@code setRollbackOnly}
     * calls on a {@code TransactionStatus}, which will always cause an eventual
     * global rollback (as it might not throw an exception after the rollback-only call).
     * <p>The recommended solution for handling failure of a subtransaction
     * is a "nested transaction", where the global transaction can be rolled
     * back to a savepoint taken at the beginning of the subtransaction.
     * PROPAGATION_NESTED provides exactly those semantics; however, it will
     * only work when nested transaction support is available. This is the case
     * with DataSourceTransactionManager, but not with JtaTransactionManager.
     * @see #setNestedTransactionAllowed
     * @see org.springframework.transaction.jta.JtaTransactionManager
     */
    public final void setGlobalRollbackOnParticipationFailure(boolean globalRollbackOnParticipationFailure) {
        this.globalRollbackOnParticipationFailure = globalRollbackOnParticipationFailure;
    }

    /**
     * Return whether to globally mark an existing transaction as rollback-only
     * after a participating transaction failed.
     */
    public final boolean isGlobalRollbackOnParticipationFailure() {
        return this.globalRollbackOnParticipationFailure;
    }

    /**
     * Set whether to fail early in case of the transaction being globally marked
     * as rollback-only.
     * <p>Default is "false", only causing an UnexpectedRollbackException at the
     * outermost transaction boundary. Switch this flag on to cause an
     * UnexpectedRollbackException as early as the global rollback-only marker
     * has been first detected, even from within an inner transaction boundary.
     * <p>Note that, as of Spring 2.0, the fail-early behavior for global
     * rollback-only markers has been unified: All transaction managers will by
     * default only cause UnexpectedRollbackException at the outermost transaction
     * boundary. This allows, for example, to continue unit tests even after an
     * operation failed and the transaction will never be completed. All transaction
     * managers will only fail earlier if this flag has explicitly been set to "true".
     * @since 2.0
     * @see org.springframework.transaction.UnexpectedRollbackException
     */
    public final void setFailEarlyOnGlobalRollbackOnly(boolean failEarlyOnGlobalRollbackOnly) {
        this.failEarlyOnGlobalRollbackOnly = failEarlyOnGlobalRollbackOnly;
    }

    /**
     * Return whether to fail early in case of the transaction being globally marked
     * as rollback-only.
     * @since 2.0
     */
    public final boolean isFailEarlyOnGlobalRollbackOnly() {
        return this.failEarlyOnGlobalRollbackOnly;
    }

    /**
     * Set whether {@code doRollback} should be performed on failure of the
     * {@code doCommit} call. Typically not necessary and thus to be avoided,
     * as it can potentially override the commit exception with a subsequent
     * rollback exception.
     * <p>Default is "false".
     * @see #doCommit
     * @see #doRollback
     */
    public final void setRollbackOnCommitFailure(boolean rollbackOnCommitFailure) {
        this.rollbackOnCommitFailure = rollbackOnCommitFailure;
    }

    /**
     * Return whether {@code doRollback} should be performed on failure of the
     * {@code doCommit} call.
     */
    public final boolean isRollbackOnCommitFailure() {
        return this.rollbackOnCommitFailure;
    }


    //---------------------------------------------------------------------
    // Implementation of PlatformTransactionManager
    //---------------------------------------------------------------------

    /**
     * This implementation handles propagation behavior. Delegates to
     * {@code doGetTransaction}, {@code isExistingTransaction}
     * and {@code doBegin}.
     * @see #doGetTransaction
     * @see #isExistingTransaction
     * @see #doBegin
     */
    @Override
    public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
            throws TransactionException {

        /**
         * definition 就是描述@Transactional注解的对象，
         * definition 是空就给一个默认值
         * */
        // Use defaults if no transaction definition given.
        TransactionDefinition def = (definition != null ? definition : TransactionDefinition.withDefaults());

        /**
         * 其实就是创建事务对象，获取的意思是从 resources 中拿到资源设置给事务对象
         * 这个资源并不一定是开启事务时创建的连接，比如空事务情况下，使用 {@link org.springframework.jdbc.datasource.DataSourceUtils#doGetConnection(DataSource)} 获取连接
         * 这个连接也会存到 resources 中，目的是在事务方法内能重复使用
         *
         * 比如：{@link org.springframework.jdbc.datasource.DataSourceTransactionManager#doGetTransaction()}
         *      会创建这个对象 DataSourceTransactionObject
         *      1. 从ThreadLocal中拿到 Map<DataSource,ConnectionHolder> {@link TransactionSynchronizationManager#resources}
         *      2. DataSource 作为key，从 resources 拿到 ConnectionHolder 设置给事务对象
         *      Tips：创建的是这个对象DataSourceTransactionObject，事务对象说白了就是 一个数据库连接的包装对象
         * */
        Object transaction = doGetTransaction();
        boolean debugEnabled = logger.isDebugEnabled();

        /**
         * 存在事务。就是出现了事务方法嵌套调用的情况
         *
         * 比如这个事务管理器：
         *      {@link org.springframework.jdbc.datasource.DataSourceTransactionManager#isExistingTransaction(Object)}
         *      `txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive()`
         *      就是事务对象有连接 且 连接是活动的事务 才是true
         *
         * 空事务不算存在事务，因为其 isTransactionActive 是false
         * */
        if (isExistingTransaction(transaction)) {
            /**
             * 存在事务的处理，就是根据传播行为看看是：暂停当前事务，新建事务，设置保存点
             * */
            // Existing transaction found -> check propagation behavior to find out how to behave.
            return handleExistingTransaction(def, transaction, debugEnabled);
        }
        // 能往下执行，说明还没有事务
        /**
         * 校验 @Transactional() 设置的值 是否合法
         * */
        // Check definition settings for new transaction.
        if (def.getTimeout() < TransactionDefinition.TIMEOUT_DEFAULT) {
            throw new InvalidTimeoutException("Invalid transaction timeout", def.getTimeout());
        }
        /**
         * 校验事务隔离级别参数，不存在事务就抛出异常
         * */
        // No existing transaction found -> check propagation behavior to find out how to proceed.
        if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_MANDATORY) {
            throw new IllegalTransactionStateException(
                    "No existing transaction found for transaction marked with propagation 'mandatory'");
        } else if (def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED ||
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW ||
                def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
            /**
             * 入参就是要暂停的事务。其实就是从ThreadLocal中取出这两个属性内容，记录到 SuspendedResourcesHolder 中
             *      {@link TransactionSynchronizationManager#synchronizations}
             *      {@link TransactionSynchronizationManager#resources}
             * */
            SuspendedResourcesHolder suspendedResources = suspend(null);
            if (debugEnabled) {
                logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
            }
            try {
                /**
                 * 开启事务（校验事务隔离级别参数，创建新事务）：
                 *  1. 事务对象，没有连接或者连接isSynchronizedWithTransaction 就通过数据源创建连接然后设置给事务对象
                 *  2. 将 @Transactional 的信息设置到连接和事务对象中（自动提交、是否只读、隔离级别、超时时间）
                 *  3. 是新创建的连接，就使用数据源作为key，将连接绑定是事务资源中 {@link TransactionSynchronizationManager#resources}
                 *  4. 设置 ConnectionHolder 属性 isSynchronizedWithTransaction 为true
                 *  5. 记录信息到 TransactionSynchronizationManager
                 * */
                return startTransaction(def, transaction, debugEnabled, suspendedResources);
            } catch (RuntimeException | Error ex) {
                // 出现异常，就恢复之前的事务状态到ThreadLocal中
                resume(null, suspendedResources);
                throw ex;
            }
        } else {
            /**
             * 创建空事务，其实就是没有事务
             * */
            // Create "empty" transaction: no actual transaction, but potentially synchronization.
            if (def.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT && logger.isWarnEnabled()) {
                logger.warn("Custom isolation level specified but no actual transaction initiated; " +
                        "isolation level will effectively be ignored: " + def);
            }
            boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
            return prepareTransactionStatus(def, null, true, newSynchronization, debugEnabled, null);
        }
    }

    /**
     * Start a new transaction.
     */
    private TransactionStatus startTransaction(TransactionDefinition definition, Object transaction,
                                               boolean debugEnabled, @Nullable SuspendedResourcesHolder suspendedResources) {
        /**
         * 不是从不同步。这个值是看你用的啥事务管理，是事务管理器的属性
         * */
        boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
        // new DefaultTransactionStatus 对象
        DefaultTransactionStatus status = newTransactionStatus(
                definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);
        /**
         * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager#doBegin(Object, TransactionDefinition)}
         *      1. 事务对象没有连接或者连接isSynchronizedWithTransaction 就通过数据源创建连接然后设置给事务对
         *      2. 将 @Transactional 的信息设置到连接和事务对象中（设置的内容：自动提交、是否只读、隔离级别、超时时间）
         *      3. 是新创建的连接，就使用数据源作为key，将连接绑定是事务资源中 {@link TransactionSynchronizationManager#resources}
         *      4. 设置 ConnectionHolder 属性 isSynchronizedWithTransaction 为true
         * */
        doBegin(transaction, definition);
        /**
         * status.isNewSynchronization() 就设置 TransactionSynchronizationManager：
         *  - 记录事务隔离级别
         *  - 是否只读
         *  - 事务的name
         *  - {@link TransactionSynchronizationManager#synchronizations} 属性初始化，会根据这个属性是否为null判断 是不是 isNewSynchronization
         * */
        prepareSynchronization(status, definition);
        return status;
    }

    /**
     * 存在的事务创建事务状态(主要是根据事务传播行为来判断)
     * Create a TransactionStatus for an existing transaction.
     */
    private TransactionStatus handleExistingTransaction(
            TransactionDefinition definition, Object transaction, boolean debugEnabled)
            throws TransactionException {
        /**
         * 传播行为：存在事务就报错
         * */
        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NEVER) {
            throw new IllegalTransactionStateException(
                    "Existing transaction found for transaction marked with propagation 'never'");
        }
        /**
         * 传播行为：存在事务就挂起
         * */
        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NOT_SUPPORTED) {
            if (debugEnabled) {
                logger.debug("Suspending current transaction");
            }
            /**
             * 暂停事务。返回 SuspendedResourcesHolder，这里面记录了被移除的资源，和所暂停事务的状态信息
             *
             * 就是从 {@link TransactionSynchronizationManager#resources} 移除资源，移除的资源会存到 SuspendedResourcesHolder 中
             * */
            Object suspendedResources = suspend(transaction);
            boolean newSynchronization = (getTransactionSynchronization() == SYNCHRONIZATION_ALWAYS);
            // 返回 事务状态
            return prepareTransactionStatus(
                    definition, null, false, newSynchronization, debugEnabled, suspendedResources);
        }

        /**
         * 传播行为：创建新的事物，已经存在事物就挂起
         * */
        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW) {
            if (debugEnabled) {
                logger.debug("Suspending current transaction, creating new transaction with name [" +
                        definition.getName() + "]");
            }
            /**
             * 暂停事务，返回的是暂停事务的资源
             * */
            SuspendedResourcesHolder suspendedResources = suspend(transaction);
            try {
                /**
                 * 开启新事物，会记录暂停事务的信息
                 * */
                return startTransaction(definition, transaction, debugEnabled, suspendedResources);
            } catch (RuntimeException | Error beginEx) {
                resumeAfterBeginException(transaction, suspendedResources, beginEx);
                throw beginEx;
            }
        }

        /**
         * 传播行为：如果当前事务存在就创建嵌套事务，否则就像 PROPAGATION_REQUIRED 一样处理
         *
         * 通过这里能体现，没有事务的时候就和PROPAGATION_REQUIRED一样 创建新的事务 {@link AbstractPlatformTransactionManager#getTransaction(TransactionDefinition) }
         * */
        if (definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_NESTED) {
            /**
             * 不支持嵌套事务 就报错，{@link org.springframework.jdbc.datasource.DataSourceTransactionManager#DataSourceTransactionManager()} 是支持的
             * */
            if (!isNestedTransactionAllowed()) {
                throw new NestedTransactionNotSupportedException(
                        "Transaction manager does not allow nested transactions by default - " +
                                "specify 'nestedTransactionAllowed' property with value 'true'");
            }
            if (debugEnabled) {
                logger.debug("Creating nested transaction with name [" + definition.getName() + "]");
            }
            /**
             * 使用保存点来实现 嵌套事务
             * DataSourceTransactionManager 就是true
             * */
            if (useSavepointForNestedTransaction()) {
                // Create savepoint within existing Spring-managed transaction,
                // through the SavepointManager API implemented by TransactionStatus.
                // Usually uses JDBC 3.0 savepoints. Never activates Spring synchronization.
                DefaultTransactionStatus status =
                        prepareTransactionStatus(definition, transaction, false, false, debugEnabled, null);
                // 执行sql创建保存点
                status.createAndHoldSavepoint();
                return status;
            } else {
                // Nested transaction through nested begin and commit/rollback calls.
                // Usually only for JTA: Spring synchronization might get activated here
                // in case of a pre-existing JTA transaction.
                return startTransaction(definition, transaction, debugEnabled, null);
            }
        }
        // Assumably PROPAGATION_SUPPORTS or PROPAGATION_REQUIRED.
        if (debugEnabled) {
            logger.debug("Participating in existing transaction");
        }
        if (isValidateExistingTransaction()) {
            if (definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) {
                Integer currentIsolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                if (currentIsolationLevel == null || currentIsolationLevel != definition.getIsolationLevel()) {
                    Constants isoConstants = DefaultTransactionDefinition.constants;
                    throw new IllegalTransactionStateException("Participating transaction with definition [" +
                            definition
                            + "] specifies isolation level which is incompatible with existing transaction: "
                            +
                            (currentIsolationLevel != null ?
                                    isoConstants.toCode(currentIsolationLevel, DefaultTransactionDefinition.PREFIX_ISOLATION) :
                                    "(unknown)"));
                }
            }
            if (!definition.isReadOnly()) {
                if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                    throw new IllegalTransactionStateException("Participating transaction with definition [" +
                            definition
                            + "] is not marked as read-only but existing transaction is");
                }
            }
        }
        boolean newSynchronization = (getTransactionSynchronization() != SYNCHRONIZATION_NEVER);
        return prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, null);
    }

    /**
     * Create a new TransactionStatus for the given arguments,
     * also initializing transaction synchronization as appropriate.
     * @see #newTransactionStatus
     * @see #prepareTransactionStatus
     */
    protected final DefaultTransactionStatus prepareTransactionStatus(
            TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
            boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {

        DefaultTransactionStatus status = newTransactionStatus(
                definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
        prepareSynchronization(status, definition);
        return status;
    }

    /**
     * Create a TransactionStatus instance for the given arguments.
     */
    protected DefaultTransactionStatus newTransactionStatus(
            TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction,
            boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {

        /**
         * newSynchronization 且 不是同步激活状态  就是 真的新同步
         *
         * 同步激活状态：
         *      - true：简单来说就是Java虚拟机栈中存在@Transactional的方法
         *      - false：暂停当前事务、完成当前事务(rollback或者commit)
         *
         * 结论：当前线程在 TransactionSynchronizationManager 没有记录信息， actualNewSynchronization 就是true
         * */
        boolean actualNewSynchronization = newSynchronization &&
                !TransactionSynchronizationManager.isSynchronizationActive();
        return new DefaultTransactionStatus(
                transaction, newTransaction, actualNewSynchronization,
                definition.isReadOnly(), debug, suspendedResources);
    }

    /**
     * 适当地初始化事务同步。
     * Initialize transaction synchronization as appropriate.
     */
    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
        /**
         * 是新的同步，才记录这些信息（注：空事务也算的）
         * */
        if (status.isNewSynchronization()) {
            /**
             * 真的活动的事务。就是得有事务，且事务是新的才是true
             * */
            TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
            // 记录事务隔离级别
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(
                    definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT ?
                            definition.getIsolationLevel() : null);
            // 是否只读
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
            // 事务的name
            TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
            // 同步属性初始化，会根据这个属性是否为null判断 是不是 isNewSynchronization
            TransactionSynchronizationManager.initSynchronization();
        }
    }

    /**
     * Determine the actual timeout to use for the given definition.
     * Will fall back to this manager's default timeout if the
     * transaction definition doesn't specify a non-default value.
     * @param definition the transaction definition
     * @return the actual timeout to use
     * @see org.springframework.transaction.TransactionDefinition#getTimeout()
     * @see #setDefaultTimeout
     */
    protected int determineTimeout(TransactionDefinition definition) {
        if (definition.getTimeout() != TransactionDefinition.TIMEOUT_DEFAULT) {
            return definition.getTimeout();
        }
        return getDefaultTimeout();
    }


    /**
     * 暂停事务。返回 SuspendedResourcesHolder，这里面记录了被移除的资源，和所暂停事务的状态信息
     * Suspend the given transaction. Suspends transaction synchronization first,
     * then delegates to the {@code doSuspend} template method.
     * @param transaction the current transaction object
     * (or {@code null} to just suspend active synchronizations, if any)
     * @return an object that holds suspended resources
     * (or {@code null} if neither transaction nor synchronization active)
     * @see #doSuspend
     * @see #resume
     */
    @Nullable
    protected final SuspendedResourcesHolder suspend(@Nullable Object transaction) throws TransactionException {
        /**
         * 是活动的事务。
         * 可以这里理解，只要Java虚拟机栈中存在@Transactional的方法，这个判断就是true
         *      1. 暂停当前事务
         *      2. 完成当前事务(rollback或者commit)
         * */
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            /**
             * 暂停事对象的同步资源。暂停了 TransactionSynchronizationManager.isSynchronizationActive() 就是 false了
             *
             * 就是拿到 {@link TransactionSynchronizationManager#synchronizations}，遍历执行 {@link TransactionSynchronization#suspend()}
             * */
            List<TransactionSynchronization> suspendedSynchronizations = doSuspendSynchronization();
            try {
                Object suspendedResources = null;
                // 存在事务对象
                if (transaction != null) {
                    /**
                     * 暂停事务对象资源。
                     * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager#doSuspend(Object)}
                     *      1. 取消事务对象绑定的连接信息 txObject.setConnectionHolder(null);
                     *      2. 从事务资源中移除事务对象对应的资源(就是移除DataSource作为key的资源) {@link TransactionSynchronizationManager#unbindResource(Object)}
                     *
                     *      Tips：返回值，就是事务资源中移除的资源
                     * */
                    suspendedResources = doSuspend(transaction);
                }
                // 拿到原来的值
                String name = TransactionSynchronizationManager.getCurrentTransactionName();
                TransactionSynchronizationManager.setCurrentTransactionName(null);
                boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
                boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive();
                TransactionSynchronizationManager.setActualTransactionActive(false);
                /**
                 * 装饰一下，就是记录现在得值，后面恢复事务需要重新设置到 TransactionSynchronizationManager
                 * */
                return new SuspendedResourcesHolder(
                        suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive);
            } catch (RuntimeException | Error ex) {
                // doSuspend failed - original transaction is still active...
                doResumeSynchronization(suspendedSynchronizations);
                throw ex;
            }
        } else if (transaction != null) {
            /**
             *  只需要记录被暂停的事物资源是啥就足够了
             * */
            // Transaction active but no synchronization active.
            Object suspendedResources = doSuspend(transaction);
            return new SuspendedResourcesHolder(suspendedResources);
        } else {
            // Neither transaction nor synchronization active.
            return null;
        }
    }

    /**
     * Resume the given transaction. Delegates to the {@code doResume}
     * template method first, then resuming transaction synchronization.
     * @param transaction the current transaction object
     * @param resourcesHolder the object that holds suspended resources,
     * as returned by {@code suspend} (or {@code null} to just
     * resume synchronizations, if any)
     * @see #doResume
     * @see #suspend
     */
    protected final void resume(@Nullable Object transaction, @Nullable SuspendedResourcesHolder resourcesHolder)
            throws TransactionException {

        if (resourcesHolder != null) {
            // 比如数据库连接
            Object suspendedResources = resourcesHolder.suspendedResources;
            if (suspendedResources != null) {
                /**
                 * 恢复事务资源。
                 * 就是重新设置回事务资源ThreadLocal中 {@link TransactionSynchronizationManager#resources}
                 * */
                doResume(transaction, suspendedResources);
            }
            List<TransactionSynchronization> suspendedSynchronizations = resourcesHolder.suspendedSynchronizations;
            if (suspendedSynchronizations != null) {
                TransactionSynchronizationManager.setActualTransactionActive(resourcesHolder.wasActive);
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(resourcesHolder.isolationLevel);
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(resourcesHolder.readOnly);
                TransactionSynchronizationManager.setCurrentTransactionName(resourcesHolder.name);
                // 恢复事务同步资源
                doResumeSynchronization(suspendedSynchronizations);
            }
        }
    }

    /**
     * Resume outer transaction after inner transaction begin failed.
     */
    private void resumeAfterBeginException(
            Object transaction, @Nullable SuspendedResourcesHolder suspendedResources, Throwable beginEx) {

        try {
            resume(transaction, suspendedResources);
        } catch (RuntimeException | Error resumeEx) {
            String exMessage = "Inner transaction begin exception overridden by outer transaction resume exception";
            logger.error(exMessage, beginEx);
            throw resumeEx;
        }
    }

    /**
     * Suspend all current synchronizations and deactivate transaction
     * synchronization for the current thread.
     * @return the List of suspended TransactionSynchronization objects
     */
    private List<TransactionSynchronization> doSuspendSynchronization() {
        // 拿到事务同步资源
        List<TransactionSynchronization> suspendedSynchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        // 遍历，然后挂起
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            /**
             * 对于数据库连接：
             *      {@link org.springframework.jdbc.datasource.DataSourceUtils.ConnectionSynchronization#suspend()}
             *       如果该Connection存在事务资源中，就取消事务资源对该Connection的引用;
             *       否则就关闭连接
             * */
            synchronization.suspend();
        }
        // 清空线程事务同步资源
        TransactionSynchronizationManager.clearSynchronization();
        // 返回 事务同步资源
        return suspendedSynchronizations;
    }

    /**
     * Reactivate transaction synchronization for the current thread
     * and resume all given synchronizations.
     * @param suspendedSynchronizations a List of TransactionSynchronization objects
     */
    private void doResumeSynchronization(List<TransactionSynchronization> suspendedSynchronizations) {
        TransactionSynchronizationManager.initSynchronization();
        for (TransactionSynchronization synchronization : suspendedSynchronizations) {
            /**
             * {@link org.springframework.jdbc.datasource.DataSourceUtils.ConnectionSynchronization#resume()}
             * 就是将其Connection重新设置到 {@link TransactionSynchronizationManager#resources} 中
             * */
            synchronization.resume();
            /**
             * 重新注册回 {@link TransactionSynchronizationManager#synchronizations}
             * */
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }
    }


    /**
     * This implementation of commit handles participating in existing
     * transactions and programmatic rollback requests.
     * Delegates to {@code isRollbackOnly}, {@code doCommit}
     * and {@code rollback}.
     * @see org.springframework.transaction.TransactionStatus#isRollbackOnly()
     * @see #doCommit
     * @see #rollback
     */
    @Override
    public final void commit(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException(
                    "Transaction is already completed - do not call commit or rollback more than once per transaction");
        }

        DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
        if (defStatus.isLocalRollbackOnly()) {
            if (defStatus.isDebug()) {
                logger.debug("Transactional code has requested rollback");
            }
            // 回滚
            processRollback(defStatus, false);
            return;
        }

        if (!shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) {
            if (defStatus.isDebug()) {
                logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
            }
            // 回滚
            processRollback(defStatus, true);
            return;
        }

        // commit
        processCommit(defStatus);
    }

    /**
     * Process an actual commit.
     * Rollback-only flags have already been checked and applied.
     * @param status object representing the transaction
     * @throws TransactionException in case of commit failure
     */
    private void processCommit(DefaultTransactionStatus status) throws TransactionException {
        try {
            boolean beforeCompletionInvoked = false;

            try {
                boolean unexpectedRollback = false;
                prepareForCommit(status);
                // 回调 TransactionSynchronization
                triggerBeforeCommit(status);
                // 回调 TransactionSynchronization
                triggerBeforeCompletion(status);
                beforeCompletionInvoked = true;

                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        logger.debug("Releasing transaction savepoint");
                    }
                    unexpectedRollback = status.isGlobalRollbackOnly();
                    status.releaseHeldSavepoint();
                } else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        logger.debug("Initiating transaction commit");
                    }
                    unexpectedRollback = status.isGlobalRollbackOnly();
                    // 提交事务
                    doCommit(status);
                } else if (isFailEarlyOnGlobalRollbackOnly()) {
                    unexpectedRollback = status.isGlobalRollbackOnly();
                }

                // Throw UnexpectedRollbackException if we have a global rollback-only
                // marker but still didn't get a corresponding exception from commit.
                if (unexpectedRollback) {
                    throw new UnexpectedRollbackException(
                            "Transaction silently rolled back because it has been marked as rollback-only");
                }
            } catch (UnexpectedRollbackException ex) {
                // can only be caused by doCommit
                triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
                throw ex;
            } catch (TransactionException ex) {
                // can only be caused by doCommit
                if (isRollbackOnCommitFailure()) {
                    doRollbackOnCommitException(status, ex);
                } else {
                    triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
                }
                throw ex;
            } catch (RuntimeException | Error ex) {
                if (!beforeCompletionInvoked) {
                    triggerBeforeCompletion(status);
                }
                doRollbackOnCommitException(status, ex);
                throw ex;
            }

            // Trigger afterCommit callbacks, with an exception thrown there
            // propagated to callers but the transaction still considered as committed.
            try {
                // 回调 TransactionSynchronization
                triggerAfterCommit(status);
            } finally {
                // 回调 TransactionSynchronization
                triggerAfterCompletion(status, TransactionSynchronization.STATUS_COMMITTED);
            }

        } finally {
            // 就是恢复暂停的事务资源
            cleanupAfterCompletion(status);
        }
    }

    /**
     * This implementation of rollback handles participating in existing
     * transactions. Delegates to {@code doRollback} and
     * {@code doSetRollbackOnly}.
     * @see #doRollback
     * @see #doSetRollbackOnly
     */
    @Override
    public final void rollback(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException(
                    "Transaction is already completed - do not call commit or rollback more than once per transaction");
        }

        DefaultTransactionStatus defStatus = (DefaultTransactionStatus) status;
        processRollback(defStatus, false);
    }

    /**
     * Process an actual rollback.
     * The completed flag has already been checked.
     * @param status object representing the transaction
     * @throws TransactionException in case of rollback failure
     */
    private void processRollback(DefaultTransactionStatus status, boolean unexpected) {
        try {
            boolean unexpectedRollback = unexpected;

            try {
                /**
                 * 回调 TransactionSynchronization
                 * 就是遍历 {@link TransactionSynchronizationManager#synchronizations} 属性，取消绑定资源
                 * */
                triggerBeforeCompletion(status);

                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        logger.debug("Rolling back transaction to savepoint");
                    }
                    /**
                     * 回滚到最前的保存点
                     * */
                    status.rollbackToHeldSavepoint();
                } else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        logger.debug("Initiating transaction rollback");
                    }
                    /**
                     * 回滚
                     * {@link org.springframework.jdbc.datasource.DataSourceTransactionManager#doRollback(DefaultTransactionStatus)}
                     * */
                    doRollback(status);
                } else {
                    // Participating in larger transaction
                    if (status.hasTransaction()) {
                        if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure()) {
                            if (status.isDebug()) {
                                logger.debug("Participating transaction failed - marking existing transaction as rollback-only");
                            }
                            doSetRollbackOnly(status);
                        } else {
                            if (status.isDebug()) {
                                logger.debug("Participating transaction failed - letting transaction originator decide on rollback");
                            }
                        }
                    } else {
                        logger.debug("Should roll back transaction but cannot - no transaction available");
                    }
                    // Unexpected rollback only matters here if we're asked to fail early
                    if (!isFailEarlyOnGlobalRollbackOnly()) {
                        unexpectedRollback = false;
                    }
                }
            } catch (RuntimeException | Error ex) {
                // 回调 TransactionSynchronization
                triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
                throw ex;
            }

            // 回调 TransactionSynchronization
            triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);

            // Raise UnexpectedRollbackException if we had a global rollback-only marker
            if (unexpectedRollback) {
                throw new UnexpectedRollbackException(
                        "Transaction rolled back because it has been marked as rollback-only");
            }
        } finally {
            // 恢复上个事务的资源
            cleanupAfterCompletion(status);
        }
    }

    /**
     * Invoke {@code doRollback}, handling rollback exceptions properly.
     * @param status object representing the transaction
     * @param ex the thrown application exception or error
     * @throws TransactionException in case of rollback failure
     * @see #doRollback
     */
    private void doRollbackOnCommitException(DefaultTransactionStatus status, Throwable ex) throws TransactionException {
        try {
            if (status.isNewTransaction()) {
                if (status.isDebug()) {
                    logger.debug("Initiating transaction rollback after commit exception", ex);
                }
                doRollback(status);
            } else if (status.hasTransaction() && isGlobalRollbackOnParticipationFailure()) {
                if (status.isDebug()) {
                    logger.debug("Marking existing transaction as rollback-only after commit exception", ex);
                }
                doSetRollbackOnly(status);
            }
        } catch (RuntimeException | Error rbex) {
            logger.error("Commit exception overridden by rollback exception", ex);
            triggerAfterCompletion(status, TransactionSynchronization.STATUS_UNKNOWN);
            throw rbex;
        }
        triggerAfterCompletion(status, TransactionSynchronization.STATUS_ROLLED_BACK);
    }


    /**
     * Trigger {@code beforeCommit} callbacks.
     * @param status object representing the transaction
     */
    protected final void triggerBeforeCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationUtils.triggerBeforeCommit(status.isReadOnly());
        }
    }

    /**
     * Trigger {@code beforeCompletion} callbacks.
     * @param status object representing the transaction
     */
    protected final void triggerBeforeCompletion(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationUtils.triggerBeforeCompletion();
        }
    }

    /**
     * Trigger {@code afterCommit} callbacks.
     * @param status object representing the transaction
     */
    private void triggerAfterCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationUtils.triggerAfterCommit();
        }
    }

    /**
     * Trigger {@code afterCompletion} callbacks.
     * @param status object representing the transaction
     * @param completionStatus completion status according to TransactionSynchronization constants
     */
    private void triggerAfterCompletion(DefaultTransactionStatus status, int completionStatus) {
        if (status.isNewSynchronization()) {
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            if (!status.hasTransaction() || status.isNewTransaction()) {
                // No transaction or new transaction for the current scope ->
                // invoke the afterCompletion callbacks immediately
                invokeAfterCompletion(synchronizations, completionStatus);
            } else if (!synchronizations.isEmpty()) {
                // Existing transaction that we participate in, controlled outside
                // of the scope of this Spring transaction manager -> try to register
                // an afterCompletion callback with the existing (JTA) transaction.
                registerAfterCompletionWithExistingTransaction(status.getTransaction(), synchronizations);
            }
        }
    }

    /**
     * Actually invoke the {@code afterCompletion} methods of the
     * given Spring TransactionSynchronization objects.
     * <p>To be called by this abstract manager itself, or by special implementations
     * of the {@code registerAfterCompletionWithExistingTransaction} callback.
     * @param synchronizations a List of TransactionSynchronization objects
     * @param completionStatus the completion status according to the
     * constants in the TransactionSynchronization interface
     * @see #registerAfterCompletionWithExistingTransaction(Object, java.util.List)
     * @see TransactionSynchronization#STATUS_COMMITTED
     * @see TransactionSynchronization#STATUS_ROLLED_BACK
     * @see TransactionSynchronization#STATUS_UNKNOWN
     */
    protected final void invokeAfterCompletion(List<TransactionSynchronization> synchronizations, int completionStatus) {
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, completionStatus);
    }

    /**
     * Clean up after completion, clearing synchronization if necessary,
     * and invoking doCleanupAfterCompletion.
     * @param status object representing the transaction
     * @see #doCleanupAfterCompletion
     */
    private void cleanupAfterCompletion(DefaultTransactionStatus status) {
        status.setCompleted();
        if (status.isNewSynchronization()) {
            // 清空所有同步信息
            TransactionSynchronizationManager.clear();
        }
        if (status.isNewTransaction()) {
            /**
             * 就是从 {@link TransactionSynchronizationManager#resources} 中移除资源，并恢复事务对象的Connection原来的属性值
             * */
            doCleanupAfterCompletion(status.getTransaction());
        }
        if (status.getSuspendedResources() != null) {
            if (status.isDebug()) {
                logger.debug("Resuming suspended transaction after completion of inner transaction");
            }
            Object transaction = (status.hasTransaction() ? status.getTransaction() : null);
            /**
             * 恢复暂停的资源到 TransactionSynchronizationManager 中
             * */
            resume(transaction, (SuspendedResourcesHolder) status.getSuspendedResources());
        }
    }


    //---------------------------------------------------------------------
    // Template methods to be implemented in subclasses
    //---------------------------------------------------------------------

    /**
     * Return a transaction object for the current transaction state.
     * <p>The returned object will usually be specific to the concrete transaction
     * manager implementation, carrying corresponding transaction state in a
     * modifiable fashion. This object will be passed into the other template
     * methods (e.g. doBegin and doCommit), either directly or as part of a
     * DefaultTransactionStatus instance.
     * <p>The returned object should contain information about any existing
     * transaction, that is, a transaction that has already started before the
     * current {@code getTransaction} call on the transaction manager.
     * Consequently, a {@code doGetTransaction} implementation will usually
     * look for an existing transaction and store corresponding state in the
     * returned transaction object.
     * @return the current transaction object
     * @throws org.springframework.transaction.CannotCreateTransactionException
     * if transaction support is not available
     * @throws TransactionException in case of lookup or system errors
     * @see #doBegin
     * @see #doCommit
     * @see #doRollback
     * @see DefaultTransactionStatus#getTransaction
     */
    protected abstract Object doGetTransaction() throws TransactionException;

    /**
     * Check if the given transaction object indicates an existing transaction
     * (that is, a transaction which has already started).
     * <p>The result will be evaluated according to the specified propagation
     * behavior for the new transaction. An existing transaction might get
     * suspended (in case of PROPAGATION_REQUIRES_NEW), or the new transaction
     * might participate in the existing one (in case of PROPAGATION_REQUIRED).
     * <p>The default implementation returns {@code false}, assuming that
     * participating in existing transactions is generally not supported.
     * Subclasses are of course encouraged to provide such support.
     * @param transaction the transaction object returned by doGetTransaction
     * @return if there is an existing transaction
     * @throws TransactionException in case of system errors
     * @see #doGetTransaction
     */
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        return false;
    }

    /**
     * Return whether to use a savepoint for a nested transaction.
     * <p>Default is {@code true}, which causes delegation to DefaultTransactionStatus
     * for creating and holding a savepoint. If the transaction object does not implement
     * the SavepointManager interface, a NestedTransactionNotSupportedException will be
     * thrown. Else, the SavepointManager will be asked to create a new savepoint to
     * demarcate the start of the nested transaction.
     * <p>Subclasses can override this to return {@code false}, causing a further
     * call to {@code doBegin} - within the context of an already existing transaction.
     * The {@code doBegin} implementation needs to handle this accordingly in such
     * a scenario. This is appropriate for JTA, for example.
     * @see DefaultTransactionStatus#createAndHoldSavepoint
     * @see DefaultTransactionStatus#rollbackToHeldSavepoint
     * @see DefaultTransactionStatus#releaseHeldSavepoint
     * @see #doBegin
     */
    protected boolean useSavepointForNestedTransaction() {
        return true;
    }

    /**
     * Begin a new transaction with semantics according to the given transaction
     * definition. Does not have to care about applying the propagation behavior,
     * as this has already been handled by this abstract manager.
     * <p>This method gets called when the transaction manager has decided to actually
     * start a new transaction. Either there wasn't any transaction before, or the
     * previous transaction has been suspended.
     * <p>A special scenario is a nested transaction without savepoint: If
     * {@code useSavepointForNestedTransaction()} returns "false", this method
     * will be called to start a nested transaction when necessary. In such a context,
     * there will be an active transaction: The implementation of this method has
     * to detect this and start an appropriate nested transaction.
     * @param transaction the transaction object returned by {@code doGetTransaction}
     * @param definition a TransactionDefinition instance, describing propagation
     * behavior, isolation level, read-only flag, timeout, and transaction name
     * @throws TransactionException in case of creation or system errors
     * @throws org.springframework.transaction.NestedTransactionNotSupportedException
     * if the underlying transaction does not support nesting
     */
    protected abstract void doBegin(Object transaction, TransactionDefinition definition)
            throws TransactionException;

    /**
     * Suspend the resources of the current transaction.
     * Transaction synchronization will already have been suspended.
     * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
     * assuming that transaction suspension is generally not supported.
     * @param transaction the transaction object returned by {@code doGetTransaction}
     * @return an object that holds suspended resources
     * (will be kept unexamined for passing it into doResume)
     * @throws org.springframework.transaction.TransactionSuspensionNotSupportedException
     * if suspending is not supported by the transaction manager implementation
     * @throws TransactionException in case of system errors
     * @see #doResume
     */
    protected Object doSuspend(Object transaction) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException(
                "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    /**
     * Resume the resources of the current transaction.
     * Transaction synchronization will be resumed afterwards.
     * <p>The default implementation throws a TransactionSuspensionNotSupportedException,
     * assuming that transaction suspension is generally not supported.
     * @param transaction the transaction object returned by {@code doGetTransaction}
     * @param suspendedResources the object that holds suspended resources,
     * as returned by doSuspend
     * @throws org.springframework.transaction.TransactionSuspensionNotSupportedException
     * if resuming is not supported by the transaction manager implementation
     * @throws TransactionException in case of system errors
     * @see #doSuspend
     */
    protected void doResume(@Nullable Object transaction, Object suspendedResources) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException(
                "Transaction manager [" + getClass().getName() + "] does not support transaction suspension");
    }

    /**
     * Return whether to call {@code doCommit} on a transaction that has been
     * marked as rollback-only in a global fashion.
     * <p>Does not apply if an application locally sets the transaction to rollback-only
     * via the TransactionStatus, but only to the transaction itself being marked as
     * rollback-only by the transaction coordinator.
     * <p>Default is "false": Local transaction strategies usually don't hold the rollback-only
     * marker in the transaction itself, therefore they can't handle rollback-only transactions
     * as part of transaction commit. Hence, AbstractPlatformTransactionManager will trigger
     * a rollback in that case, throwing an UnexpectedRollbackException afterwards.
     * <p>Override this to return "true" if the concrete transaction manager expects a
     * {@code doCommit} call even for a rollback-only transaction, allowing for
     * special handling there. This will, for example, be the case for JTA, where
     * {@code UserTransaction.commit} will check the read-only flag itself and
     * throw a corresponding RollbackException, which might include the specific reason
     * (such as a transaction timeout).
     * <p>If this method returns "true" but the {@code doCommit} implementation does not
     * throw an exception, this transaction manager will throw an UnexpectedRollbackException
     * itself. This should not be the typical case; it is mainly checked to cover misbehaving
     * JTA providers that silently roll back even when the rollback has not been requested
     * by the calling code.
     * @see #doCommit
     * @see DefaultTransactionStatus#isGlobalRollbackOnly()
     * @see DefaultTransactionStatus#isLocalRollbackOnly()
     * @see org.springframework.transaction.TransactionStatus#setRollbackOnly()
     * @see org.springframework.transaction.UnexpectedRollbackException
     * @see javax.transaction.UserTransaction#commit()
     * @see javax.transaction.RollbackException
     */
    protected boolean shouldCommitOnGlobalRollbackOnly() {
        return false;
    }

    /**
     * Make preparations for commit, to be performed before the
     * {@code beforeCommit} synchronization callbacks occur.
     * <p>Note that exceptions will get propagated to the commit caller
     * and cause a rollback of the transaction.
     * @param status the status representation of the transaction
     * @throws RuntimeException in case of errors; will be <b>propagated to the caller</b>
     * (note: do not throw TransactionException subclasses here!)
     */
    protected void prepareForCommit(DefaultTransactionStatus status) {
    }

    /**
     * Perform an actual commit of the given transaction.
     * <p>An implementation does not need to check the "new transaction" flag
     * or the rollback-only flag; this will already have been handled before.
     * Usually, a straight commit will be performed on the transaction object
     * contained in the passed-in status.
     * @param status the status representation of the transaction
     * @throws TransactionException in case of commit or system errors
     * @see DefaultTransactionStatus#getTransaction
     */
    protected abstract void doCommit(DefaultTransactionStatus status) throws TransactionException;

    /**
     * Perform an actual rollback of the given transaction.
     * <p>An implementation does not need to check the "new transaction" flag;
     * this will already have been handled before. Usually, a straight rollback
     * will be performed on the transaction object contained in the passed-in status.
     * @param status the status representation of the transaction
     * @throws TransactionException in case of system errors
     * @see DefaultTransactionStatus#getTransaction
     */
    protected abstract void doRollback(DefaultTransactionStatus status) throws TransactionException;

    /**
     * Set the given transaction rollback-only. Only called on rollback
     * if the current transaction participates in an existing one.
     * <p>The default implementation throws an IllegalTransactionStateException,
     * assuming that participating in existing transactions is generally not
     * supported. Subclasses are of course encouraged to provide such support.
     * @param status the status representation of the transaction
     * @throws TransactionException in case of system errors
     */
    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        throw new IllegalTransactionStateException(
                "Participating in existing transactions is not supported - when 'isExistingTransaction' " +
                        "returns true, appropriate 'doSetRollbackOnly' behavior must be provided");
    }

    /**
     * Register the given list of transaction synchronizations with the existing transaction.
     * <p>Invoked when the control of the Spring transaction manager and thus all Spring
     * transaction synchronizations end, without the transaction being completed yet. This
     * is for example the case when participating in an existing JTA or EJB CMT transaction.
     * <p>The default implementation simply invokes the {@code afterCompletion} methods
     * immediately, passing in "STATUS_UNKNOWN". This is the best we can do if there's no
     * chance to determine the actual outcome of the outer transaction.
     * @param transaction the transaction object returned by {@code doGetTransaction}
     * @param synchronizations a List of TransactionSynchronization objects
     * @throws TransactionException in case of system errors
     * @see #invokeAfterCompletion(java.util.List, int)
     * @see TransactionSynchronization#afterCompletion(int)
     * @see TransactionSynchronization#STATUS_UNKNOWN
     */
    protected void registerAfterCompletionWithExistingTransaction(
            Object transaction, List<TransactionSynchronization> synchronizations) throws TransactionException {

        logger.debug("Cannot register Spring after-completion synchronization with existing transaction - " +
                "processing Spring after-completion callbacks immediately, with outcome status 'unknown'");
        invokeAfterCompletion(synchronizations, TransactionSynchronization.STATUS_UNKNOWN);
    }

    /**
     * Cleanup resources after transaction completion.
     * <p>Called after {@code doCommit} and {@code doRollback} execution,
     * on any outcome. The default implementation does nothing.
     * <p>Should not throw any exceptions but just issue warnings on errors.
     * @param transaction the transaction object returned by {@code doGetTransaction}
     */
    protected void doCleanupAfterCompletion(Object transaction) {
    }


    //---------------------------------------------------------------------
    // Serialization support
    //---------------------------------------------------------------------

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        // Rely on default serialization; just initialize state after deserialization.
        ois.defaultReadObject();

        // Initialize transient fields.
        this.logger = LogFactory.getLog(getClass());
    }


    /**
     * Holder for suspended resources.
     * Used internally by {@code suspend} and {@code resume}.
     */
    protected static final class SuspendedResourcesHolder {

        @Nullable
        private final Object suspendedResources;

        @Nullable
        private List<TransactionSynchronization> suspendedSynchronizations;

        @Nullable
        private String name;

        private boolean readOnly;

        @Nullable
        private Integer isolationLevel;

        private boolean wasActive;

        private SuspendedResourcesHolder(Object suspendedResources) {
            this.suspendedResources = suspendedResources;
        }

        private SuspendedResourcesHolder(
                @Nullable Object suspendedResources, List<TransactionSynchronization> suspendedSynchronizations,
                @Nullable String name, boolean readOnly, @Nullable Integer isolationLevel, boolean wasActive) {

            this.suspendedResources = suspendedResources;
            this.suspendedSynchronizations = suspendedSynchronizations;
            this.name = name;
            this.readOnly = readOnly;
            this.isolationLevel = isolationLevel;
            this.wasActive = wasActive;
        }
    }

}
