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

package org.jppf.client.balancer.queue;

import static org.jppf.utils.CollectionUtils.*;

import java.text.ParseException;
import java.util.*;

import org.jppf.client.*;
import org.jppf.client.balancer.*;
import org.jppf.client.submission.SubmissionStatus;
import org.jppf.management.JPPFManagementInfo;
import org.jppf.node.policy.*;
import org.jppf.node.protocol.*;
import org.jppf.scheduling.*;
import org.jppf.server.protocol.JPPFJobSLA;
import org.jppf.utils.JPPFUuid;
import org.slf4j.*;

/**
 * A JPPF queue whose elements are ordered by decreasing priority.
 * @author Laurent Cohen
 */
public class JPPFPriorityQueue extends AbstractJPPFQueue
{
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(JPPFPriorityQueue.class);
  /**
   * Determines whether the debug level is enabled in the logging configuration, without the cost of a method call.
   */
  private static boolean debugEnabled = log.isDebugEnabled();
  /**
   * Comparator for job priority.
   */
  private static final Comparator<Integer> PRIORITY_COMPARATOR = new Comparator<Integer>() {
    @Override
    public int compare(final Integer o1, final Integer o2) {
      return Integer.compare(o2, o1);
    }
  };
  /**
   * An of task bundles, ordered by descending priority.
   */
  private final TreeMap<Integer, List<ClientJob>> priorityMap = new TreeMap<Integer, List<ClientJob>>(PRIORITY_COMPARATOR);
  /**
   * Contains the ids of all queued jobs.
   */
  private final Map<String, ClientJob> jobMap = new HashMap<String, ClientJob>();
  /**
   * The job submission manager
   */
  private final SubmissionManagerClient submissionManager;
  /**
   * Handles the schedule of each job that has one.
   */
  private final JPPFScheduleHandler jobScheduleHandler = new JPPFScheduleHandler("Job Schedule Handler");
  /**
   * Handles the expiration schedule of each job that has one.
   */
  private final JPPFScheduleHandler jobExpirationHandler = new JPPFScheduleHandler("Job Expiration Handler");

  /**
   * Initialize this queue.
   * @param submissionManager reference to submission manager.
   */
  public JPPFPriorityQueue(final SubmissionManagerClient submissionManager)
  {
    this.submissionManager = submissionManager;
  }

  /**
   * Add an object to the queue, and notify all listeners about it.
   * @param bundleWrapper the object to add to the queue.
   */
  @Override
  public void addBundle(final ClientJob bundleWrapper)
  {
    JobSLA sla = bundleWrapper.getSLA();
    final String jobUuid = bundleWrapper.getUuid();
    if (sla.isBroadcastJob() && (bundleWrapper.getBroadcastUUID() == null))
    {
      if (debugEnabled)
      {
        log.debug("before processing broadcast job with id=" + bundleWrapper.getName() + ", uuid=" + jobUuid + ", task count=" + bundleWrapper.getTaskCount());
      }
      processBroadcastJob(bundleWrapper);
      return;
    }
    lock.lock();
    try
    {
      ClientJob other = jobMap.get(jobUuid);
      if (other != null)
      {
        other.merge(bundleWrapper.getTasks(), false);
        if (debugEnabled) log.debug("re-submitting bundle with " + bundleWrapper);
//        bundle.setParameter(BundleParameter.REAL_TASK_COUNT, bundleWrapper.getTaskCount());
        fireQueueEvent(new QueueEvent(this, other, true));
      }
      else
      {
        bundleWrapper.addOnDone(new Runnable()
        {
          @Override
          public void run()
          {
            lock.lock();
            try
            {
              jobMap.remove(jobUuid);
              removeBundle(bundleWrapper);
            }
            finally
            {
              lock.unlock();
            }
          }
        });
        bundleWrapper.setSubmissionStatus(SubmissionStatus.PENDING);
        bundleWrapper.setQueueEntryTime(System.currentTimeMillis());
        bundleWrapper.setJobReceivedTime(bundleWrapper.getQueueEntryTime());
        putInListMap(sla.getPriority(), bundleWrapper, priorityMap);
        putInListMap(getSize(bundleWrapper), bundleWrapper, sizeMap);
        if (debugEnabled) log.debug("adding bundle with " + bundleWrapper);
        handleStartJobSchedule(bundleWrapper);
        handleExpirationJobSchedule(bundleWrapper);
        jobMap.put(jobUuid, bundleWrapper);
        fireQueueEvent(new QueueEvent(this, bundleWrapper, false));
      }
    }
    finally
    {
      lock.unlock();
    }
    if (debugEnabled)
    {
      log.debug("Maps size information: " + formatSizeMapInfo("priorityMap", priorityMap) + " - " + formatSizeMapInfo("sizeMap", sizeMap));
    }
  }

