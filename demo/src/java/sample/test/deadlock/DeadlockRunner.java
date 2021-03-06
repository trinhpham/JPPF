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

package sample.test.deadlock;

import java.util.List;

import org.jppf.client.*;
import org.jppf.management.*;
import org.jppf.management.forwarding.JPPFNodeForwardingMBean;
import org.jppf.node.policy.Equal;
import org.jppf.node.protocol.Task;
import org.jppf.utils.*;
import org.jppf.utils.Operator;
import org.slf4j.*;

/**
 * An illustration of the patterns for submitting multiple jobs in parallel.
 */
public class DeadlockRunner {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(ProvisioningThread.class);
  /**
   *
   */
  private static JMXDriverConnectionWrapper jmx = null;

  /**
   * Entry point for this demo.
   * @param args the first argument is a function number that determines which method to call.
   */
  public static void main(final String[] args) {
    try {
      //StreamUtils.waitKeyPressed("Please press [Enter]");
      final DeadlockRunner runner = new DeadlockRunner();
      runner.jobStreaming();
      //runner.testNodes();
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Execute a stream of non-blocking jobs from a single thread, process the results asynchronously.
   */
  public void jobStreaming() {
    final RunOptions ro = new RunOptions();
    printf("Running with conccurencyLimit=%d, nbJobs=%d, tasksPerJob=%d, taskDuration=%d", ro.concurrencyLimit, ro.nbJobs, ro.tasksPerJob, ro.taskOptions.taskDuration);
    ProvisioningThread pt = null;
    MasterNodeMonitoringThread mnmt = null;
    try (JPPFClient client = new JPPFClient(); JobStreamImpl jobProvider = new JobStreamImpl(ro)) {
      getJmxConnection(client);
      ro.callback = new ScriptedJobCallback();
      //ro.callback = new SystemExitCallback();
      //ro.callback = new JobPersistenceCallback();
      ensureSufficientConnections(client, ro.clientConnections);
      if (ro.slaves >= 0) updateSlaveNodes(client, ro.slaves);
      if (ro.simulateNodeCrashes) {
        pt = new ProvisioningThread(client, ro.waitTime);
        mnmt = new MasterNodeMonitoringThread(client, 5000L, pt);
      }
      try {
        if (ro.simulateNodeCrashes) {
          new Thread(pt, "ProvisioningThread").start();
          new Thread(mnmt, "MasterNodeMonitoringThread").start();
        }
        final TimeMarker marker = new TimeMarker().start();
        int count = 0;
        for (final JPPFJob job: jobProvider) {
          if ((job != null) && !client.isClosed()) client.submitJob(job);
          if (count == ro.triggerNodeDeadlockAfter) {
            final JPPFJob deadlockingJob = new JPPFJob();
            deadlockingJob.setName("deadlock trigger job");
            deadlockingJob.setBlocking(false);
            deadlockingJob.add(new DeadlockingTask());
            client.submitJob(deadlockingJob);
          }
          count++;
          //requestNodeShutdown(client);
        }
        while (jobProvider.hasPendingJob()) Thread.sleep(10L);
        printf("*** executed a total of %,d jobs and %,d tasks in %s", jobProvider.getJobCount(), jobProvider.getTaskCount(), marker.stop().getLastElapsedAsString());
      } finally {
        if (ro.simulateNodeCrashes) {
          pt.setStopped(true);
          mnmt.setStopped(true);
        }
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  /**
   *
   */
  public void testNodes() {
    final TypedProperties config = JPPFConfiguration.getProperties();
    final int[] nbSlaves = StringUtils.parseIntValues(config.getString("deadlock.nbSlaves", "0"));
    try (JPPFClient jppfClient = new JPPFClient()) {
      ensureSufficientConnections(jppfClient, 1);
      updateSlaveNodes(jppfClient, 0);
      final TimeMarker marker = new TimeMarker().start();
      for (int n: nbSlaves) updateSlaveNodes(jppfClient, n);
      printf("total time: %s", marker.stop().getLastElapsedAsString());
      updateSlaveNodes(jppfClient, 0);
    } catch(final Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Process the results of a job.
   * @param job the JPPF job whose results are printed.
   */
  public static void processResults(final JPPFJob job) {
    printf("*** results for job '%s' ***", job.getName());
    final List<Task<?>> results = job.getAllResults();
    for (Task<?> task: results) {
      if (task.getThrowable() != null) printf("%s raised an exception : %s", task.getId(), ExceptionUtils.getMessage(task.getThrowable()));
      //else System.out.printf("result of %s : %s\n", task.getId(), task.getResult());
    }
  }

  /**
   * Ensure that the JPPF client has the specified number of connections.
   * @param client the jppf client.
   * @param nbConnections the desired number of connections.
   * @throws Exception if any error occurs.
   */
  private static void ensureSufficientConnections(final JPPFClient client, final int nbConnections) throws Exception {
    printf("***** ensuring %d connections ...", nbConnections);
    final JPPFConnectionPool pool = client.awaitConnectionPool();
    printf("***** ensuring %d connections, found pool = %s", nbConnections, pool);
    pool.setSize(nbConnections);
    printf("***** ensuring %d connections, called setSize(%d)", nbConnections, nbConnections);
    pool.awaitActiveConnections(Operator.AT_LEAST, nbConnections);
    printf("***** ensuring %d connections, after pool.await()", nbConnections);
  }

  /**
   * Update the number of running slave nodes.
   * @param client the JPPF client to get a JMX connection from.
   * @param nbSlaves the number of slave nodes to reach.
   * @throws Exception if any error occurs.
   */
  private static void updateSlaveNodes(final JPPFClient client, final int nbSlaves) throws Exception {
    printf("ensuring %d slaves ...", nbSlaves);
    final JMXDriverConnectionWrapper jmx = getJmxConnection(client);
    if (jmx.nbNodes() == nbSlaves + 1) return;
    final JPPFNodeForwardingMBean forwarder = jmx.getNodeForwarder();
    final NodeSelector masterSelector = new ExecutionPolicySelector(new Equal("jppf.node.provisioning.master", true));
    // request that <nbSlaves> slave nodes be provisioned
    final TimeMarker marker = new TimeMarker().start();
    forwarder.provisionSlaveNodes(masterSelector, nbSlaves, null);
    while (jmx.nbNodes() != nbSlaves + 1) Thread.sleep(10L);
    printf("slaves confirmation wait time: %s", marker.stop().getLastElapsedAsString());
  }

  /**
   * Request that a node be shutdown.
   * @param client the JPPF client to get a JMX connection from.
   * @throws Exception if any error occurs.
   */
  @SuppressWarnings("unused")
  private static void requestNodeShutdown(final JPPFClient client) throws Exception {
    printf("requesting node shutdown ...");
    final JMXDriverConnectionWrapper jmx = getJmxConnection(client);
    final NodeSelector selector = new ExecutionPolicySelector(new Equal("jppf.node.provisioning.master", true));
    jmx.getNodeForwarder().shutdown(selector, false);
  }

  /**
   * Get a JMX connectionf rom the specified client.
   * @param client the client ot get the connection from.
   * @return a {@link JMXDriverConnectionWrapper} instance.
   * @throws Exception if any error occurs.
   */
  static synchronized JMXDriverConnectionWrapper getJmxConnection(final JPPFClient client) throws Exception {
    if (jmx == null) jmx = client.awaitWorkingConnectionPool().awaitWorkingJMXConnection();
    return jmx;
  }

  /**
   * Submit a job with a single task wich triggers a deadlock int he node where it executes.
   * @param client the client which submits the job.
   * @throws Exception if any error occurs.
   */
  @SuppressWarnings("unused")
  private static void submitDeadlockingJob(final JPPFClient client) throws Exception {
    final JPPFJob job = new JPPFJob();
    job.setName("Deadlocking job");
    job.setBlocking(false);
    job.add(new DeadlockingTask());
    client.submitJob(job);
  }

  /**
   * Print and log the specified formatted message.
   * @param format the message format.
   * @param params the parameters of the message.
   */
  static void printf(final String format, final Object...params) {
    final String msg = String.format(format, params);
    System.out.println(msg);
    log.info(msg);
  }
}
