/*
 * Copyright 2002-2018 the original author or authors.
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

import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

/**
 * Resource holder wrapping a JDBC {@link Connection}.
 * {@link DataSourceTransactionManager} binds instances of this class
 * to the thread, for a specific {@link javax.sql.DataSource}.
 *
 * <p>Inherits rollback-only support for nested JDBC transactions
 * and reference count functionality from the base class.
 *
 * <p>Note: This is an SPI class, not intended to be used by applications.
 *
 * @author Juergen Hoeller
 * @since 06.05.2003
 * @see DataSourceTransactionManager
 * @see DataSourceUtils
 */
public class ConnectionHolder extends ResourceHolderSupport {

    /**
     * Prefix for savepoint names.
     */
    public static final String SAVEPOINT_NAME_PREFIX = "SAVEPOINT_";


    @Nullable
    private ConnectionHandle connectionHandle;

    @Nullable
    private Connection currentConnection;

    /**
     * 激活的事务。就是当前事务对象用的连接
     *
     * 该属性是true说明已存在事务 {@link DataSourceTransactionManager#isExistingTransaction(Object)}
     *
     * 开启事务，其实就是给 DataSourceTransactionObject 设置ResourceHolderSupport类型的属性，
     * 此时会将 transactionActive 和 synchronizedWithTransaction(父类的属性) 设置为true，只有在事务完成时，才会将该属性设置为false。
     *    {@link org.springframework.jdbc.datasource.DataSourceTransactionManager#doBegin(Object, TransactionDefinition)}
     *    {@link DataSourceTransactionManager#doCleanupAfterCompletion(Object)}
     *
     * 而在事务暂停时，其实并没有修改为false，因为没必要，因为事务暂停是直接将 DataSourceTransactionObject的ResourceHolderSupport类型的属性移除了，
     * 并存到SuspendedResourcesHolder对象中，所以根本就没必要，因为从ThreadLocal中读不到了，并不会影响新事物的执行
     *    {@link AbstractPlatformTransactionManager#suspend(Object)}
     *
     * 和其父类属性{@link ResourceHolderSupport#synchronizedWithTransaction}很像
     * 区别：是事务对象的连接 transactionActive 是true，是事务内创建的连接 synchronizedWithTransaction 是true，
     *      而事务对象的连接肯定是事务内创建的连接，事务内创建的连接 不一定是事务对象的连接。
     *      比如你在事务方法内(空事务也算) 使用 {@link DataSourceUtils#doGetConnection(DataSource)}获取连接，使用的数据源和事务管理器的数据源不一致，
     *      那就会创建出新的连接，这个连接 这称为事务内创建的连接 只会将连接的 synchronizedWithTransaction 设置为true，
     *
     *      Tips：
     *      1. 事务对象的连接 至多只有一个，空事务没有
     *      2. 事务内创建的连接 有多个
     */
    private boolean transactionActive = false;

    @Nullable
    private Boolean savepointsSupported;

    private int savepointCounter = 0;


    /**
     * Create a new ConnectionHolder for the given ConnectionHandle.
     * @param connectionHandle the ConnectionHandle to hold
     */
    public ConnectionHolder(ConnectionHandle connectionHandle) {
        Assert.notNull(connectionHandle, "ConnectionHandle must not be null");
        this.connectionHandle = connectionHandle;
    }

    /**
     * Create a new ConnectionHolder for the given JDBC Connection,
     * wrapping it with a {@link SimpleConnectionHandle},
     * assuming that there is no ongoing transaction.
     * @param connection the JDBC Connection to hold
     * @see SimpleConnectionHandle
     * @see #ConnectionHolder(java.sql.Connection, boolean)
     */
    public ConnectionHolder(Connection connection) {
        this.connectionHandle = new SimpleConnectionHandle(connection);
    }