  protected void requeue(final ClientTaskBundle bundle) {
    lock.lock();
    try {
      ClientJob job = bundle.getClientJob();
      if(!jobMap.containsKey(job.getUuid())) throw new IllegalStateException("Job not managed");

      boolean requeue = job.getTaskCount() == 0;
      job.merge(bundle.getTasksL(), false);
      if(requeue)
      {
        putInListMap(job.getSLA().getPriority(), job, priorityMap);
        putInListMap(getSize(job), job, sizeMap);
        fireQueueEvent(new QueueEvent(this, job, true));
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Get the next object in the queue.
   * @param nbTasks the maximum number of tasks to get out of the bundle.
   * @return the most recent object that was added to the queue.
   */
  @Override
  public ClientTaskBundle nextBundle(final int nbTasks)
  {
    Iterator<ClientJob> it = iterator();
    return it.hasNext() ? nextBundle(it.next(), nbTasks) : null;
  }

  /**
   * Get the next object in the queue.
   * @param bundleWrapper the bundle to either remove or extract a sub-bundle from.
   * @param nbTasks       the maximum number of tasks to get out of the bundle.
   * @return the most recent object that was added to the queue.
   */
  @Override
  public ClientTaskBundle nextBundle(final ClientJob bundleWrapper, final int nbTasks)
  {
    final ClientTaskBundle result;
    lock.lock();
    try
    {
      if (debugEnabled)
      {
        log.debug("requesting bundle with " + nbTasks + " tasks, next bundle has " + bundleWrapper.getTaskCount() + " tasks");
      }
      int size = getSize(bundleWrapper);
      removeFromListMap(size, bundleWrapper, sizeMap);
      if (nbTasks >= bundleWrapper.getTaskCount())
      {
        result = bundleWrapper.copy(bundleWrapper.getTaskCount());
        removeBundle(bundleWrapper);
//        bundle.setParameter(BundleParameter.REAL_TASK_COUNT, 0);
      }
      else
      {
        if (debugEnabled) log.debug("removing " + nbTasks + " tasks from bundle");
        result = bundleWrapper.copy(nbTasks);
        int newSize = bundleWrapper.getTaskCount();
        List<ClientJob> list = sizeMap.get(newSize);
        if (list == null)
        {
          list = new ArrayList<ClientJob>();
          //sizeMap.put(newSize, list);
          sizeMap.put(size, list);
        }
        list.add(bundleWrapper);
//        bundle.setParameter(BundleParameter.REAL_TASK_COUNT, bundleWrapper.getTaskCount());
        List<ClientJob> bundleList = priorityMap.get(bundleWrapper.getSLA().getPriority());
        bundleList.remove(bundleWrapper);
        bundleList.add(bundleWrapper);
      }
    }
    finally
    {
      lock.unlock();
    }
    if (debugEnabled)
    {
      log.debug("Maps size information: " + formatSizeMapInfo("priorityMap", priorityMap) + " - " + formatSizeMapInfo("sizeMap", sizeMap));
    }
//    ClientTaskBundle resultJob = (ClientTaskBundle) result.getJob();
    result.setOnRequeue(new Runnable()
    {
      @Override
      public void run()
      {
        requeue(result);
      }
    });
    return result;
  }

  /**
   * Determine whether the queue is empty or not.
   * @return true if the queue is empty, false otherwise.
   */
  @Override
  public boolean isEmpty()
  {
    lock.lock();
    try
    {
      return priorityMap.isEmpty();
    }
    finally
    {
      lock.unlock();
    }
  }

  /**
   * Get the maximum bundle size for the bundles present in the queue.
   * @return the bundle size as an int.
   */
  @Override
  public int getMaxBundleSize()
  {
    lock.lock();
    try
    {
      latestMaxSize = sizeMap.isEmpty() ? latestMaxSize : sizeMap.lastKey();
      return latestMaxSize;
    }
    finally
    {
      lock.unlock();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ClientJob removeBundle(final ClientJob bundleWrapper)
  {
    lock.lock();
    try
    {
      if (debugEnabled) log.debug("removing bundle from queue, jobId=" + bundleWrapper.getName());
      removeFromListMap(bundleWrapper.getSLA().getPriority(), bundleWrapper, priorityMap);
//      jobMap.remove(bundleWrapper.getUuid());
      return null;
    }
    finally
    {
      lock.unlock();
    }
  }

  /**
   * Get an iterator on the task bundles in this queue.
   * @return an iterator.
   * @see Iterable#iterator()
   */
  @Override
  public Iterator<ClientJob> iterator()
  {
    return new BundleIterator(priorityMap, lock);
  }

  /**
   * Process the start schedule specified in the job SLA.
   * @param bundleWrapper the job to process.
   */
  private void handleStartJobSchedule(final ClientJob bundleWrapper)
  {
    JPPFSchedule schedule = bundleWrapper.getSLA().getJobSchedule();
    if (schedule != null)
    {
      bundleWrapper.setPending(true);
      String jobId = bundleWrapper.getName();
      final String uuid = bundleWrapper.getUuid();
      if (debugEnabled) log.debug("found start " + schedule + " for jobId = " + jobId);
      try
      {
        long dt = bundleWrapper.getJobReceivedTime();
        jobScheduleHandler.scheduleAction(uuid, schedule, new JobScheduleAction(bundleWrapper), dt);
        bundleWrapper.addOnDone(new Runnable()
        {
          @Override
          public void run()
          {
            jobScheduleHandler.cancelAction(uuid);
          }
        });
      }
      catch (ParseException e)
      {
        bundleWrapper.setPending(false);
        log.error("Unparseable start date for job id " + jobId + " : date = " + schedule.getDate() +
                ", date format = " + (schedule.getFormat() == null ? "null" : schedule.getFormat()), e);
      }
    }
    else
    {
      bundleWrapper.setPending(false);
    }
  }

  /**
   * Process the expiration schedule specified in the job SLA.
   * @param bundleWrapper the job to process.
   */
  private void handleExpirationJobSchedule(final ClientJob bundleWrapper)
  {
    JPPFSchedule schedule = bundleWrapper.getSLA().getJobExpirationSchedule();
    if (schedule != null)
    {
      String jobId = bundleWrapper.getName();
      final String uuid = bundleWrapper.getUuid();
      if (debugEnabled) log.debug("found expiration " + schedule + " for jobId = " + jobId);
      long dt = bundleWrapper.getJobReceivedTime();
      try
      {
        jobExpirationHandler.scheduleAction(uuid, schedule, new JobExpirationAction(bundleWrapper), dt);
        bundleWrapper.addOnDone(new Runnable()
        {
          @Override
          public void run()
          {
            jobExpirationHandler.cancelAction(uuid);
          }
        });
      }
      catch (ParseException e)
      {
        log.error("Unparsable expiration date for job id " + jobId + " : date = " + schedule.getDate() +
                ", date format = " + (schedule.getFormat() == null ? "null" : schedule.getFormat()), e);
      }
    }
  }

  /**
   * Process the specified broadcast job.
   * <b/>This consists in creating one job per node, each containing the same tasks,
   * and with an execution policy that enforces its execution ont he designated node only.
   * @param bundleWrapper the broadcast job to process.
   */
  private void processBroadcastJob(final ClientJob bundleWrapper)
  {
    JPPFJob bundle = bundleWrapper.getJob();
    List<ChannelWrapper> connections = submissionManager.getAllConnections();
    if (connections.isEmpty())
    {
      bundleWrapper.taskCompleted(null, null);
      return;
    }
    JobSLA sla = bundle.getSLA();
    ExecutionPolicy policy = sla.getExecutionPolicy();
    List<ClientJob> jobList = new ArrayList<ClientJob>(connections.size());

    Set<String> uuidSet = new HashSet<String>();
    for (ChannelWrapper connection : connections)
    {
      JPPFClientConnectionStatus status = connection.getStatus();
      if(status == JPPFClientConnectionStatus.ACTIVE || status == JPPFClientConnectionStatus.EXECUTING)
      {
        if ((policy != null) && !policy.accepts(connection.getSystemInfo())) continue;

        String uuid = connection.getUuid();
        if(uuid != null && uuid.length() > 0 && uuidSet.add(uuid))
        {
          ClientJob newBundle = bundleWrapper.createBroadcastJob(uuid);

          JPPFManagementInfo info = connection.getManagementInfo();
          ExecutionPolicy broadcastPolicy = new Equal("jppf.uuid", true, uuid);
          newBundle.setSLA(((JPPFJobSLA) sla).copy());
          newBundle.setName(bundle.getName() + " [driver: " + info.toString() + ']');
          newBundle.setUuid(new JPPFUuid(JPPFUuid.HEXADECIMAL_CHAR, 32).toString());
          if (debugEnabled) log.debug("Execution policy for job uuid=" + newBundle.getUuid() + " :\n" + broadcastPolicy);
          jobList.add(newBundle);
        }
      }
    }
    if (jobList.isEmpty())
    {
      bundleWrapper.taskCompleted(null, null);
    }
    else
    {
      bundleWrapper.setSubmissionStatus(SubmissionStatus.PENDING);
      for (ClientJob job : jobList) {
        addBundle(job);
      }
    }
  }

  /**
   * Update the priority of the job with the specified uuid.
   * @param jobUuid     the uuid of the job to re-prioritize.
   * @param newPriority the new priority of the job.
   */
  public void updatePriority(final String jobUuid, final int newPriority)
  {
    lock.lock();
    try
    {
      ClientJob job = jobMap.get(jobUuid);
      if (job == null) return;
      int oldPriority = job.getJob().getSLA().getPriority();
      if (oldPriority != newPriority)
      {
        job.getJob().getSLA().setPriority(newPriority);
        removeFromListMap(oldPriority, job, priorityMap);
        putInListMap(newPriority, job, priorityMap);
      }
    }
    finally
    {
      lock.unlock();
    }
  }

  /**
   * Cancel the job with the specified UUID
   * @param jobId the uuid of the job to cancel.
   * @return whether cancellation was successful.
   */
  public boolean cancelJob(final String jobId)
  {
    lock.lock();
    try
    {
      ClientJob job = jobMap.get(jobId);
      if(job == null)
        return false;
      else
        return job.cancel(false);
    }
    finally
    {
      lock.unlock();
    }
  }

  /**
   * Close this queue and all resources it uses.
   */
  public void close()
  {
    lock.lock();
    try
    {
      jobScheduleHandler.clear(true);
      jobExpirationHandler.clear(true);
    }
    finally
    {
      lock.unlock();
    }
  }
}
