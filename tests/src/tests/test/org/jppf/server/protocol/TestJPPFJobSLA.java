/*
 * JPPF.
 * Copyright (C) 2005-2017 JPPF Team.
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

package test.org.jppf.server.protocol;

import static org.junit.Assert.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.*;

import org.jppf.client.*;
import org.jppf.client.event.*;
import org.jppf.job.*;
import org.jppf.management.*;
import org.jppf.management.forwarding.JPPFNodeForwardingMBean;
import org.jppf.node.policy.*;
import org.jppf.node.protocol.*;
import org.jppf.scheduling.JPPFSchedule;
import org.jppf.server.job.management.DriverJobManagementMBean;
import org.jppf.utils.*;
import org.jppf.utils.Operator;
import org.jppf.utils.streams.StreamUtils;
import org.junit.*;

import test.org.jppf.test.setup.*;
import test.org.jppf.test.setup.common.*;

/**
 * Unit tests for {@link org.jppf.node.protocol.JobSLA JobSLA}.
 * In this class, we test that the behavior is the expected one, from the client point of view,
 * as specified in the job SLA.
 * @author Laurent Cohen
 */
public class TestJPPFJobSLA extends Setup1D2N1C {
  /**
   * A "short" duration for this test.
   */
  private static final long TIME_SHORT = 750L;
  /**
   * A "long" duration for this test.
   */
  private static final long TIME_LONG = 3000L;
  /**
   * A the date format used in the tests.
   */
  private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

  /**
   * @throws Exception if any error occurs.
   */
  @After
  public void instanceCleanup() throws Exception {
    final JMXDriverConnectionWrapper jmx = BaseSetup.getJMXConnection();
    final String driverState = (String) jmx.invoke("org.jppf:name=debug,type=driver", "dumpQueueDetails");
    if ((driverState !=  null) && !driverState.trim().isEmpty()) {
      printOut("-------------------- driver state --------------------");
      printOut(driverState);
    }
  }

