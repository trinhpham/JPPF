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
package org.jppf.server.node.android;

import java.util.List;

import org.jppf.*;
import org.jppf.server.node.AbstractNodeTaskWrapper;
import org.jppf.server.protocol.JPPFTask;

/**
 * Wrapper around a JPPF task used to catch exceptions caused by the task execution.
 * @author Domingos Creado
 * @author Laurent Cohen
 */
class AndroidNodeTaskWrapper extends AbstractNodeTaskWrapper
{
	/**
	 * The JPPF node that runs this task.
	 */
	private final AbstractJPPFAndroidNode node;
	/**
	 * The execution manager.
	 */
	private AndroidNodeExecutionManager executionManager = null;

	/**
	 * Initialize this task wrapper with a specified JPPF task.
	 * @param node the JPPF node that runs this task.
	 * @param task the task to execute within a try/catch block.
	 * @param uuidPath the key to the JPPFContainer for the task's classloader.
	 * @param number the internal number identifying the task for the thread pool.
	 */
	public AndroidNodeTaskWrapper(AbstractJPPFAndroidNode node, JPPFTask task, List<String> uuidPath, long number)
	{
		super(task, uuidPath, number);
		this.node = node;
		this.executionManager = node.getExecutionManager();
	}

	/**
	 * Execute the task within a try/catch block.
	 * @see java.lang.Runnable#run()
	 */
	@Override
    public void run()
	{
		long cpuTime = 0L;
		long elapsedTime = 0L;
		try
		{
			if (node.isNotifying()) node.incrementExecutingCount();
			Thread.currentThread().setContextClassLoader(node.getContainer(uuidPath).getClassLoader());
			long id = Thread.currentThread().getId();
			executionManager.processTaskTimeout(task, number);
			long startTime = System.currentTimeMillis();
			task.run();
			try
			{
				elapsedTime = System.currentTimeMillis() - startTime;
			}
			catch(Throwable ignore)
			{
			}
		}
		catch(JPPFNodeReconnectionNotification t)
		{
			reconnectionNotification = t;
		}
		catch(Throwable t)
		{
			if (t instanceof Exception) task.setException((Exception) t);
			else task.setException(new JPPFException(t));
		}
		finally
		{
			if (reconnectionNotification == null)
			{
				try
				{
					if (node.isNotifying()) node.decrementExecutingCount();
					executionManager.taskEnded(number, cpuTime, elapsedTime, task.getException() != null);
				}
				catch(JPPFNodeReconnectionNotification t)
				{
					reconnectionNotification = t;
				}
			}
			if (reconnectionNotification != null)
			{
				executionManager.setReconnectionNotification(reconnectionNotification);
				executionManager.wakeUp();
			}
		}
	}
}