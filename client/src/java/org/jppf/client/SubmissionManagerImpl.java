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

package org.jppf.client;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.jppf.utils.*;
import org.slf4j.*;

/**
 * This task provides asynchronous management of tasks submitted through the resource adapter.
 * It relies on a queue where job are first added, then submitted when a driver connection becomes available.
 * It also provides methods to check the status of a submission and retrieve the results.
 * @author Laurent Cohen
 */
public class SubmissionManagerImpl extends ThreadSynchronization implements SubmissionManager
{
	/**
	 * Logger for this class.
	 */
	private static Logger log = LoggerFactory.getLogger(SubmissionManagerImpl.class);
	/**
	 * Determines whether the debug level is enabled in the logging configuration, without the cost of a method call.
	 */
	private static boolean debugEnabled = log.isDebugEnabled();
	/**
	 * Maximum wait time in milliseconds in the the submission manager loop.
	 */
	private static final long MAX_WAIT_MILLIS = JPPFConfiguration.getProperties().getLong("jppf.submission.manager.maxwait.millis", 0L);
	/**
	 * Maximum wait time in milliseconds in the the submission manager loop.
	 */
	private static final int MAX_WAIT_NANOS = JPPFConfiguration.getProperties().getInt("jppf.submission.manager.maxwait.nanos", 100000);
	/**
	 * The queue of submissions pending execution.
	 */
	private ConcurrentLinkedQueue<JPPFJob> execQueue = new ConcurrentLinkedQueue<JPPFJob>();
	/**
	 * The JPPF client that manages connections to the JPPF drivers.
	 */
	JPPFClient client = null;

	/**
	 * Initialize this submission manager with the specified JPPF client.
	 * @param client the JPPF client that manages connections to the JPPF drivers.
	 * JPPF submissions.
	 */
	public SubmissionManagerImpl(JPPFClient client)
	{
		this.client = client;
	}

	/**
	 * Run the loop of this submission manager, watching for the queue and starting a job
	 * when the queue has one and a connnection is available.
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run()
	{
		while (!isStopped())
		{
			Pair<Boolean, Boolean> execFlags = null;
			while ((execQueue.isEmpty() || !(execFlags = client.handleAvailableConnection()).first()) && !isStopped())
			{
				goToSleep(MAX_WAIT_MILLIS, MAX_WAIT_NANOS);
			}
			if (isStopped()) break;
			synchronized(this)
			{
				JPPFJob job = execQueue.poll();
				if (debugEnabled) log.debug("submiting jobId=" + job.getId());
				JPPFClientConnectionImpl c = (JPPFClientConnectionImpl) client.getClientConnection(true);
				if (c != null) c.getTaskServerConnection().setStatus(JPPFClientConnectionStatus.EXECUTING);
				JobSubmission submission = new JobSubmissionImpl(job, c, this, execFlags.second());
				client.getExecutor().submit(submission);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public String submitJob(JPPFJob job)
	{
		if (debugEnabled) log.debug("adding new submission: jobId=" + job.getId());
		execQueue.offer(job);
		wakeUp();
		return job.getId();
	}

	/**
	 * {@inheritDoc}
	 */
	public String submitJob(JPPFJob job, SubmissionStatusListener listener)
	{
		return submitJob(job);
	}

	/**
	 * {@inheritDoc}
	 */
	public String resubmitJob(JPPFJob job)
	{
		if (debugEnabled) log.debug("resubmitting job with id=" + job.getId());
		execQueue.offer(job);
		wakeUp();
		return job.getId();
	}
}