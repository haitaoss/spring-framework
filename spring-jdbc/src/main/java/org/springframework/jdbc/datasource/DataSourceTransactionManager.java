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

package org.springframework.jdbc.datasource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.*;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * {@link org.springframework.transaction.PlatformTransactionManager}
 * implementation for a single JDBC {@link javax.sql.DataSource}. This class is
 * capable of working in any environment with any JDBC driver, as long as the setup
 * uses a {@code javax.sql.DataSource} as its {@code Connection} factory mechanism.
 * Binds a JDBC Connection from the specified DataSource to the current thread,
 * potentially allowing for one thread-bound Connection per DataSource.
 *
 * <p><b>Note: The DataSource that this transaction manager operates on needs
 * to return independent Connections.</b> The Connections may come from a pool
 * (the typical case), but the DataSource must not return thread-scoped /
 * request-scoped Connections or the like. This transaction manager will
 * associate Connections with thread-bound transactions itself, according
 * to the specified propagation behavior. It assumes that a separate,
 * independent Connection can be obtained even during an ongoing transaction.
 *
 * <p>Application code is required to retrieve the JDBC Connection via
 * {@link DataSourceUtils#getConnection(DataSource)} instead of a standard
 * Java EE-style {@link DataSource#getConnection()} call. Spring classes such as
 * {@link org.springframework.jdbc.core.JdbcTemplate} use this strategy implicitly.
 * If not used in combination with this transaction manager, the
 * {@link DataSourceUtils} lookup strategy behaves exactly like the native
 * DataSource lookup; it can thus be used in a portable fashion.
 *
 * <p>Alternatively, you can allow application code to work with the standard
 * Java EE-style lookup pattern {@link DataSource#getConnection()}, for example for
 * legacy code that is not aware of Spring at all. In that case, define a
 * {@link TransactionAwareDataSourceProxy} for your target DataSource, and pass
 * that proxy DataSource to your DAOs, which will automatically participate in
 * Spring-managed transactions when accessing it.
 *
 * <p>Supports custom isolation levels, and timeouts which get applied as
 * appropriate JDBC statement timeouts. To support the latter, application code
 * must either use {@link org.springframework.jdbc.core.JdbcTemplate}, call
 * {@link DataSourceUtils#applyTransactionTimeout} for each created JDBC Statement,
 * or go through a {@link TransactionAwareDataSourceProxy} which will create
 * timeout-aware JDBC Connections and Statements automatically.
 *
 * <p>Consider defining a {@link LazyConnectionDataSourceProxy} for your target
 * DataSource, pointing both this transaction manager and your DAOs to it.
 * This will lead to optimized handling of "empty" transactions, i.e. of transactions
 * without any JDBC statements executed. A LazyConnectionDataSourceProxy will not fetch
 * an actual JDBC Connection from the target DataSource until a Statement gets executed,
 * lazily applying the specified transaction settings to the target Connection.
 *
 * <p>This transaction manager supports nested transactions via the JDBC 3.0
 * {@link java.sql.Savepoint} mechanism. The
 * {@link #setNestedTransactionAllowed "nestedTransactionAllowed"} flag defaults
 * to "true", since nested transactions will work without restrictions on JDBC
 * drivers that support savepoints (such as the Oracle JDBC driver).
 *
 * <p>This transaction manager can be used as a replacement for the
 * {@link org.springframework.transaction.jta.JtaTransactionManager} in the single
 * resource case, as it does not require a container that supports JTA, typically
 * in combination with a locally defined JDBC DataSource (e.g. an Apache Commons
 * DBCP connection pool). Switching between this local strategy and a JTA
 * environment is just a matter of configuration!
 *
 * <p>As of 4.3.4, this transaction manager triggers flush callbacks on registered
 * transaction synchronizations (if synchronization is generally active), assuming
 * resources operating on the underlying JDBC {@code Connection}. This allows for
 * setup analogous to {@code JtaTransactionManager}, in particular with respect to
 * lazily registered ORM resources (e.g. a Hibernate {@code Session}).
 *
 * <p><b>NOTE: As of 5.3, {@link org.springframework.jdbc.support.JdbcTransactionManager}
 * is available as an extended subclass which includes commit/rollback exception
 * translation, aligned with {@link org.springframework.jdbc.core.JdbcTemplate}.</b>
 *
 * @author Juergen Hoeller
 * @since 02.05.2003
 * @see #setNestedTransactionAllowed
 * @see java.sql.Savepoint
 * @see DataSourceUtils#getConnection(javax.sql.DataSource)
 * @see DataSourceUtils#applyTransactionTimeout
 * @see DataSourceUtils#releaseConnection
 * @see TransactionAwareDataSourceProxy
 * @see LazyConnectionDataSourceProxy
 * @see org.springframework.jdbc.core.JdbcTemplate
 */
@SuppressWarnings("serial")
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager
        implements ResourceTransactionManager, InitializingBean {

    @Nullable
    private DataSource dataSource;

    private boolean enforceReadOnly = false;


    /**
     * Create a new DataSourceTransactionManager instance.
     * A DataSource has to be set to be able to use it.
     * @see #setDataSource
     */
    public DataSourceTransactionManager() {
        setNestedTransactionAllowed(true);
    }

    /**
     * Create a new DataSourceTransactionManager instance.
     * @param dataSource the JDBC DataSource to manage transactions for
     */
    public DataSourceTransactionManager(DataSource dataSource) {
        this();
        setDataSource(dataSource);
        afterPropertiesSet();
    }


    /**
     * Set the JDBC DataSource that this instance should manage transactions for.
     * <p>This will typically be a locally defined DataSource, for example an
     * Apache Commons DBCP connection pool. Alternatively, you can also drive
     * transactions for a non-XA J2EE DataSource fetched from JNDI. For an XA
     * DataSource, use JtaTransactionManager.
     * <p>The DataSource specified here should be the target DataSource to manage
     * transactions for, not a TransactionAwareDataSourceProxy. Only data access
     * code may work with TransactionAwareDataSourceProxy, while the transaction
     * manager needs to work on the underlying target DataSource. If there's
     * nevertheless a TransactionAwareDataSourceProxy passed in, it will be
     * unwrapped to extract its target DataSource.
     * <p><b>The DataSource passed in here needs to return independent Connections.</b>
     * The Connections may come from a pool (the typical case), but the DataSource
     * must not return thread-scoped / request-scoped Connections or the like.
     * @see TransactionAwareDataSourceProxy
     * @see org.springframework.transaction.jta.JtaTransactionManager
     */
    public void setDataSource(@Nullable DataSource dataSource) {
        if (dataSource instanceof TransactionAwareDataSourceProxy) {
            // If we got a TransactionAwareDataSourceProxy, we need to perform transactions
            // for its underlying target DataSource, else data access code won't see
            // properly exposed transactions (i.e. transactions for the target DataSource).
            this.dataSource = ((TransactionAwareDataSourceProxy) dataSource).getTargetDataSource();
        } else {
            this.dataSource = dataSource;
        }
    }

    /**
     * Return the JDBC DataSource that this instance manages transactions for.
     */
    @Nullable
    public DataSource getDataSource() {
        return this.dataSource;
    }

    /**
     * Obtain the DataSource for actual use.
     * @return the DataSource (never {@code null})
     * @throws IllegalStateException in case of no DataSource set
     * @since 5.0
     */
    protected DataSource obtainDataSource() {
        DataSource dataSource = getDataSource();
        Assert.state(dataSource != null, "No DataSource set");
        return dataSource;
    }

    /**
     * Specify whether to enforce the read-only nature of a transaction
     * (as indicated by {@link TransactionDefinition#isReadOnly()}
     * through an explicit statement on the transactional connection:
     * "SET TRANSACTION READ ONLY" as understood by Oracle, MySQL and Postgres.
     * <p>The exact treatment, including any SQL statement executed on the connection,
     * can be customized through {@link #prepareTransactionalConnection}.
     * <p>This mode of read-only handling goes beyond the {@link Connection#setReadOnly}
     * hint that Spring applies by default. In contrast to that standard JDBC hint,
     * "SET TRANSACTION READ ONLY" enforces an isolation-level-like connection mode
     * where data manipulation statements are strictly disallowed. Also, on Oracle,
     * this read-only mode provides read consistency for the entire transaction.
     * <p>Note that older Oracle JDBC drivers (9i, 10g) used to enforce this read-only
     * mode even for {@code Connection.setReadOnly(true}. However, with recent drivers,
     * this strong enforcement needs to be applied explicitly, e.g. through this flag.
     * @since 4.3.7
     * @see #prepareTransactionalConnection
     */
    public void setEnforceReadOnly(boolean enforceReadOnly) {
        this.enforceReadOnly = enforceReadOnly;
    }

    /**
     * Return whether to enforce the read-only nature of a transaction
     * through an explicit statement on the transactional connection.
     * @since 4.3.7
     * @see #setEnforceReadOnly
     */
    public boolean isEnforceReadOnly() {
        return this.enforceReadOnly;
    }

    @Override
    public void afterPropertiesSet() {
        if (getDataSource() == null) {
            throw new IllegalArgumentException("Property 'dataSource' is required");
        }
    }


    @Override
    public Object getResourceFactory() {
        return obtainDataSource();
    }

    @Override
    protected Object doGetTransaction() {
        DataSourceTransactionObject txObject = new DataSourceTransactionObject();
        txObject.setSavepointAllowed(isNestedTransactionAllowed());
        /**
         * 使用 DataSource 作为key从 ThreadLocal中拿 ConnectionHolder,
         * 没有就是 null，第一次开启事务 这个值就是null咯
         * */
        ConnectionHolder conHolder =
                (ConnectionHolder) TransactionSynchronizationManager.getResource(obtainDataSource());
        txObject.setConnectionHolder(conHolder, false);
        return txObject;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
        /**
         * 在开始事务时会将 isTransactionActive 设置为true
         * 只有在事务完成时，才会将事务的持有的 getConnectionHolder().isTransactionActive() 设置为false
         * 所以可以通过这个判断 是否存在事务
         *
         * 空事务不算存在事务，因为空事务不会开启事务(不会给事务对象创建数据库连接)，也就不会将 isTransactionActive 设置为true
         *
         * 在空事务下，使用{@link DataSourceUtils#doGetConnection(DataSource)}获取连接，也不会设置 isTransactionActive 为true，
         * 只是存到了 {@link TransactionSynchronizationManager#resources}
         * */
        return (txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive());
    }

    /**
     * 1. 事务对象，没有连接或者连接isSynchronizedWithTransaction 就通过数据源创建连接然后设置给事务对象<br/>
     * 2. 将 @Transactional 的信息设置到连接和事务对象中（自动提交、是否只读、隔离级别、超时时间） <br/>
     * 3. 是新创建的连接，就使用数据源作为key，将连接绑定是事务资源中 {@link TransactionSynchronizationManager#resources} <br/>
     * 4. 设置 ConnectionHolder 属性 isSynchronizedWithTransaction 为true
     *
     * @param transaction the transaction object returned by {@code doGetTransaction}
     * @param definition a TransactionDefinition instance, describing propagation
     * behavior, isolation level, read-only flag, timeout, and transaction name
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
        Connection con = null;

        try {
            /**
             * 没有连接 或者 连接是事务同步 那就应该创建一个新的连接
             *
             * 可以这么理解，当前方法叫 doBegin ，也就是要给事务对象绑定连接。连接没有那就创建一个在绑定合情合理，
             * 而连接有了，但是连接是事务同步 说明上一个事务还未完成，你还要绑定 那也给你创建一个新的连接，这种属于非法操作了，如果你老老实实用Spring 别搞啥自定义，是不会出现这种情况的
             *
             * 因为 doBegin 是在 startTransaction 的时候会执行，而 startTransaction 之前会先暂停当前线程的事物资源，在开启事物，
             * 而暂停就是
             *      1. 移除事物对象的引用 `txObject.setConnectionHolder(null);`
             *      2. 从 {@link TransactionSynchronizationManager#resources} 移除 ConnectionHolder 存起来。这个ConnectionHolder 其实就是上面置空的属性
             *          Tips: 暂停事务的代码 {@link DataSourceTransactionManager#doSuspend(Object)}
             *      记住代码是死的，Spring的事物代码都这么写了，所以基本不可能出现 isSynchronizedWithTransaction 的情况，除非开发人员玩花活
             *
             * 注：在一个Spring事务(单一事务、嵌套事务)
             *      事务暂停：`txObject.setConnectionHolder(null)`
             *      事务完成：` txObject.getConnectionHolder().setSynchronizedWithTransaction(false);`
             * */
            if (!txObject.hasConnectionHolder() ||
                    txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
                /**
                 * 拿到连接 {@link DataSource#getConnection()}
                 *
                 * 这里就是动态数据源的原理了 {@link AbstractRoutingDataSource#getConnection()}
                 * */
                Connection newCon = obtainDataSource().getConnection();
                if (logger.isDebugEnabled()) {
                    logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
                }
                // 装饰 Connection 并设置给事务对象
                txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
            }

            /**
             * 表示 这个事务对象的连接 是事务同步
             *
             * 在事物完成时 会将该属性设置为 false
             *  {@link DataSourceTransactionManager#doCleanupAfterCompletion(Object)}
             *  {@link ConnectionHolder#clear()}
             * */
            txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
            con = txObject.getConnectionHolder().getConnection();

            /**
             * 就是根据 @Transactional 注解值，给Connection设置 只读属性、隔离级别属性。
             * 返回的是 Connection 之前的隔离饥级别信息
             * */
            Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
            // 隔离级别
            txObject.setPreviousIsolationLevel(previousIsolationLevel);
            // 是否只读
            txObject.setReadOnly(definition.isReadOnly());

            // 如果连接是自动提交，就设置为非自动提交
            // Switch to manual commit if necessary. This is very expensive in some JDBC drivers,
            // so we don't want to do it unnecessarily (for example if we've explicitly
            // configured the connection pool to set it already).
            if (con.getAutoCommit()) {
                txObject.setMustRestoreAutoCommit(true);
                if (logger.isDebugEnabled()) {
                    logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
                }
                // 设置为非自动提交
                con.setAutoCommit(false);
            }

            /**
             * 就比如 `@Transactional(readOnly = true)` 就设置事务为只读的
             * 如果是只读的就执行sql设置为只读事务  `SET TRANSACTION READ ONLY`
             * */
            prepareTransactionalConnection(con, definition);
            /**
             * 活动的事务。
             * 在事务完成时才会将该属性设置为false，暂停事务并不会
             * */
            txObject.getConnectionHolder().setTransactionActive(true);

            /**
             * `@Transactional(timeout = -1)` 不是-1才拿注解值，没有就返回默认值 -1
             * */
            int timeout = determineTimeout(definition);
            if (timeout != TransactionDefinition.TIMEOUT_DEFAULT) {
                // 设置超时时间
                txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
            }

            /**
             * 是新创建的connect的，就绑定到ThreadLocal中 {@link TransactionSynchronizationManager#resources}
             * 缓存的Map<DataSource,ConnectionHolder>。使用DataSource作为key，是因为一个线程可能有来回切换数据源的情况(动态数据源)
             * */
            // Bind the connection holder to the thread.
            if (txObject.isNewConnectionHolder()) {
                /**
                 * 绑定到事务资源中 {@link TransactionSynchronizationManager#resources}
                 * */
                TransactionSynchronizationManager.bindResource(obtainDataSource(), txObject.getConnectionHolder());
            }
        } catch (Throwable ex) {
            if (txObject.isNewConnectionHolder()) {
                /**
                 * 从事务资源 {@link TransactionSynchronizationManager#resources} 中移除该连接，或者是关闭连接
                 *
                 * */
                DataSourceUtils.releaseConnection(con, obtainDataSource());
                // 清除 txObject 的连接信息
                txObject.setConnectionHolder(null, false);
            }
            throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", ex);
        }
    }

    /**
     *
     * @param transaction the transaction object returned by {@code doGetTransaction}
     * @return
     */
    @Override
    protected Object doSuspend(Object transaction) {
        DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;
        txObject.setConnectionHolder(null);
        return TransactionSynchronizationManager.unbindResource(obtainDataSource());
    }

    @Override
    protected void doResume(@Nullable Object transaction, Object suspendedResources) {
        TransactionSynchronizationManager.bindResource(obtainDataSource(), suspendedResources);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
        Connection con = txObject.getConnectionHolder().getConnection();
        if (status.isDebug()) {
            logger.debug("Committing JDBC transaction on Connection [" + con + "]");
        }
        try {
            con.commit();
        } catch (SQLException ex) {
            throw translateException("JDBC commit", ex);
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
        Connection con = txObject.getConnectionHolder().getConnection();
        if (status.isDebug()) {
            logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
        }
        try {
            con.rollback();
        } catch (SQLException ex) {
            throw translateException("JDBC rollback", ex);
        }
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        DataSourceTransactionObject txObject = (DataSourceTransactionObject) status.getTransaction();
        if (status.isDebug()) {
            logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() +
                    "] rollback-only");
        }
        txObject.setRollbackOnly();
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        DataSourceTransactionObject txObject = (DataSourceTransactionObject) transaction;

        // Remove the connection holder from the thread, if exposed.
        if (txObject.isNewConnectionHolder()) {
            // 从事务资源中移除
            TransactionSynchronizationManager.unbindResource(obtainDataSource());
        }

        // Reset connection.
        Connection con = txObject.getConnectionHolder().getConnection();
        try {
            if (txObject.isMustRestoreAutoCommit()) {
                con.setAutoCommit(true);
            }
            // 完成事务后重设连接，就是恢复Connection之前的值
            DataSourceUtils.resetConnectionAfterTransaction(
                    con, txObject.getPreviousIsolationLevel(), txObject.isReadOnly());
        } catch (Throwable ex) {
            logger.debug("Could not reset JDBC Connection after transaction", ex);
        }

        if (txObject.isNewConnectionHolder()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
            }
            // 释放连接
            DataSourceUtils.releaseConnection(con, this.dataSource);
        }

        /**
         * 清除保存信息
         * 主要是将这个两个属性设置为false，表示事物完成了
         *  {@link ConnectionHolder#transactionActive}
         *  {@link ResourceHolderSupport#synchronizedWithTransaction}
         * */
        txObject.getConnectionHolder().clear();
    }


    /**
     * Prepare the transactional {@code Connection} right after transaction begin.
     * <p>The default implementation executes a "SET TRANSACTION READ ONLY" statement
     * if the {@link #setEnforceReadOnly "enforceReadOnly"} flag is set to {@code true}
     * and the transaction definition indicates a read-only transaction.
     * <p>The "SET TRANSACTION READ ONLY" is understood by Oracle, MySQL and Postgres
     * and may work with other databases as well. If you'd like to adapt this treatment,
     * override this method accordingly.
     * @param con the transactional JDBC Connection
     * @param definition the current transaction definition
     * @throws SQLException if thrown by JDBC API
     * @since 4.3.7
     * @see #setEnforceReadOnly
     */
    protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition)
            throws SQLException {

        if (isEnforceReadOnly() && definition.isReadOnly()) {
            try (Statement stmt = con.createStatement()) {
                stmt.executeUpdate("SET TRANSACTION READ ONLY");
            }
        }
    }

    /**
     * Translate the given JDBC commit/rollback exception to a common Spring
     * exception to propagate from the {@link #commit}/{@link #rollback} call.
     * <p>The default implementation throws a {@link TransactionSystemException}.
     * Subclasses may specifically identify concurrency failures etc.
     * @param task the task description (commit or rollback)
     * @param ex the SQLException thrown from commit/rollback
     * @return the translated exception to throw, either a
     * {@link org.springframework.dao.DataAccessException} or a
     * {@link org.springframework.transaction.TransactionException}
     * @since 5.3
     */
    protected RuntimeException translateException(String task, SQLException ex) {
        return new TransactionSystemException(task + " failed", ex);
    }


    /**
     * DataSource transaction object, representing a ConnectionHolder.
     * Used as transaction object by DataSourceTransactionManager.
     */
    private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {

        private boolean newConnectionHolder;

        private boolean mustRestoreAutoCommit;

        public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
            super.setConnectionHolder(connectionHolder);
            this.newConnectionHolder = newConnectionHolder;
        }

        public boolean isNewConnectionHolder() {
            return this.newConnectionHolder;
        }

        public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
            this.mustRestoreAutoCommit = mustRestoreAutoCommit;
        }

        public boolean isMustRestoreAutoCommit() {
            return this.mustRestoreAutoCommit;
        }

        public void setRollbackOnly() {
            getConnectionHolder().setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return getConnectionHolder().isRollbackOnly();
        }

        @Override
        public void flush() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationUtils.triggerFlush();
            }
        }
    }

}
