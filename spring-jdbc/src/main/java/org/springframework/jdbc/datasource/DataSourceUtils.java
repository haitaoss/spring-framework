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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Helper class that provides static methods for obtaining JDBC Connections from
 * a {@link javax.sql.DataSource}. Includes special support for Spring-managed
 * transactional Connections, e.g. managed by {@link DataSourceTransactionManager}
 * or {@link org.springframework.transaction.jta.JtaTransactionManager}.
 *
 * <p>Used internally by Spring's {@link org.springframework.jdbc.core.JdbcTemplate},
 * Spring's JDBC operation objects and the JDBC {@link DataSourceTransactionManager}.
 * Can also be used directly in application code.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see #getConnection
 * @see #releaseConnection
 * @see DataSourceTransactionManager
 * @see org.springframework.transaction.jta.JtaTransactionManager
 * @see org.springframework.transaction.support.TransactionSynchronizationManager
 */
public abstract class DataSourceUtils {

    /**
     * Order value for TransactionSynchronization objects that clean up JDBC Connections.
     */
    public static final int CONNECTION_SYNCHRONIZATION_ORDER = 1000;

    private static final Log logger = LogFactory.getLog(DataSourceUtils.class);


    /**
     * Obtain a Connection from the given DataSource. Translates SQLExceptions into
     * the Spring hierarchy of unchecked generic data access exceptions, simplifying
     * calling code and making any exception that is thrown more meaningful.
     * <p>Is aware of a corresponding Connection bound to the current thread, for example
     * when using {@link DataSourceTransactionManager}. Will bind a Connection to the
     * thread if transaction synchronization is active, e.g. when running within a
     * {@link org.springframework.transaction.jta.JtaTransactionManager JTA} transaction).
     * @param dataSource the DataSource to obtain Connections from
     * @return a JDBC Connection from the given DataSource
     * @throws org.springframework.jdbc.CannotGetJdbcConnectionException
     * if the attempt to get a Connection failed
     * @see #releaseConnection
     */
    public static Connection getConnection(DataSource dataSource) throws CannotGetJdbcConnectionException {
        try {
            return doGetConnection(dataSource);
        } catch (SQLException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", ex);
        } catch (IllegalStateException ex) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection: " + ex.getMessage());
        }
    }

    /**
     * Actually obtain a JDBC Connection from the given DataSource.
     * Same as {@link #getConnection}, but throwing the original SQLException.
     * <p>Is aware of a corresponding Connection bound to the current thread, for example
     * when using {@link DataSourceTransactionManager}. Will bind a Connection to the thread
     * if transaction synchronization is active (e.g. if in a JTA transaction).
     * <p>Directly accessed by {@link TransactionAwareDataSourceProxy}.
     * @param dataSource the DataSource to obtain Connections from
     * @return a JDBC Connection from the given DataSource
     * @throws SQLException if thrown by JDBC methods
     * @see #doReleaseConnection
     */
    public static Connection doGetConnection(DataSource dataSource) throws SQLException {
        Assert.notNull(dataSource, "No DataSource specified");

        /**
         * 会使用 DataSource 作为key从 resources 中查询资源，找到就返回。
         * */
        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        /**
         * 存在连接 且 连接是事务的同步资源 就返回这个连接，不需要创建
         *
         * 事务的同步资源：指的是在事务内创建的连接，目的就是为了在一个事务内能重复使用。
         *      1. 开始事务时，创建的连接就会设置这个属性为true
         *          {@link DataSourceTransactionManager#doBegin(Object, TransactionDefinition)}
         *
         *      2. 当前方法(doGetConnection)，创建了新的连接，线程开启了事务(空事务也算是事务)，会设置这个属性为true
         *          {@link DataSourceUtils#doGetConnection(DataSource)}
         * */
        if (conHolder != null && (conHolder.hasConnection() || conHolder.isSynchronizedWithTransaction())) {
            conHolder.requested();
            if (!conHolder.hasConnection()) {
                logger.debug("Fetching resumed JDBC Connection from DataSource");
                conHolder.setConnection(fetchConnection(dataSource));
            }
            // 返回连接
            return conHolder.getConnection();
        }
        // Else we either got no holder or an empty thread-bound holder here.

        logger.debug("Fetching JDBC Connection from DataSource");
        /**
         * 就是获取连接 {@link DataSource#getConnection()}
         * 这里也就是动态数据源的原理了 {@link AbstractRoutingDataSource#getConnection()}
         * */
        Connection con = fetchConnection(dataSource);

        /**
         * 如果线程存在事务，就将连接设置到事务的同步资源中，表示该资源所属于事务对象
         * Tips：空事务也算。可以这么理解，只要Java虚拟机栈中存在@Transactional的方法，这个判断就是true
         * */
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                // Use same Connection for further JDBC actions within the transaction.
                // Thread-bound object will get removed by synchronization at transaction completion.
                ConnectionHolder holderToUse = conHolder;
                if (holderToUse == null) {
                    holderToUse = new ConnectionHolder(con);
                } else {
                    holderToUse.setConnection(con);
                }
                holderToUse.requested();
                /**
                 * 注册到属性中 {@link TransactionSynchronizationManager#synchronizations}
                 *
                 * 存到这个属性的好处，在事务完成时(rollback、commit)会回调接口，释放连接
                 * */
                TransactionSynchronizationManager.registerSynchronization(
                        new ConnectionSynchronization(holderToUse, dataSource));
                // 设置标识
                holderToUse.setSynchronizedWithTransaction(true);
                if (holderToUse != conHolder) {
                    // 是新的就注册到 事物资源中
                    TransactionSynchronizationManager.bindResource(dataSource, holderToUse);
                }
            } catch (RuntimeException ex) {
                // Unexpected exception from external delegation call -> close Connection and rethrow.
                releaseConnection(con, dataSource);
                throw ex;
            }
        }

        return con;
    }

    /**
     * Actually fetch a {@link Connection} from the given {@link DataSource},
     * defensively turning an unexpected {@code null} return value from
     * {@link DataSource#getConnection()} into an {@link IllegalStateException}.
     * @param dataSource the DataSource to obtain Connections from
     * @return a JDBC Connection from the given DataSource (never {@code null})
     * @throws SQLException if thrown by JDBC methods
     * @throws IllegalStateException if the DataSource returned a null value
     * @see DataSource#getConnection()
     */
    private static Connection fetchConnection(DataSource dataSource) throws SQLException {
        Connection con = dataSource.getConnection();
        if (con == null) {
            throw new IllegalStateException("DataSource returned null from getConnection(): " + dataSource);
        }
        return con;
    }

    /**
     * 就是将 @Transactional(readOnly = true,isolation = Isolation.READ_UNCOMMITTED) 的信息设置给 Connection
     * Prepare the given Connection with the given transaction semantics.
     * @param con the Connection to prepare
     * @param definition the transaction definition to apply
     * @return the previous isolation level, if any （以前的隔离级别(如果有的话)）
     * @throws SQLException if thrown by JDBC methods
     * @see #resetConnectionAfterTransaction
     * @see Connection#setTransactionIsolation
     * @see Connection#setReadOnly
     */
    @Nullable
    public static Integer prepareConnectionForTransaction(Connection con, @Nullable TransactionDefinition definition)
            throws SQLException {

        Assert.notNull(con, "No Connection specified");

        boolean debugEnabled = logger.isDebugEnabled();
        // Set read-only flag.
        if (definition != null && definition.isReadOnly()) {
            try {
                if (debugEnabled) {
                    logger.debug("Setting JDBC Connection [" + con + "] read-only");
                }
                // 只读的
                con.setReadOnly(true);
            } catch (SQLException | RuntimeException ex) {
                Throwable exToCheck = ex;
                while (exToCheck != null) {
                    if (exToCheck.getClass().getSimpleName().contains("Timeout")) {
                        // Assume it's a connection timeout that would otherwise get lost: e.g. from JDBC 4.0
                        throw ex;
                    }
                    exToCheck = exToCheck.getCause();
                }
                // "read-only not supported" SQLException -> ignore, it's just a hint anyway
                logger.debug("Could not set JDBC Connection read-only", ex);
            }
        }

        /**
         * 不是默认值才设置给 Connection
         * */
        // Apply specific isolation level, if any.
        Integer previousIsolationLevel = null;
        if (definition != null && definition.getIsolationLevel() != TransactionDefinition.ISOLATION_DEFAULT) {
            if (debugEnabled) {
                logger.debug("Changing isolation level of JDBC Connection [" + con + "] to " +
                        definition.getIsolationLevel());
            }
            /**
             * Connection的隔离级别，如果是刚拿到的 {@link DataSource#getConnection()} 那就是数据库配置的隔离级别
             * */
            int currentIsolation = con.getTransactionIsolation();
            if (currentIsolation != definition.getIsolationLevel()) {
                previousIsolationLevel = currentIsolation;
                // 设置隔离级别
                con.setTransactionIsolation(definition.getIsolationLevel());
            }
        }
        // 返回之前的隔离级别
        return previousIsolationLevel;
    }

    /**
     * Reset the given Connection after a transaction,
     * regarding read-only flag and isolation level.
     * @param con the Connection to reset
     * @param previousIsolationLevel the isolation level to restore, if any
     * @param resetReadOnly whether to reset the connection's read-only flag
     * @since 5.2.1
     * @see #prepareConnectionForTransaction
     * @see Connection#setTransactionIsolation
     * @see Connection#setReadOnly
     */
    public static void resetConnectionAfterTransaction(
            Connection con, @Nullable Integer previousIsolationLevel, boolean resetReadOnly) {

        Assert.notNull(con, "No Connection specified");
        boolean debugEnabled = logger.isDebugEnabled();
        try {
            // Reset transaction isolation to previous value, if changed for the transaction.
            if (previousIsolationLevel != null) {
                if (debugEnabled) {
                    logger.debug("Resetting isolation level of JDBC Connection [" +
                            con + "] to " + previousIsolationLevel);
                }
                con.setTransactionIsolation(previousIsolationLevel);
            }

            // Reset read-only flag if we originally switched it to true on transaction begin.
            if (resetReadOnly) {
                if (debugEnabled) {
                    logger.debug("Resetting read-only flag of JDBC Connection [" + con + "]");
                }
                con.setReadOnly(false);
            }
        } catch (Throwable ex) {
            logger.debug("Could not reset JDBC Connection after transaction", ex);
        }
    }

    /**
     * Reset the given Connection after a transaction,
     * regarding read-only flag and isolation level.
     * @param con the Connection to reset
     * @param previousIsolationLevel the isolation level to restore, if any
     * @deprecated as of 5.1.11, in favor of
     * {@link #resetConnectionAfterTransaction(Connection, Integer, boolean)}
     */
    @Deprecated
    public static void resetConnectionAfterTransaction(Connection con, @Nullable Integer previousIsolationLevel) {
        Assert.notNull(con, "No Connection specified");
        try {
            // Reset transaction isolation to previous value, if changed for the transaction.
            if (previousIsolationLevel != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Resetting isolation level of JDBC Connection [" +
                            con + "] to " + previousIsolationLevel);
                }
                con.setTransactionIsolation(previousIsolationLevel);
            }

            // Reset read-only flag.
            if (con.isReadOnly()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Resetting read-only flag of JDBC Connection [" + con + "]");
                }
                con.setReadOnly(false);
            }
        } catch (Throwable ex) {
            logger.debug("Could not reset JDBC Connection after transaction", ex);
        }
    }

    /**
     * Determine whether the given JDBC Connection is transactional, that is,
     * bound to the current thread by Spring's transaction facilities.
     * @param con the Connection to check
     * @param dataSource the DataSource that the Connection was obtained from
     * (may be {@code null})
     * @return whether the Connection is transactional
     */
    public static boolean isConnectionTransactional(Connection con, @Nullable DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        return (conHolder != null && connectionEquals(conHolder, con));
    }

    /**
     * Apply the current transaction timeout, if any,
     * to the given JDBC Statement object.
     * @param stmt the JDBC Statement object
     * @param dataSource the DataSource that the Connection was obtained from
     * @throws SQLException if thrown by JDBC methods
     * @see java.sql.Statement#setQueryTimeout
     */
    public static void applyTransactionTimeout(Statement stmt, @Nullable DataSource dataSource) throws SQLException {
        applyTimeout(stmt, dataSource, -1);
    }

    /**
     * Apply the specified timeout - overridden by the current transaction timeout,
     * if any - to the given JDBC Statement object.
     * @param stmt the JDBC Statement object
     * @param dataSource the DataSource that the Connection was obtained from
     * @param timeout the timeout to apply (or 0 for no timeout outside of a transaction)
     * @throws SQLException if thrown by JDBC methods
     * @see java.sql.Statement#setQueryTimeout
     */
    public static void applyTimeout(Statement stmt, @Nullable DataSource dataSource, int timeout) throws SQLException {
        Assert.notNull(stmt, "No Statement specified");
        ConnectionHolder holder = null;
        if (dataSource != null) {
            holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
        }
        if (holder != null && holder.hasTimeout()) {
            // Remaining transaction timeout overrides specified value.
            stmt.setQueryTimeout(holder.getTimeToLiveInSeconds());
        } else if (timeout >= 0) {
            // No current transaction timeout -> apply specified value.
            stmt.setQueryTimeout(timeout);
        }
    }

    /**
     * Close the given Connection, obtained from the given DataSource,
     * if it is not managed externally (that is, not bound to the thread).
     * @param con the Connection to close if necessary
     * (if this is {@code null}, the call will be ignored)
     * @param dataSource the DataSource that the Connection was obtained from
     * (may be {@code null})
     * @see #getConnection
     */
    public static void releaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) {
        try {
            doReleaseConnection(con, dataSource);
        } catch (SQLException ex) {
            logger.debug("Could not close JDBC Connection", ex);
        } catch (Throwable ex) {
            logger.debug("Unexpected exception on closing JDBC Connection", ex);
        }
    }

    /**
     * Actually close the given Connection, obtained from the given DataSource.
     * Same as {@link #releaseConnection}, but throwing the original SQLException.
     * <p>Directly accessed by {@link TransactionAwareDataSourceProxy}.
     * @param con the Connection to close if necessary
     * (if this is {@code null}, the call will be ignored)
     * @param dataSource the DataSource that the Connection was obtained from
     * (may be {@code null})
     * @throws SQLException if thrown by JDBC methods
     * @see #doGetConnection
     */
    public static void doReleaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) throws SQLException {
        if (con == null) {
            return;
        }
        if (dataSource != null) {
            /**
             * 就是从该属性 {@link TransactionSynchronizationManager#resources} 中获取资源，只有事务资源才会存在这里面
             * */
            ConnectionHolder conHolder = (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
            if (conHolder != null && connectionEquals(conHolder, con)) {
                /**
                 * 是事务性的链接，就不要关闭。
                 * 就是取消 ConnectionHolder 对 Connection 的引用，并不是关闭连接
                 * */
                // It's the transactional Connection: Don't close it.
                conHolder.released();
                return;
            }
        }
        // 关闭连接
        doCloseConnection(con, dataSource);
    }

    /**
     * Close the Connection, unless a {@link SmartDataSource} doesn't want us to.
     * @param con the Connection to close if necessary
     * @param dataSource the DataSource that the Connection was obtained from
     * @throws SQLException if thrown by JDBC methods
     * @see Connection#close()
     * @see SmartDataSource#shouldClose(Connection)
     */
    public static void doCloseConnection(Connection con, @Nullable DataSource dataSource) throws SQLException {
        if (!(dataSource instanceof SmartDataSource) || ((SmartDataSource) dataSource).shouldClose(con)) {
            con.close();
        }
    }

    /**
     * Determine whether the given two Connections are equal, asking the target
     * Connection in case of a proxy. Used to detect equality even if the
     * user passed in a raw target Connection while the held one is a proxy.
     * @param conHolder the ConnectionHolder for the held Connection (potentially a proxy)
     * @param passedInCon the Connection passed-in by the user
     * (potentially a target Connection without proxy)
     * @return whether the given Connections are equal
     * @see #getTargetConnection
     */
    private static boolean connectionEquals(ConnectionHolder conHolder, Connection passedInCon) {
        if (!conHolder.hasConnection()) {
            return false;
        }
        Connection heldCon = conHolder.getConnection();
        // Explicitly check for identity too: for Connection handles that do not implement
        // "equals" properly, such as the ones Commons DBCP exposes).
        return (heldCon == passedInCon || heldCon.equals(passedInCon) ||
                getTargetConnection(heldCon).equals(passedInCon));
    }

    /**
     * Return the innermost target Connection of the given Connection. If the given
     * Connection is a proxy, it will be unwrapped until a non-proxy Connection is
     * found. Otherwise, the passed-in Connection will be returned as-is.
     * @param con the Connection proxy to unwrap
     * @return the innermost target Connection, or the passed-in one if no proxy
     * @see ConnectionProxy#getTargetConnection()
     */
    public static Connection getTargetConnection(Connection con) {
        Connection conToUse = con;
        while (conToUse instanceof ConnectionProxy) {
            conToUse = ((ConnectionProxy) conToUse).getTargetConnection();
        }
        return conToUse;
    }

    /**
     * Determine the connection synchronization order to use for the given
     * DataSource. Decreased for every level of nesting that a DataSource
     * has, checked through the level of DelegatingDataSource nesting.
     * @param dataSource the DataSource to check
     * @return the connection synchronization order to use
     * @see #CONNECTION_SYNCHRONIZATION_ORDER
     */
    private static int getConnectionSynchronizationOrder(DataSource dataSource) {
        int order = CONNECTION_SYNCHRONIZATION_ORDER;
        DataSource currDs = dataSource;
        while (currDs instanceof DelegatingDataSource) {
            order--;
            currDs = ((DelegatingDataSource) currDs).getTargetDataSource();
        }
        return order;
    }


    /**
     * Callback for resource cleanup at the end of a non-native JDBC transaction
     * (e.g. when participating in a JtaTransactionManager transaction).
     * @see org.springframework.transaction.jta.JtaTransactionManager
     */
    private static class ConnectionSynchronization implements TransactionSynchronization {

        private final ConnectionHolder connectionHolder;

        private final DataSource dataSource;

        private int order;

        private boolean holderActive = true;

        public ConnectionSynchronization(ConnectionHolder connectionHolder, DataSource dataSource) {
            this.connectionHolder = connectionHolder;
            this.dataSource = dataSource;
            this.order = getConnectionSynchronizationOrder(dataSource);
        }

        @Override
        public int getOrder() {
            return this.order;
        }

        @Override
        public void suspend() {
            // 活动的
            if (this.holderActive) {
                /**
                 * 使用dataSource为key从{@link TransactionSynchronizationManager#resources}中删除
                 * */
                TransactionSynchronizationManager.unbindResource(this.dataSource);
                // 连接未打开,那就直接释放掉就行了
                if (this.connectionHolder.hasConnection() && !this.connectionHolder.isOpen()) {
                    /**
                     * 如果该Connection存在事物资源中，就取消事物资源对该Connection的引用
                     *
                     * 就是从该属性 {@link TransactionSynchronizationManager#resources} 中获取资源，如果资源和Connection 是一样的，说明
                     * Connection 是事物资源，只需要取消引用即可。否则就关闭连接
                     * */
                    // Release Connection on suspend if the application doesn't keep
                    // a handle to it anymore. We will fetch a fresh Connection if the
                    // application accesses the ConnectionHolder again after resume,
                    // assuming that it will participate in the same transaction.
                    releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                    // 取消引用
                    this.connectionHolder.setConnection(null);
                }
            }
        }

        @Override
        public void resume() {
            if (this.holderActive) {
                TransactionSynchronizationManager.bindResource(this.dataSource, this.connectionHolder);
            }
        }

        @Override
        public void beforeCompletion() {
            // Release Connection early if the holder is not open anymore
            // (that is, not used by another resource like a Hibernate Session
            // that has its own cleanup via transaction synchronization),
            // to avoid issues with strict JTA implementations that expect
            // the close call before transaction completion.
            if (!this.connectionHolder.isOpen()) {
                TransactionSynchronizationManager.unbindResource(this.dataSource);
                this.holderActive = false;
                if (this.connectionHolder.hasConnection()) {
                    releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                }
            }
        }

        @Override
        public void afterCompletion(int status) {
            // If we haven't closed the Connection in beforeCompletion,
            // close it now. The holder might have been used for other
            // cleanup in the meantime, for example by a Hibernate Session.
            if (this.holderActive) {
                // The thread-bound ConnectionHolder might not be available anymore,
                // since afterCompletion might get called from a different thread.
                TransactionSynchronizationManager.unbindResourceIfPossible(this.dataSource);
                this.holderActive = false;
                if (this.connectionHolder.hasConnection()) {
                    releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                    // Reset the ConnectionHolder: It might remain bound to the thread.
                    this.connectionHolder.setConnection(null);
                }
            }
            this.connectionHolder.reset();
        }
    }

}