  /**
   * Simply test that a job does expires at a specified date.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  public void testJobExpirationAtDate() throws Exception {
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, 1, SimpleTask.class, TIME_LONG);
    final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
    final Date date = new Date(System.currentTimeMillis() + TIME_SHORT);
    job.getSLA().setJobExpirationSchedule(new JPPFSchedule(sdf.format(date), DATE_FORMAT));
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), 1);
    final Task<?> task = results.get(0);
    assertNull(task.getResult());
  }

  /**
   * Test that a job does not expires at a specified date, because it completes before that date.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  public void testJobExpirationAtDateTooLate() throws Exception {
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, 1, SimpleTask.class, TIME_SHORT);
    final SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
    final Date date = new Date(System.currentTimeMillis() + TIME_LONG);
    job.getSLA().setJobExpirationSchedule(new JPPFSchedule(sdf.format(date), DATE_FORMAT));
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), 1);
    final Task<?> task = results.get(0);
    assertNotNull(task.getResult());
    assertEquals(BaseTestHelper.EXECUTION_SUCCESSFUL_MESSAGE, task.getResult());
  }

  /**
   * Simply test that a job does expires after a specified delay.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  public void testJobExpirationAfterDelay() throws Exception {
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, 1, SimpleTask.class, TIME_LONG);
    job.getSLA().setJobExpirationSchedule(new JPPFSchedule(TIME_SHORT));
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), 1);
    final Task<?> task = results.get(0);
    assertNull(task.getResult());
  }

  /**
   * Test that a job does not expire after a specified delay, because it completes before that.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  public void testJobExpirationAfterDelayTooLate() throws Exception {
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, 1, SimpleTask.class, TIME_SHORT);
    job.getSLA().setJobExpirationSchedule(new JPPFSchedule(TIME_LONG));
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), 1);
    final Task<?> task = results.get(0);
    assertNotNull(task.getResult());
    assertEquals(BaseTestHelper.EXECUTION_SUCCESSFUL_MESSAGE, task.getResult());
  }

  /**
   * Simply test that a suspended job does expires after a specified delay.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=5000)
  public void testSuspendedJobExpiration() throws Exception {
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, 1, SimpleTask.class, TIME_LONG);
    job.getSLA().setSuspended(true);
    job.getSLA().setJobExpirationSchedule(new JPPFSchedule(TIME_SHORT));
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), 1);
    final Task<?> task = results.get(0);
    assertNull(task.getResult());
  }

  /**
   * Test that a job queued in the client does not expire there.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testMultipleJobsExpiration() throws Exception {
    final JPPFJob job1 = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName() + "-1", false, false, 1, SimpleTask.class, TIME_LONG);
    job1.getSLA().setJobExpirationSchedule(new JPPFSchedule(TIME_SHORT));
    final JPPFJob job2 = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName() + "-2", false, false, 1, SimpleTask.class, TIME_SHORT);
    job2.getSLA().setJobExpirationSchedule(new JPPFSchedule(TIME_LONG));
    client.submitJob(job1);
    client.submitJob(job2);
    List<Task<?>> results = job1.awaitResults();
    assertNotNull(results);
    assertEquals(results.size(), 1);
    Task<?> task = results.get(0);
    assertNull(task.getResult());
    results = job2.awaitResults();
    assertNotNull(results);
    assertEquals(results.size(), 1);
    task = results.get(0);
    assertNotNull(task.getResult());
    assertEquals(BaseTestHelper.EXECUTION_SUCCESSFUL_MESSAGE, task.getResult());
  }

  /**
   * Test that a job is not cancelled when the client connection is closed
   * and <code>JPPFJob.getSLA().setCancelUponClientDisconnect(false)</code> has been set.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testCancelJobUponClientDisconnect() throws Exception {
    printOut("%s() configuration: %s", ReflectionUtils.getCurrentMethodName(), JPPFConfiguration.getProperties());
    final String fileName = "testCancelJobUponClientDisconnect";
    final File f = new File(fileName + ".tmp");
    f.deleteOnExit();
    try {
      assertFalse(f.exists());
      final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), false, false, 1, FileTask.class, fileName, false);
      job.getSLA().setCancelUponClientDisconnect(false);
      client.submitJob(job);
      Thread.sleep(1000L);
      client.close();
      Thread.sleep(2000L);
      assertTrue(f.exists());
    } catch(final Exception e) {
      e.printStackTrace();
      throw e;
    } finally {
      f.delete();
      client = BaseSetup.createClient(null);
    }
  }

  /**
   * Test that a job with a higher priority is executed before a job with a smaller priority.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testJobPriority() throws Exception {
    final int nbJobs = 3;
    JPPFConnectionPool pool = null;
    try {
      while ((pool = client.getConnectionPool()) == null) Thread.sleep(10L);
      pool.setJMXPoolSize(nbJobs);
      final JPPFJob[] jobs = new JPPFJob[nbJobs];
      final ExecutionPolicy policy = new Equal("jppf.node.uuid", false, "n1");
      for (int i=0; i<nbJobs; i++) {
        jobs[i] = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName() + i, false, false, 1, LifeCycleTask.class, 750L);
        jobs[i].getSLA().setPriority(i);
        jobs[i].getSLA().setExecutionPolicy(policy);
      }
      for (int i=0; i<nbJobs; i++) {
        client.submitJob(jobs[i]);
        if (i == 0) Thread.sleep(500L);
      }
      final List<List<Task<?>>> results = new ArrayList<>(nbJobs);
      for (int i=0; i<nbJobs; i++) results.add(jobs[i].awaitResults());
      final LifeCycleTask t1 = (LifeCycleTask) results.get(1).get(0);
      assertNotNull(t1);
      final LifeCycleTask t2 = (LifeCycleTask) results.get(2).get(0);
      assertNotNull(t2);
      assertTrue("3rd job (start=" + t2.getStart() + ") should have started before the 2nd (start=" + t1.getStart() + ")", t2.getStart() < t1.getStart());
    } finally {
      if (pool != null) pool.setJMXPoolSize(1);
    }
  }

  /**
   * Test that a job is only sent to the server according to its execution policy.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  public void testJobExecutionPolicy() throws Exception {
    final int nbTasks = 10;
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, nbTasks, LifeCycleTask.class);
    job.getSLA().setExecutionPolicy(new Equal("jppf.node.uuid", false, "n2"));
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), nbTasks);
    for (final Task<?> t: results) {
      final LifeCycleTask task = (LifeCycleTask) t;
      assertTrue("n2".equals(task.getNodeUuid()));
      assertNotNull(task.getResult());
      assertEquals(BaseTestHelper.EXECUTION_SUCCESSFUL_MESSAGE, task.getResult());
    }
  }

  /**
   * Test that a job is only executed on one node at a time.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  @Ignore
  public void testJobMaxNodes() throws Exception {
    final int nbTasks = 5 * BaseSetup.nbNodes();
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, nbTasks, LifeCycleTask.class, 250L);
    job.getSLA().setMaxNodes(1);
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), nbTasks);
    // check that no 2 tasks were executing at the same time on different nodes
    for (int i=0; i<results.size()-1; i++) {
      final LifeCycleTask t1 = (LifeCycleTask) results.get(i);
      final Range<Double> r1 = new Range<>(t1.getStart(), t1.getStart() + t1.getElapsed());
      for (int j=i+1; j<results.size(); j++) {
        final LifeCycleTask t2 = (LifeCycleTask) results.get(j);
        final Range<Double> r2 = new Range<>(t2.getStart(), t2.getStart() + t2.getElapsed());
        assertFalse("r1=" + r1 + ", r2=" + r2 + ", uuid1=" + t1.getNodeUuid() + ", uuid2=" + t2.getNodeUuid(),
            r1.intersects(r2, false) && !t1.getNodeUuid().equals(t2.getNodeUuid()));
      }
    }
  }

  /**
   * Test that a job is executed on both nodes.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  @Ignore
  public void testJobMaxNodes2() throws Exception {
    final int nbTasks = 5 * BaseSetup.nbNodes();
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, nbTasks, LifeCycleTask.class, 250L);
    job.getSLA().setMaxNodes(2);
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    assertEquals(results.size(), nbTasks);
    boolean found = false;
    // check that at least 2 tasks were executing at the same time on different nodes
    for (int i=0; i<results.size()-1; i++) {
      final LifeCycleTask t1 = (LifeCycleTask) results.get(i);
      final Range<Double> r1 = new Range<>(t1.getStart(), t1.getStart() + t1.getElapsed());
      for (int j=i+1; j<results.size(); j++) {
        final LifeCycleTask t2 = (LifeCycleTask) results.get(j);
        final Range<Double> r2 = new Range<>(t2.getStart(), t2.getStart() + t2.getElapsed());
        if (r1.intersects(r2) && !t1.getNodeUuid().equals(t2.getNodeUuid())) {
          found = true;
          break;
        }
      }
      if (found) break;
    }
    assertTrue(found);
  }

  /**
   * Test that a broadcast job is executed on all nodes.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=8000)
  public void testBroadcastJob() throws Exception {
    final String suffix = "node-";
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, true, 1, FileTask.class, suffix, true);
    job.getSLA().setMaxNodes(2);
    client.submitJob(job);
    for (int i=1; i<=2; i++) {
      final File file = new File("node-n" + i + ".tmp");
      try {
        assertTrue("file '" + file + "' does not exist", file.exists());
      } finally {
        if (file.exists()) file.delete();
      }
    }
  }

  /**
   * Test that a broadcast job is executed on all nodes,
   * even though another job is already executing at the time it is submitted.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=15000)
  public void testBroadcastJob2() throws Exception {
    final JPPFConnectionPool pool = client.awaitWorkingConnectionPool();
    try {
      pool.setSize(2);
      pool.awaitConnections(Operator.EQUAL, 2, 5000L, JPPFClientConnectionStatus.workingStatuses());
      final String methodName = ReflectionUtils.getCurrentMethodName();
      final JPPFJob job1 = BaseTestHelper.createJob(methodName + "-normal", false, false, 10, LifeCycleTask.class, 500L);
      job1.getSLA().setPriority(1000);
      final String suffix = "broadcast-node-";
      final JPPFJob job2 = BaseTestHelper.createJob(methodName + "-broadcast", false, true, 1, FileTask.class, suffix, true);
      job2.getSLA().setPriority(-1000);
      client.submitJob(job1);
      Thread.sleep(500L);
      client.submitJob(job2);
      job1.awaitResults();
      job2.awaitResults();
      for (int i=1; i<=2; i++) {
        final File file = new File(suffix + "n" + i + ".tmp");
        try {
          assertTrue("file '" + file + "' does not exist", file.exists());
        } finally {
          if (file.exists()) file.delete();
        }
      }
    } finally {
      pool.setSize(1);
      pool.awaitConnections(Operator.EQUAL, 1, 5000L, JPPFClientConnectionStatus.workingStatuses());
    }
  }

  /**
   * Test that a broadcast job is not executed when no node is available.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=5000)
  public void testBroadcastJobNoNodeAvailable() throws Exception {
    final String suffix = "node-";
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, true, 1, FileTask.class, suffix, true);
    job.getSLA().setMaxNodes(2);
    job.getSLA().setExecutionPolicy(new Equal("jppf.uuid", false, "no node has this as uuid!"));
    client.submitJob(job);
    for (int i=1; i<=2; i++) {
      final File file = new File("node-n" + i + ".tmp");
      try {
        assertFalse("file '" + file + "' exists but shouldn't", file.exists());
      } finally {
        if (file.exists()) file.delete();
      }
    }
  }

  /**
   * Test that a job is not resubmitted when the SLA flag {@code applyMaxResubmitsUponNoError} is true
   * and {@code maxTaskResubmits} is set to 0.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  @Ignore
  public void testApplyMaxResubmitsUponNodeError() throws Exception {
    try {
      final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentClassAndMethod(), true, false, 1, LifeCycleTask.class, 15000L);
      final ExecutionPolicy n1Policy = new Equal("jppf.uuid", false, "n1");
      job.getSLA().setExecutionPolicy(n1Policy);
      job.getSLA().setMaxTaskResubmits(0);
      job.getSLA().setApplyMaxResubmitsUponNodeError(true);
      JPPFConnectionPool pool;
      while ((pool = client.getConnectionPool()) == null) Thread.sleep(10L);
      final JMXDriverConnectionWrapper jmx = pool.getJmxConnection();
      final DriverJobManagementMBean jobManager = jmx.getJobManager();
      final JPPFNodeForwardingMBean forwarder = jmx.getNodeForwarder();
      // restart the node upon first dispatch of the job to this node
      final NotificationListener listener = new NotificationListener() {
        @Override
        public synchronized void handleNotification(final Notification notification, final Object handback) {
          final JobNotification jobNotif = (JobNotification) notification;
          if (jobNotif.getEventType() == JobEventType.JOB_DISPATCHED) {
            try {
              Thread.sleep(500L);
              forwarder.forwardInvoke(new UuidSelector("n1"), JPPFNodeAdminMBean.MBEAN_NAME, "restart");
            } catch (@SuppressWarnings("unused") final Exception ignore) {
              //ignore.printStackTrace();
            }
          }
        }
      };
      jobManager.addNotificationListener(listener, null, null);
      final List<Task<?>> results = client.submitJob(job);
      assertNotNull(results);
      assertEquals(1, results.size());
      final LifeCycleTask task = (LifeCycleTask) results.get(0);
      assertNull(task.getResult());
      assertNull(task.getThrowable());
      assertNull(task.getNodeUuid());
    } finally {
      BaseSetup.checkDriverAndNodesInitialized(1, 2);
    }
  }

  /**
   * Test that results are returned according to the SendNodeResultsStrategy specified in the SLA.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=5000)
  public void testSendNodeResultsStrategy() throws Exception {
    checkSendResultsStrategy(ReflectionUtils.getCurrentMethodName(), SendResultsStrategyConstants.NODE_RESULTS, 4);
  }

  /**
   * Test that results are returned according to the SendAllResultsStrategy specified in the SLA.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=5000)
  public void testSendAllResultsStrategy() throws Exception {
    checkSendResultsStrategy(ReflectionUtils.getCurrentMethodName(), SendResultsStrategyConstants.ALL_RESULTS, 1);
  }

  /**
   * Test that results are returned according to the default strategy (SendNodeResultsStrategy).
   * @throws Exception if any error occurs.
   */
  @Test(timeout=5000)
  public void testDefaultSendResultsStrategy() throws Exception {
    checkSendResultsStrategy(ReflectionUtils.getCurrentMethodName(), null, 4);
  }

