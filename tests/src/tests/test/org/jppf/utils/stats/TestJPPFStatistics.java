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

package test.org.jppf.utils.stats;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.Callable;

import org.jppf.client.JPPFJob;
import org.jppf.job.JobEventType;
import org.jppf.management.*;
import org.jppf.node.protocol.*;
import org.jppf.utils.ReflectionUtils;
import org.jppf.utils.stats.*;
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import test.org.jppf.test.setup.*;
import test.org.jppf.test.setup.common.*;

/**
 * Unit tests for the {@link ScriptedTask} class.
 * @author Laurent Cohen
 */
public class TestJPPFStatistics extends Setup1D1N1C {
  /** */
  private static long WAIT_TIME = 3000L;
  /** */
  @Rule
  public TestWatcher testJPPFStatisticsWatcher = new TestWatcher() {
    @Override
    protected void finished(final Description description) {
      final String message = String.format("end of method %s()", description.getMethodName());
      BaseTestHelper.printToAll(client, true, true, true, true, message);
    }
  };

  /**
   * Test that the latest queue size is zero, after a job has completed and during whose execution the node was restarted.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=15000)
  public void testLatestQueueTaskCountUponNodeRestart() throws Exception {
    final int nbTasks = 2;
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentClassAndMethod(), false, false, nbTasks, LifeCycleTask.class, 2000L);
    final JMXDriverConnectionWrapper jmx = BaseSetup.getJMXConnection();
    jmx.resetStatistics();
    client.submitJob(job);
    Thread.sleep(1000L);
    jmx.getNodeForwarder().restart(NodeSelector.ALL_NODES);
    final List<Task<?>> results = job.awaitResults();
    assertNotNull(results);
    BaseTestHelper.waitForTest(new TaskAndJobCountTester(jmx), WAIT_TIME);
  }

  /**
   * Test that the latest queue size is zero, after a job has completed and whose tasks resubmit themselves once.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testLatestQueueTaskCountUponTaskResubmit() throws Exception {
    final int nbTasks = 2;
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentClassAndMethod(), false, false, nbTasks, CustomTask.class, 100L);
    final JMXDriverConnectionWrapper jmx = BaseSetup.getJMXConnection();
    jmx.resetStatistics();
    job.getSLA().setMaxTaskResubmits(1);
    client.submitJob(job);
    final List<Task<?>> results = job.awaitResults();
    assertNotNull(results);
    BaseTestHelper.waitForTest(new TaskAndJobCountTester(jmx), WAIT_TIME);
  }

  /**
   * Test that the latest task count and job count are zero after the client is closed with the job's cancelUponClientDisconnect = true.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testTaskAndJobCountUponClientClose() throws Exception {
    final int nbTasks = 1;
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentClassAndMethod(), false, false, nbTasks, LifeCycleTask.class, 2000L);
    JMXDriverConnectionWrapper jmx = BaseSetup.getJMXConnection();
    jmx.resetStatistics();
    job.getSLA().setCancelUponClientDisconnect(true);
    job.getSLA().setSuspended(true);
    final AwaitJobNotificationListener listener = new AwaitJobNotificationListener(client, JobEventType.JOB_QUEUED);
    client.submitJob(job);
    listener.await();
    try {
      client.close();
    } finally {
      client = BaseSetup.createClient(null);
    }
    jmx = BaseSetup.getJMXConnection();
    BaseTestHelper.waitForTest(new TaskAndJobCountTester(jmx), WAIT_TIME);
  }

  /**
   * Test that the latest task count and job count are zero after the client is closed with the job's cancelUponClientDisconnect = false.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testTaskAndJobCountUponClientCloseWithoutCancel() throws Exception {
    final int nbTasks = 1;
    final long duration = 3000L;
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentClassAndMethod(), false, false, nbTasks, LifeCycleTask.class, duration);
    JMXDriverConnectionWrapper jmx = BaseSetup.getJMXConnection();
    jmx.resetStatistics();
    job.getSLA().setCancelUponClientDisconnect(false);
    final AwaitJobNotificationListener listener = new AwaitJobNotificationListener(client, JobEventType.JOB_DISPATCHED);
    client.submitJob(job);
    final long start = System.nanoTime();
    listener.await();
    try {
      client.close();
    } finally {
      client = BaseSetup.createClient(null);
    }
    jmx = BaseSetup.getJMXConnection();
    final long elapsed = (System.nanoTime() - start) / 1_000_000L;
    final long waitTime = duration - elapsed + 500L;
    // make sure the job has time to complete
    if (waitTime > 0L) Thread.sleep(waitTime);
    BaseTestHelper.waitForTest(new TaskAndJobCountTester(jmx), WAIT_TIME);
  }

  /**
   * Test that the latest task count and job count are zero after the job completes normally.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testTaskAndJobCountUponCompletion() throws Exception {
    final int nbTasks = 1;
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentClassAndMethod(), true, false, nbTasks, LifeCycleTask.class, 100L);
    final JMXDriverConnectionWrapper jmx = BaseSetup.getJMXConnection();
    jmx.resetStatistics();
    final List<Task<?>> results = client.submitJob(job);
    assertNotNull(results);
    BaseTestHelper.waitForTest(new TaskAndJobCountTester(jmx), WAIT_TIME);
  }

  /**
   * Test that the latest task count and job count are zero after the job has been cancelled.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=10000)
  public void testTaskAndJobCountUponCancel() throws Exception {
    final int nbTasks = 1;
    final String notif = "task notif";
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentClassAndMethod(), false, false, nbTasks, LifeCycleTask.class, 2000L, true, notif);
    final JMXDriverConnectionWrapper jmx = BaseSetup.getJMXConnection();
    jmx.resetStatistics();
    final AwaitTaskNotificationListener taskListener = new AwaitTaskNotificationListener(client, notif);
    client.submitJob(job);
    taskListener.await();
    jmx.cancelJob(job.getUuid());
    final List<Task<?>> results = job.awaitResults();
    assertNotNull(results);
    BaseTestHelper.waitForTest(new TaskAndJobCountTester(jmx), WAIT_TIME);
  }

  /**
   * A task that resubmits itself. Be careful to call job.getSLA().setMaxTaskResubmit(1) or with another appropriate value.
   */
  public static class CustomTask extends LifeCycleTask {
    /**
     * Explicit serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Initialize this task.
     * @param duration duration of the task in ms.
     */
    public CustomTask(final long duration) {
      super(duration);
    }

    @Override
    public void run() {
      super.run();
      this.setResubmit(true);
    }
  }

  /** */
  public class TaskAndJobCountTester implements Callable<Object> {
    /** */
    private final JMXDriverConnectionWrapper jmx;

    /**
     * @param jmx .
     */
    public TaskAndJobCountTester(final JMXDriverConnectionWrapper jmx) {
      this.jmx = jmx;
    }

    @Override
    public Object call() throws Exception {
      final JPPFStatistics stats = jmx.statistics();
      JPPFSnapshot snapshot = stats.getSnapshot(JPPFStatisticsHelper.TASK_QUEUE_COUNT);
      assertEquals(Double.valueOf(0d), Double.valueOf(snapshot.getLatest()));
      snapshot = stats.getSnapshot(JPPFStatisticsHelper.JOB_COUNT);
      assertEquals(Double.valueOf(0d), Double.valueOf(snapshot.getLatest()));
      return null;
    }
  }
}
