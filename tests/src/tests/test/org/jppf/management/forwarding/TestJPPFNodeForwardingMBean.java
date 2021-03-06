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

package test.org.jppf.management.forwarding;

import static org.junit.Assert.*;

import java.util.*;

import org.jppf.JPPFException;
import org.jppf.classloader.DelegationModel;
import org.jppf.client.JPPFJob;
import org.jppf.management.*;
import org.jppf.node.policy.*;
import org.jppf.node.protocol.Task;
import org.jppf.utils.*;
import org.jppf.utils.configuration.JPPFProperties;
import org.junit.Test;

import test.org.jppf.test.setup.common.*;

/**
 * Unit tests for {@link JPPFDriverAdminMBean}.
 * In this class, we test that the functionality of the DriverJobManagementMBean from the client point of view.
 * @author Laurent Cohen
 */
public class TestJPPFNodeForwardingMBean extends AbstractTestJPPFNodeForwardingMBean {
  /**
   * Test getting the node state.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 15000)
  public void testState() throws Exception {
    testState(new AllNodesSelector(), "n1", "n2");
    testState(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testState(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs
   */
  private static void testState(final NodeSelector selector, final String... expectedNodes) throws Exception {
    final int nbNodes = expectedNodes.length;
    try {
      configureLoadBalancer();
      Map<String, Object> result = nodeForwarder.state(selector);
      checkNodes(result, JPPFNodeState.class, expectedNodes);
      for (final Map.Entry<String, Object> entry : result.entrySet()) {
        final JPPFNodeState state = (JPPFNodeState) entry.getValue();
        assertEquals(1, state.getThreadPoolSize());
        assertEquals(Thread.NORM_PRIORITY, state.getThreadPriority());
        assertEquals(JPPFNodeState.ConnectionState.CONNECTED, state.getConnectionStatus());
        assertEquals(JPPFNodeState.ExecutionState.IDLE, state.getExecutionStatus());
      }
      final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName() + " - " + selector, false, false, nbNodes, LifeCycleTask.class, 2000L);
      job.getSLA().setExecutionPolicy(new OneOf("jppf.node.uuid", false, expectedNodes));
      client.submitJob(job);
      Thread.sleep(750L);
      result = nodeForwarder.state(selector);
      checkNodes(result, JPPFNodeState.class, expectedNodes);
      for (final Map.Entry<String, Object> entry : result.entrySet()) {
        final JPPFNodeState state = (JPPFNodeState) entry.getValue();
        assertEquals(0, state.getNbTasksExecuted());
        assertEquals(JPPFNodeState.ExecutionState.EXECUTING, state.getExecutionStatus());
      }
      job.awaitResults();
      result = nodeForwarder.state(selector);
      checkNodes(result, JPPFNodeState.class, expectedNodes);
      for (final Map.Entry<String, Object> entry : result.entrySet()) {
        final JPPFNodeState state = (JPPFNodeState) entry.getValue();
        assertEquals(1, state.getNbTasksExecuted());
        assertEquals(JPPFNodeState.ExecutionState.IDLE, state.getExecutionStatus());
      }
    } finally {
      nodeForwarder.resetTaskCounter(selector);
      resetLoadBalancer();
    }
  }

  /**
   * Test updating the number of processing threads.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testUpdateThreadPoolSize() throws Exception {
    try {
      testUpdateThreadPoolSize(new AllNodesSelector(), "n1", "n2");
      testUpdateThreadPoolSize(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
      testUpdateThreadPoolSize(new UuidSelector("n2"), "n2");
    } catch(final Exception e) {
      throw new JPPFException(e);
    }
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testUpdateThreadPoolSize(final NodeSelector selector, final String... expectedNodes) throws Exception {
    Map<String, Object> result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(1, state.getThreadPoolSize());
    }
    result = nodeForwarder.updateThreadPoolSize(selector, 3);
    checkNoException(result, expectedNodes);
    result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(3, state.getThreadPoolSize());
    }
    nodeForwarder.updateThreadPoolSize(selector, 1);
  }

  /**
   * Test updating the priority of processing threads.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testUpdateThreadPriority() throws Exception {
    testUpdateThreadPriority(new AllNodesSelector(), "n1", "n2");
    testUpdateThreadPriority(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n2")), "n2");
    testUpdateThreadPriority(new UuidSelector("n1"), "n1");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testUpdateThreadPriority(final NodeSelector selector, final String... expectedNodes) throws Exception {
    Map<String, Object> result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(Thread.NORM_PRIORITY, state.getThreadPriority());
    }
    result = nodeForwarder.updateThreadsPriority(selector, Thread.MAX_PRIORITY);
    checkNoException(result, expectedNodes);
    result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(Thread.MAX_PRIORITY, state.getThreadPriority());
    }
    nodeForwarder.updateThreadsPriority(selector, Thread.NORM_PRIORITY);
  }

  /**
   * Test resetting the tasks counter.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testResetTaskCounter() throws Exception {
    testResetTaskCounter(new AllNodesSelector(), "n1", "n2");
    testResetTaskCounter(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testResetTaskCounter(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testResetTaskCounter(final NodeSelector selector, final String... expectedNodes) throws Exception {
    final int nbNodes = expectedNodes.length;
    final int nbTasks = 5 * nbNodes;
    Map<String, Object> result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(0, state.getNbTasksExecuted());
    }
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName() + " - " + selector, true, false, nbTasks, LifeCycleTask.class, 1L);
    job.getSLA().setExecutionPolicy(new OneOf("jppf.node.uuid", false, expectedNodes));
    client.submitJob(job);
    result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(5, state.getNbTasksExecuted());
    }
    result = nodeForwarder.resetTaskCounter(selector);
    checkNoException(result, expectedNodes);
    result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(0, state.getNbTasksExecuted());
    }
  }

  /**
   * Test setting the tasks counter to a given value.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testSetTaskCounter() throws Exception {
    testSetTaskCounter(new AllNodesSelector(), "n1", "n2");
    testSetTaskCounter(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testSetTaskCounter(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testSetTaskCounter(final NodeSelector selector, final String... expectedNodes) throws Exception {
    Map<String, Object> result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(0, state.getNbTasksExecuted());
    }
    result = nodeForwarder.setTaskCounter(selector, 12);
    System.out.println("testSetTaskCounter() result = " + result);
    checkNullResults(result, expectedNodes);
    result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(12, state.getNbTasksExecuted());
    }
    result = nodeForwarder.setTaskCounter(selector, 0);
    checkNullResults(result, expectedNodes);
    result = nodeForwarder.state(selector);
    checkNodes(result, JPPFNodeState.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFNodeState state = (JPPFNodeState) entry.getValue();
      assertEquals(0, state.getNbTasksExecuted());
    }
  }

  /**
   * Test getting the node system information.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testSystemInformation() throws Exception {
    testSystemInformation(new AllNodesSelector(), "n1", "n2");
    testSystemInformation(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testSystemInformation(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testSystemInformation(final NodeSelector selector, final String... expectedNodes) throws Exception {
    final Map<String, Object> result = nodeForwarder.systemInformation(selector);
    checkNodes(result, JPPFSystemInformation.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final JPPFSystemInformation info = (JPPFSystemInformation) entry.getValue();
      assertNotNull(info);
      assertNotNull(info.getEnv());
      assertFalse(info.getEnv().isEmpty());
      assertNotNull(info.getJppf());
      assertEquals(1, (int) info.getJppf().get(JPPFProperties.PROCESSING_THREADS));
      assertEquals(entry.getKey(), info.getJppf().getString("jppf.node.uuid"));
      assertFalse(info.getJppf().isEmpty());
      assertNotNull(info.getNetwork());
      assertFalse(info.getNetwork().isEmpty());
      assertNotNull(info.getRuntime());
      assertFalse(info.getRuntime().isEmpty());
      assertEquals(Runtime.getRuntime().availableProcessors(), info.getRuntime().getInt("availableProcessors"));
      assertNotNull(info.getStorage());
      assertFalse(info.getStorage().isEmpty());
      assertNotNull(info.getSystem());
      assertFalse(info.getSystem().isEmpty());
    }
  }

  /**
   * Test updating the ndoe's configuration.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testUpdateConfiguration() throws Exception {
    testUpdateConfiguration(new AllNodesSelector(), "n1", "n2");
    testUpdateConfiguration(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testUpdateConfiguration(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testUpdateConfiguration(final NodeSelector selector, final String... expectedNodes) throws Exception {
    TypedProperties oldConfig = null;
    TypedProperties newConfig = null;
    Map<String, Object> result = nodeForwarder.systemInformation(selector);
    checkNodes(result, JPPFSystemInformation.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final String uuid = entry.getKey();
      final JPPFSystemInformation info = (JPPFSystemInformation) entry.getValue();
      assertNotNull(info);
      final TypedProperties config = info.getJppf();
      assertNotNull(config);
      assertFalse(config.isEmpty());
      assertEquals(1, (int) config.get(JPPFProperties.PROCESSING_THREADS));
      assertEquals(uuid, config.getString("jppf.node.uuid"));
      if (oldConfig == null) oldConfig = new TypedProperties(config);
      if (newConfig == null) newConfig = new TypedProperties(config).set(JPPFProperties.PROCESSING_THREADS, 8).setString("custom.property", "custom.value");
    }
    result = nodeForwarder.updateConfiguration(selector, newConfig, false);
    checkNoException(result, expectedNodes);
    result = nodeForwarder.systemInformation(selector);
    checkNodes(result, JPPFSystemInformation.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      final String uuid = entry.getKey();
      final JPPFSystemInformation info = (JPPFSystemInformation) entry.getValue();
      assertNotNull(info);
      newConfig = info.getJppf();
      assertNotNull(newConfig);
      assertFalse(newConfig.isEmpty());
      assertEquals(8, (int) newConfig.get(JPPFProperties.PROCESSING_THREADS));
      assertEquals(uuid, newConfig.getString("jppf.node.uuid"));
      assertEquals("custom.value", newConfig.getString("custom.property"));
    }
    nodeForwarder.updateConfiguration(selector, oldConfig, false);
  }

  /**
   * Test cancelling a job executing in the nodes.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 10000)
  public void testCancelJob() throws Exception {
    testCancelJob(new AllNodesSelector(), "n1", "n2");
    testCancelJob(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testCancelJob(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testCancelJob(final NodeSelector selector, final String... expectedNodes) throws Exception {
    final int nbNodes = expectedNodes.length;
    final int nbTasks = 5 * nbNodes;
    final JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName() + " - " + selector, false, false, nbTasks, LifeCycleTask.class, 5000L);
    job.getSLA().setExecutionPolicy(new OneOf("jppf.node.uuid", false, expectedNodes));
    final String uuid = job.getUuid();
    client.submitJob(job);
    Thread.sleep(750L);
    final Map<String, Object> result = nodeForwarder.cancelJob(selector, uuid, false);
    checkNoException(result, expectedNodes);
    final List<Task<?>> jobResult = job.awaitResults();
    assertNotNull(jobResult);
    assertEquals(nbTasks, jobResult.size());
    int count = 0;
    for (final Task<?> t : jobResult) {
      final LifeCycleTask task = (LifeCycleTask) t;
      if (count == 0) assertTrue(task.isCancelled());
      assertNull(task.getResult());
      count++;
    }
    nodeForwarder.resetTaskCounter(selector);
  }

  /**
   * Test getting the class loader's delegation model.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testGetDelegationModel() throws Exception {
    testGetDelegationModel(new AllNodesSelector(), "n1", "n2");
    testGetDelegationModel(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testGetDelegationModel(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testGetDelegationModel(final NodeSelector selector, final String... expectedNodes) throws Exception {
    final Map<String, Object> result = nodeForwarder.getDelegationModel(selector);
    checkNodes(result, DelegationModel.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      assertEquals(DelegationModel.PARENT_FIRST, entry.getValue());
    }
  }

  /**
   * Test setting the class loader's delegation model.
   * @throws Exception if any error occurs.
   */
  @Test(timeout = 5000)
  public void testSetDelegationModel() throws Exception {
    testSetDelegationModel(new AllNodesSelector(), "n1", "n2");
    testSetDelegationModel(new ExecutionPolicySelector(new Equal("jppf.node.uuid", false, "n1")), "n1");
    testSetDelegationModel(new UuidSelector("n2"), "n2");
  }

  /**
   * Execute the tests with the specified node selector.
   * @param selector the selector to apply.
   * @param expectedNodes the set of nodes the selector is expected to resilve to.
   * @throws Exception if any error occurs.
   */
  private static void testSetDelegationModel(final NodeSelector selector, final String... expectedNodes) throws Exception {
    Map<String, Object> result = nodeForwarder.setDelegationModel(selector, DelegationModel.URL_FIRST);
    checkNullResults(result, expectedNodes);
    result = nodeForwarder.getDelegationModel(selector);
    checkNodes(result, DelegationModel.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      assertEquals(DelegationModel.URL_FIRST, entry.getValue());
    }
    result = nodeForwarder.setDelegationModel(selector, DelegationModel.PARENT_FIRST);
    checkNullResults(result, expectedNodes);
    result = nodeForwarder.getDelegationModel(selector);
    checkNodes(result, DelegationModel.class, expectedNodes);
    for (final Map.Entry<String, Object> entry : result.entrySet()) {
      assertEquals(DelegationModel.PARENT_FIRST, entry.getValue());
    }
  }
}
