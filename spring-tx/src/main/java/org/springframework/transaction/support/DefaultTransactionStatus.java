/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.SavepointManager;
import org.springframework.util.Assert;

/**
 * Default implementation of the {@link org.springframework.transaction.TransactionStatus}
 * interface, used by {@link AbstractPlatformTransactionManager}. Based on the concept
 * of an underlying "transaction object".
 *
 * <p>Holds all status information that {@link AbstractPlatformTransactionManager}
 * needs internally, including a generic transaction object determined by the
 * concrete transaction manager implementation.
 *
 * <p>Supports delegating savepoint-related methods to a transaction object
 * that implements the {@link SavepointManager} interface.
 *
 * <p><b>NOTE:</b> This is <i>not</i> intended for use with other PlatformTransactionManager
 * implementations, in particular not for mock transaction managers in testing environments.
 * Use the alternative {@link SimpleTransactionStatus} class or a mock for the plain
 * {@link org.springframework.transaction.TransactionStatus} interface instead.
 *
 * @author Juergen Hoeller
 * @since 19.01.2004
 * @see AbstractPlatformTransactionManager
 * @see org.springframework.transaction.SavepointManager
 * @see #getTransaction
 * @see #createSavepoint
 * @see #rollbackToSavepoint
 * @see #releaseSavepoint
 * @see SimpleTransactionStatus
 */
public class DefaultTransactionStatus extends AbstractTransactionStatus {

    /**
     * 事务对象，若是空事务该属性就是null
     */
    @Nullable
    private final Object transaction;

    /**
     * 新事务。事务是新开启的(非事务也算)，这个属性就是true。
     *
     * 用处：在事务完成时，会判断 transaction != null && newTransaction 就做处理(从事务资源中移除、释放连接等操作)
     *      {@link AbstractPlatformTransactionManager#cleanupAfterCompletion(DefaultTransactionStatus)}
     *
     * 举例事务嵌套：
     *  - require->require 第一个是true，第二个是false
     *  - require->nested 第一个是true，第二个是false
     *  - require->require_new  第一个、第二个都是true
     *  - require->not_supported  第一个是true，第二个是false
     *  - not_supported->not_supported  第一个、第二个都是true(因为not_supported不算存在事务，所以再次执行也是按照新事物算)
     *
     */
    private final boolean newTransaction;

    /**
     * 新同步。
     * 是true，那么在事务开启时会将事务的信息设置到 TransactionSynchronizationManager 中，而要想设置，那么得保证当前线程没有存东西在 TransactionSynchronizationManager 中
     * 不存东西？那就是 事务被暂停了或者是非事务方法
     *
     *  {@link AbstractPlatformTransactionManager#prepareSynchronization}
     *
     * 单事务：该属性就是true，
     * 嵌套事务：父事务肯定是true，而子事务，只有子事务和父事务不一样才是新的
     *
     * 举例事务嵌套：
     *  - require->require 第一个是true，第二个是false
     *  - require->nested 第一个是true，第二个是false
     *  - require->require_new  第一个、第二个都是true
     *  - require->not_supported  第一个、第二个都是true
     */
    private final boolean newSynchronization;

    private final boolean readOnly;

    private final boolean debug;

    /**
     * 暂停的资源(上一个事务的连接和TransactionSynchronization等)
     *    {@link AbstractPlatformTransactionManager.SuspendedResourcesHolder}
     */
    @Nullable
    private final Object suspendedResources;


    /**
     * Create a new {@code DefaultTransactionStatus} instance.
     * @param transaction underlying transaction object that can hold state
     * for the internal transaction implementation
     * @param newTransaction if the transaction is new, otherwise participating
     * in an existing transaction
     * @param newSynchronization if a new transaction synchronization has been
     * opened for the given transaction
     * @param readOnly whether the transaction is marked as read-only
     * @param debug should debug logging be enabled for the handling of this transaction?
     * Caching it in here can prevent repeated calls to ask the logging system whether
     * debug logging should be enabled.
     * @param suspendedResources a holder for resources that have been suspended
     * for this transaction, if any
     */
    public DefaultTransactionStatus(
            @Nullable Object transaction, boolean newTransaction, boolean newSynchronization,
            boolean readOnly, boolean debug, @Nullable Object suspendedResources) {

        this.transaction = transaction;
        this.newTransaction = newTransaction;
        this.newSynchronization = newSynchronization;
        this.readOnly = readOnly;
        this.debug = debug;
        this.suspendedResources = suspendedResources;
    }


    /**
     * Return the underlying transaction object.
     * @throws IllegalStateException if no transaction is active
     */
    public Object getTransaction() {
        Assert.state(this.transaction != null, "No transaction active");
        return this.transaction;
    }

    /**
     * Return whether there is an actual transaction active.
     */
    public boolean hasTransaction() {
        return (this.transaction != null);
    }

    @Override
    public boolean isNewTransaction() {
        return (hasTransaction() && this.newTransaction);
    }

    /**
     * Return if a new transaction synchronization has been opened
     * for this transaction.
     */
    public boolean isNewSynchronization() {
        return this.newSynchronization;
    }

    /**
     * Return if this transaction is defined as read-only transaction.
     */
    public boolean isReadOnly() {
        return this.readOnly;
    }

    /**
     * Return whether the progress of this transaction is debugged. This is used by
     * {@link AbstractPlatformTransactionManager} as an optimization, to prevent repeated
     * calls to {@code logger.isDebugEnabled()}. Not really intended for client code.
     */
    public boolean isDebug() {
        return this.debug;
    }

    /**
     * Return the holder for resources that have been suspended for this transaction,
     * if any.
     */
    @Nullable
    public Object getSuspendedResources() {
        return this.suspendedResources;
    }


    //---------------------------------------------------------------------
    // Enable functionality through underlying transaction object
    //---------------------------------------------------------------------

    /**
     * Determine the rollback-only flag via checking the transaction object, provided
     * that the latter implements the {@link SmartTransactionObject} interface.
     * <p>Will return {@code true} if the global transaction itself has been marked
     * rollback-only by the transaction coordinator, for example in case of a timeout.
     * @see SmartTransactionObject#isRollbackOnly()
     */
    @Override
    public boolean isGlobalRollbackOnly() {
        return ((this.transaction instanceof SmartTransactionObject) &&
                ((SmartTransactionObject) this.transaction).isRollbackOnly());
    }

    /**
     * This implementation exposes the {@link SavepointManager} interface
     * of the underlying transaction object, if any.
     * @throws NestedTransactionNotSupportedException if savepoints are not supported
     * @see #isTransactionSavepointManager()
     */
    @Override
    protected SavepointManager getSavepointManager() {
        Object transaction = this.transaction;
        if (!(transaction instanceof SavepointManager)) {
            throw new NestedTransactionNotSupportedException(
                    "Transaction object [" + this.transaction + "] does not support savepoints");
        }
        return (SavepointManager) transaction;
    }

    /**
     * Return whether the underlying transaction implements the {@link SavepointManager}
     * interface and therefore supports savepoints.
     * @see #getTransaction()
     * @see #getSavepointManager()
     */
    public boolean isTransactionSavepointManager() {
        return (this.transaction instanceof SavepointManager);
    }

    /**
     * Delegate the flushing to the transaction object, provided that the latter
     * implements the {@link SmartTransactionObject} interface.
     * @see SmartTransactionObject#flush()
     */
    @Override
    public void flush() {
        if (this.transaction instanceof SmartTransactionObject) {
            ((SmartTransactionObject) this.transaction).flush();
        }
    }

}
