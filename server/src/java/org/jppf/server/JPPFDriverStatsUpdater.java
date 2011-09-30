/*
 * JPPF.
 * Copyright (C) 2005-2011 JPPF Team.
 * http://www.jppf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jppf.server;

import org.jppf.utils.*;


/**
 * Instances of this class are used to collect statistics on the JPPF server.
 * @author Laurent Cohen
 */
public final class JPPFDriverStatsUpdater implements JPPFDriverListener
{
	/**
	 * The object that holds the stats.
	 */
	private JPPFStats stats = new JPPFStats();
	
	/**
	 * Called to notify that a new client is connected to he JPPF server.
	 */
	@Override
    public synchronized void newClientConnection()
	{
		stats.setNbClients(stats.getNbClients() + 1);
		if (stats.getNbClients() > stats.getMaxClients()) stats.setMaxClients(stats.getMaxClients() + 1);
	}

	/**
	 * Called to notify that a new client has disconnected from he JPPF server.
	 */
	@Override
    public synchronized void clientConnectionClosed()
	{
		stats.setNbClients(stats.getNbClients() - 1);
	}

	/**
	 * Called to notify that a new node is connected to he JPPF server.
	 */
	@Override
    public synchronized void newNodeConnection()
	{
		stats.setNbNodes(stats.getNbNodes() + 1);
		if (stats.getNbNodes() > stats.getMaxNodes()) stats.setMaxNodes(stats.getMaxNodes() + 1);
	}

	/**
	 * Called to notify that a new node is connected to he JPPF server.
	 */
	@Override
    public synchronized void nodeConnectionClosed()
	{
		stats.setNbNodes(stats.getNbNodes() - 1);
	}

	/**
	 * Called to notify that a task was added to the queue.
	 * @param count the number of tasks that have been added to the queue.
	 */
	@Override
    public synchronized void taskInQueue(int count)
	{
		QueueStats queue = stats.getTaskQueue();
		StatsSnapshot sizes = queue.getSizes();
		//queue.setQueueSize(queue.getQueueSize() + count);
		sizes.setLatest(sizes.getLatest() + count);
		if (sizes.getLatest() > sizes.getMax()) sizes.setMax(sizes.getLatest());
	}

	/**
	 * Called to notify that a task was removed from the queue.
	 * @param count the number of tasks that have been removed from the queue.
	 * @param time the time the task remained in the queue.
	 */
	@Override
    public synchronized void taskOutOfQueue(int count, long time)
	{
		QueueStats queue = stats.getTaskQueue();
		StatsSnapshot sizes = queue.getSizes();
		//queue.setQueueSize(queue.getQueueSize() - count);
		sizes.setLatest(sizes.getLatest() - count);
		//queue.setTotalQueued(queue.getTotalQueued() + count);
		sizes.setTotal(sizes.getTotal() + count);
		queue.getTimes().newValues(time, count, sizes.getTotal());
	}
	
	/**
	 * Called when a task execution has completed.
	 * @param count the number of tasks that have been executed.
	 * @param time the time it took to execute the task, including transport to and from the node.
	 * @param remoteTime the time it took to execute the tasks in the node only.
	 * @param size the size in bytes of the bundle that was sent to the node.
	 */
	@Override
    public synchronized void taskExecuted(int count, long time, long remoteTime, long size)
	{
		stats.setTotalTasksExecuted(stats.getTotalTasksExecuted() + count);
		stats.getExecution().newValues(time, count, stats.getTotalTasksExecuted());
		stats.getNodeExecution().newValues(remoteTime, count, stats.getTotalTasksExecuted());
		stats.getTransport().newValues(time - remoteTime, count, stats.getTotalTasksExecuted());
		stats.setFootprint(stats.getFootprint() + size);
	}

	/**
	 * Get the stats maintained by this updater.
	 * @return a <code>JPPFStats</code> instance.
	 */
	public synchronized JPPFStats getStats()
	{
		return stats.makeCopy();
	}

	/**
	 * Get the current number of nodes connected to the server.
	 * @return the numbe rof nodes as an int.
	 */
	public int getNbNodes()
	{
		return stats.getNbNodes();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
    public void reset()
	{
		stats.reset();
	}
}
