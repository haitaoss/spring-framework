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

package org.springframework.transaction.support;

import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;

import javax.sql.DataSource;
import java.util.Date;

/**
 * Convenient base class for resource holders.
 *
 * <p>Features rollback-only support for participating transactions.
 * Can expire after a certain number of seconds or milliseconds
 * in order to determine a transactional timeout.
 *
 * @author Juergen Hoeller
 * @since 02.02.2004
 * @see org.springframework.jdbc.datasource.DataSourceTransactionManager#doBegin
 * @see org.springframework.jdbc.datasource.DataSourceUtils#applyTransactionTimeout
 */
public abstract class ResourceHolderSupport implements ResourceHolder {

	/**
	 * 事务的同步资源，在事务内获取的资源，这个属性就是true。
	 *
	 * 将 synchronizedWithTransaction 设置为true 的情况
	 * 	1. 开启事务创建的连接 {@link org.springframework.jdbc.datasource.DataSourceTransactionManager#doBegin(Object, TransactionDefinition)}
	 * 	2. 执行 {@link org.springframework.jdbc.datasource.DataSourceUtils#doGetConnection(DataSource)} 获取连接
	 * 		(空事务下 和 事务下使用与事务管理器配置的数据源不一致的数据源获取的连接)
	 *
	 *
	 * 	只有在事务完成时，才会将该属性设置为false。
	 *	{@link {@link DataSourceTransactionManager#doCleanupAfterCompletion(Object)}}
	 *
	 * 而在事务暂停时，其实并没有修改为false，因为没必要，因为事务暂停是直接将 DataSourceTransactionObject的ResourceHolderSupport类型的属性移除了
	 * ，并存到SuspendedResourcesHolder对象中，
	 * 所以根本就没必要，因为从ThreadLocal中读不到了，并不会影响新事物的执行
	 *	{@link AbstractPlatformTransactionManager#suspend(Object)}
	 */
	private boolean synchronizedWithTransaction = false;

	private boolean rollbackOnly = false;

	@Nullable
	private Date deadline;

	private int referenceCount = 0;

	private boolean isVoid = false;


	/**
	 * Mark the resource as synchronized with a transaction.
	 */
	public void setSynchronizedWithTransaction(boolean synchronizedWithTransaction) {
		this.synchronizedWithTransaction = synchronizedWithTransaction;
	}

	/**
	 * Return whether the resource is synchronized with a transaction.
	 */
	public boolean isSynchronizedWithTransaction() {
		return this.synchronizedWithTransaction;
	}

	/**
	 * Mark the resource transaction as rollback-only.
	 */
	public void setRollbackOnly() {
		this.rollbackOnly = true;
	}

	/**
	 * Reset the rollback-only status for this resource transaction.
	 * <p>Only really intended to be called after custom rollback steps which
	 * keep the original resource in action, e.g. in case of a savepoint.
	 * @since 5.0
	 * @see org.springframework.transaction.SavepointManager#rollbackToSavepoint
	 */
	public void resetRollbackOnly() {
		this.rollbackOnly = false;
	}

	/**
	 * Return whether the resource transaction is marked as rollback-only.
	 */
	public boolean isRollbackOnly() {
		return this.rollbackOnly;
	}

	/**
	 * Set the timeout for this object in seconds.
	 * @param seconds number of seconds until expiration
	 */
	public void setTimeoutInSeconds(int seconds) {
		setTimeoutInMillis(seconds * 1000L);
	}

	/**
	 * Set the timeout for this object in milliseconds.
	 * @param millis number of milliseconds until expiration
	 */
	public void setTimeoutInMillis(long millis) {
		this.deadline = new Date(System.currentTimeMillis() + millis);
	}

	/**
	 * Return whether this object has an associated timeout.
	 */
	public boolean hasTimeout() {
		return (this.deadline != null);
	}

	/**
	 * Return the expiration deadline of this object.
	 * @return the deadline as Date object
	 */
	@Nullable
	public Date getDeadline() {
		return this.deadline;
	}

	/**
	 * Return the time to live for this object in seconds.
	 * Rounds up eagerly, e.g. 9.00001 still to 10.
	 * @return number of seconds until expiration
	 * @throws TransactionTimedOutException if the deadline has already been reached
	 */
	public int getTimeToLiveInSeconds() {
		double diff = ((double) getTimeToLiveInMillis()) / 1000;
		int secs = (int) Math.ceil(diff);
		checkTransactionTimeout(secs <= 0);
		return secs;
	}

	/**
	 * Return the time to live for this object in milliseconds.
	 * @return number of milliseconds until expiration
	 * @throws TransactionTimedOutException if the deadline has already been reached
	 */
	public long getTimeToLiveInMillis() throws TransactionTimedOutException{
		if (this.deadline == null) {
			throw new IllegalStateException("No timeout specified for this resource holder");
		}
		long timeToLive = this.deadline.getTime() - System.currentTimeMillis();
		checkTransactionTimeout(timeToLive <= 0);
		return timeToLive;
	}

	/**
	 * Set the transaction rollback-only if the deadline has been reached,
	 * and throw a TransactionTimedOutException.
	 */
	private void checkTransactionTimeout(boolean deadlineReached) throws TransactionTimedOutException {
		if (deadlineReached) {
			setRollbackOnly();
			throw new TransactionTimedOutException("Transaction timed out: deadline was " + this.deadline);
		}
	}

	/**
	 * Increase the reference count by one because the holder has been requested
	 * (i.e. someone requested the resource held by it).
	 */
	public void requested() {
		this.referenceCount++;
	}

	/**
	 * Decrease the reference count by one because the holder has been released
	 * (i.e. someone released the resource held by it).
	 */
	public void released() {
		this.referenceCount--;
	}

	/**
	 * Return whether there are still open references to this holder.
	 */
	public boolean isOpen() {
		return (this.referenceCount > 0);
	}

	/**
	 * Clear the transactional state of this resource holder.
	 */
	public void clear() {
		this.synchronizedWithTransaction = false;
		this.rollbackOnly = false;
		this.deadline = null;
	}

	/**
	 * Reset this resource holder - transactional state as well as reference count.
	 */
	@Override
	public void reset() {
		clear();
		this.referenceCount = 0;
	}

	@Override
	public void unbound() {
		this.isVoid = true;
	}

	@Override
	public boolean isVoid() {
		return this.isVoid;
	}

}
