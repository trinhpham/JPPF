/*
 * JPPF.
 * Copyright (C) 2005-2012 JPPF Team.
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

package org.jppf.jca.work.submission;

import static org.jppf.client.submission.SubmissionStatus.PENDING;

import java.util.*;

import org.jppf.client.*;
import org.jppf.client.event.SubmissionStatusListener;
import org.jppf.client.submission.*;
import org.jppf.jca.work.JPPFJcaClient;
import org.slf4j.*;

/**
 * This task provides asynchronous management of tasks submitted through the resource adapter.
 * It relies on a queue where job are first added, then submitted when a driver connection becomes available.
 * It also provides methods to check the status of a submission and retrieve the results.
 * @author Laurent Cohen
 */
public class JcaSubmissionManager extends AbstractSubmissionManager
{
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(JcaSubmissionManager.class);
  /**
   * Determines whether the debug level is enabled in the logging configuration, without the cost of a method call.
   */
  private static boolean debugEnabled = log.isDebugEnabled();
  /**
   * Mapping of submissions to their submission id.
   */
  private Map<String, JPPFResultCollector> submissionMap = new Hashtable<String, JPPFResultCollector>();

  /**
   * Initialize this submission worker with the specified JPPF client.
   * @param client the JPPF client that manages connections to the JPPF drivers.
   */
  public JcaSubmissionManager(final JPPFJcaClient client)
  {
    this.client = client;
  }

  /**
   * Add a task submission to the execution queue.
   * @param job encapsulation of the execution data.
   * @return the unique id of the submission.
   */
  @Override
  public String submitJob(final JPPFJob job)
  {
    return submitJob(job, null);
  }

  /**
   * Add a task submission to the execution queue.
   * @param job encapsulation of the execution data.
   * @param listener an optional listener to receive submission status change notifications, may be null.
   * @return the unique id of the submission.
   */
  @Override
  public String submitJob(final JPPFJob job, final SubmissionStatusListener listener)
  {
    int count = job.getTasks().size();
    JPPFResultCollector submission = new JPPFResultCollector(job);
    if (debugEnabled) log.debug("adding new submission: jobId=" + job.getName() + ", nbTasks=" + count + ", submission id=" + submission.getId());
    if (listener != null) submission.addSubmissionStatusListener(listener);
    job.setResultListener(submission);
    submission.setStatus(PENDING);
    execQueue.add(job);
    submissionMap.put(submission.getId(), submission);
    wakeUp();
    return submission.getId();
  }

  /**
   * Add an existing submission back into the execution queue.
   * @param job encapsulation of the execution data.
   * @return the unique id of the submission.
   */
  @Override
  public String resubmitJob(final JPPFJob job)
  {
    JPPFResultCollector submission = (JPPFResultCollector) job.getResultListener();
    submission.setStatus(PENDING);
    execQueue.add(job);
    submissionMap.put(submission.getId(), submission);
    wakeUp();
    return submission.getId();
  }

  /**
   * Get a submission given its id, without removing it from this submission manager.
   * @param id the id of the submission to find.
   * @return the submission corresponding to the id, or null if the submission could not be found.
   */
  public JPPFResultCollector peekSubmission(final String id)
  {
    return submissionMap.get(id);
  }

  /**
   * Get a submission given its id, and remove it from this submission manager.
   * @param id the id of the submission to find.
   * @return the submission corresponding to the id, or null if the submission could not be found.
   */
  public JPPFResultCollector pollSubmission(final String id)
  {
    return submissionMap.remove(id);
  }

  /**
   * Get the ids of all currently available submissions.
   * @return a collection of ids as strings.
   */
  public Collection<String> getAllSubmissionIds()
  {
    return Collections.unmodifiableSet(submissionMap.keySet());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected JobSubmission createSubmission(final JPPFJob job, final AbstractJPPFClientConnection c, final boolean locallyExecuting)
  {
    return new JcaJobSubmission(job, c, locallyExecuting, this);
  }
}