  /**
   * Test that results are returned according to the specified strategy.
   * @param jobName the name the job to execute.
   * @param strategyName the name of the strategy to test.
   * @param expectedReturnedCount the expected number of 'job returned' notifications.
   * @throws Exception if any error occurs.
   */
  private static void checkSendResultsStrategy(final String jobName, final String strategyName, final int expectedReturnedCount) throws Exception {
    final int nbTasks = 20;
    final JPPFJob job = BaseTestHelper.createJob(jobName, true, false, nbTasks, LifeCycleTask.class, 1L);
    final AtomicInteger returnedCount = new AtomicInteger(0);
    final JobListener listener = new JobListenerAdapter() {
      @Override
      public synchronized void jobReturned(final JobEvent event) {
        returnedCount.incrementAndGet();
      }
    };
    job.addJobListener(listener);
    job.getSLA().setResultsStrategy(strategyName);
    client.submitJob(job);
    assertEquals(expectedReturnedCount, returnedCount.get());
  }

  /**
   * A task that creates a file.
   */
  public static class FileTask extends AbstractTask<String> {
    /**
     * Explicit serialVersionUID.
     */
    private static final long serialVersionUID = 1L;
    /** */
    private final String filePath;
    /** */
    private final boolean appendNodeSuffix;

    /**
     * Initialize this task with the specified file path.
     * @param filePath the path of the file to create.
     * @param appendNodeSuffix <code>true</code> to append the node name to the file's name, <code>false</code> otherwise.
     */
    public FileTask(final String filePath, final boolean appendNodeSuffix) {
      this.filePath = filePath;
      this.appendNodeSuffix = appendNodeSuffix;
    }

    @Override
    public void run() {
      try {
        String name = filePath;
        if (appendNodeSuffix) name = name + JPPFConfiguration.getProperties().getString("jppf.node.uuid");
        name = name + ".tmp";
        printOut("creating file '%s'", name);
        final File f = new File(name);
        Thread.sleep(2000L);
        final Writer writer = new FileWriter(f);
        StreamUtils.closeSilent(writer);
      } catch (final Exception e) {
        setThrowable(e);
        e.printStackTrace();
      }
    }
  }
}