    /**
     * Create a new ConnectionHolder for the given JDBC Connection,
     * wrapping it with a {@link SimpleConnectionHandle}.
     * @param connection the JDBC Connection to hold
     * @param transactionActive whether the given Connection is involved
     * in an ongoing transaction
     * @see SimpleConnectionHandle
     */
    public ConnectionHolder(Connection connection, boolean transactionActive) {
        this(connection);
        this.transactionActive = transactionActive;
    }


    /**
     * Return the ConnectionHandle held by this ConnectionHolder.
     */
    @Nullable
    public ConnectionHandle getConnectionHandle() {
        return this.connectionHandle;
    }

    /**
     * Return whether this holder currently has a Connection.
     */
    protected boolean hasConnection() {
        return (this.connectionHandle != null);
    }

    /**
     * Set whether this holder represents an active, JDBC-managed transaction.
     * @see DataSourceTransactionManager
     */
    protected void setTransactionActive(boolean transactionActive) {
        this.transactionActive = transactionActive;
    }

    /**
     * Return whether this holder represents an active, JDBC-managed transaction.
     */
    protected boolean isTransactionActive() {
        return this.transactionActive;
    }


    /**
     * Override the existing Connection handle with the given Connection.
     * Reset the handle if given {@code null}.
     * <p>Used for releasing the Connection on suspend (with a {@code null}
     * argument) and setting a fresh Connection on resume.
     */
    protected void setConnection(@Nullable Connection connection) {
        if (this.currentConnection != null) {
            if (this.connectionHandle != null) {
                this.connectionHandle.releaseConnection(this.currentConnection);
            }
            this.currentConnection = null;
        }
        if (connection != null) {
            this.connectionHandle = new SimpleConnectionHandle(connection);
        } else {
            this.connectionHandle = null;
        }
    }

    /**
     * Return the current Connection held by this ConnectionHolder.
     * <p>This will be the same Connection until {@code released}
     * gets called on the ConnectionHolder, which will reset the
     * held Connection, fetching a new Connection on demand.
     * @see ConnectionHandle#getConnection()
     * @see #released()
     */
    public Connection getConnection() {
        Assert.notNull(this.connectionHandle, "Active Connection is required");
        if (this.currentConnection == null) {
            this.currentConnection = this.connectionHandle.getConnection();
        }
        return this.currentConnection;
    }

    /**
     * Return whether JDBC 3.0 Savepoints are supported.
     * Caches the flag for the lifetime of this ConnectionHolder.
     * @throws SQLException if thrown by the JDBC driver
     */
    public boolean supportsSavepoints() throws SQLException {
        if (this.savepointsSupported == null) {
            this.savepointsSupported = getConnection().getMetaData().supportsSavepoints();
        }
        return this.savepointsSupported;
    }

    /**
     * Create a new JDBC 3.0 Savepoint for the current Connection,
     * using generated savepoint names that are unique for the Connection.
     * @return the new Savepoint
     * @throws SQLException if thrown by the JDBC driver
     */
    public Savepoint createSavepoint() throws SQLException {
        this.savepointCounter++;
        return getConnection().setSavepoint(SAVEPOINT_NAME_PREFIX + this.savepointCounter);
    }

    /**
     * Releases the current Connection held by this ConnectionHolder.
     * <p>This is necessary for ConnectionHandles that expect "Connection borrowing",
     * where each returned Connection is only temporarily leased and needs to be
     * returned once the data operation is done, to make the Connection available
     * for other operations within the same transaction.
     */
    @Override
    public void released() {
        super.released();
        if (!isOpen() && this.currentConnection != null) {
            if (this.connectionHandle != null) {
                this.connectionHandle.releaseConnection(this.currentConnection);
            }
            // 设置为 null，就是没有连接的
            this.currentConnection = null;
        }
    }


    @Override
    public void clear() {
        super.clear();
        this.transactionActive = false;
        this.savepointsSupported = null;
        this.savepointCounter = 0;
    }

}
