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

package test.org.jppf.client;

import static org.junit.Assert.*;

import java.io.NotSerializableException;
import java.lang.management.*;
import java.util.List;

import org.jppf.client.*;
import org.jppf.server.protocol.*;
import org.jppf.utils.*;
import org.junit.Test;

import test.org.jppf.test.setup.*;
import test.org.jppf.test.setup.common.*;

/**
 * Unit tests for <code>JPPFClient</code>.
 * @author Laurent Cohen
 */
public class TestJPPFClient extends Setup1D1N
{
  /**
   * Invocation of the <code>JPPFClient()</code> constructor.
   * @throws Exception if any error occurs
   */
  @Test(timeout=10000)
  public void testDefaultConstructor() throws Exception
  {
    JPPFClient client = new JPPFClient();
    try
    {
      while (!client.hasAvailableConnection()) Thread.sleep(10L);
    }
    finally
    {
      client.close();
    }
  }

  /**
   * Invocation of the <code>JPPFClient(String uuid)</code> constructor.
   * @throws Exception if any error occurs
   */
  @Test(timeout=10000)
  public void testConstructorWithUuid() throws Exception
  {
    JPPFClient client = new JPPFClient("some_uuid");
    try
    {
      while (!client.hasAvailableConnection()) Thread.sleep(10L);
    }
    finally
    {
      client.close();
    }
  }

  /**
   * Test the submission of a job.
   * @throws Exception if any error occurs
   */
  @Test(timeout=10000)
  public void testSubmit() throws Exception
  {
    JPPFClient client = BaseSetup.createClient(null);
    try
    {
      int nbTasks = 10;
      JPPFJob job = BaseTestHelper.createJob("TestSubmit", true, false, nbTasks, LifeCycleTask.class, 0L);
      int i = 0;
      for (JPPFTask task: job.getTasks()) task.setId("" + i++);
      List<JPPFTask> results = client.submit(job);
      assertNotNull(results);
      assertTrue("results size should be " + nbTasks + " but is " + results.size(), results.size() == nbTasks);
      String msg = BaseTestHelper.EXECUTION_SUCCESSFUL_MESSAGE;
      for (i=0; i<nbTasks; i++)
      {
        JPPFTask t = results.get(i);
        Exception e = t.getException();
        assertNull("task " + i +" has an exception " + e, e);
        assertEquals("result of task " + i + " should be " + msg + " but is " + t.getResult(), msg, t.getResult());
      }
    }
    finally
    {
      client.close();
    }
  }

  /**
   * Test the cancellation of a job.
   * @throws Exception if any error occurs
   */
  @Test(timeout=10000)
  public void testCancelJob() throws Exception
  {
    JPPFClient client = BaseSetup.createClient(null);
    try
    {
      int nbTasks = 10;
      JPPFJob job = BaseTestHelper.createJob("TestJPPFClientCancelJob", false, false, nbTasks, LifeCycleTask.class, 5000L);
      JPPFResultCollector collector = (JPPFResultCollector) job.getResultListener();
      int i = 0;
      for (JPPFTask task: job.getTasks()) task.setId("" + i++);
      client.submit(job);
      Thread.sleep(1500L);
      client.cancelJob(job.getUuid());
      List<JPPFTask> results = collector.waitForResults();
      assertNotNull(results);
      assertTrue("results size should be " + nbTasks + " but is " + results.size(), results.size() == nbTasks);
      int count = 0;
      for (JPPFTask t: results)
      {
        if (t.getResult() == null) count++;
      }
      assertTrue(count > 0);
    }
    finally
    {
      client.close();
    }
  }

  /**
   * Test that the number of threads for local execution is the configured one.
   * See bug <a href="http://sourceforge.net/tracker/?func=detail&aid=3539111&group_id=135654&atid=733518">3539111 - Local execution does not use configured number of threads</a>.
   * @throws Exception if any error occurs
   */
  @Test(timeout=10000)
  public void testLocalExecutionNbThreads() throws Exception
  {
    int nbThreads = 2;
    TypedProperties config = JPPFConfiguration.getProperties();
    config.setProperty("jppf.local.execution.enabled", "true");
    config.setProperty("jppf.local.execution.threads", "" + nbThreads);
    JPPFClient client = new JPPFClient();
    try
    {
      while (!client.hasAvailableConnection()) Thread.sleep(10L);

      // submit a job to ensure all local execution threads are created
      int nbTasks = 100;
      JPPFJob job = BaseTestHelper.createJob("TestSubmit", true, false, nbTasks, LifeCycleTask.class, 0L);
      int i = 0;
      for (JPPFTask task: job.getTasks()) task.setId("" + i++);
      List<JPPFTask> results = client.submit(job);
      assertNotNull(results);
      assertEquals(nbTasks, results.size());
      String msg = BaseTestHelper.EXECUTION_SUCCESSFUL_MESSAGE;
      for (JPPFTask t: results)
      {
        Exception e = t.getException();
        assertNull(e);
        assertEquals(msg, t.getResult());
      }

      ThreadMXBean mxbean = ManagementFactory.getThreadMXBean();
      long[] ids = mxbean.getAllThreadIds();
      ThreadInfo[] allInfo = mxbean.getThreadInfo(ids);
      int count = 0;
      for (ThreadInfo ti: allInfo)
      {
        if (ti == null) continue;
        String name = ti.getThreadName();
        if (name == null) continue;
        if (name.startsWith("node processing")) count++;
      }
      assertEquals(nbThreads, count);
    }
    finally
    {
      client.close();
      JPPFConfiguration.reset();
    }
  }

  /**
   * Test that a {@link java.io.NotSerializableException} occurring when submitting a job to a driver is properly handled.
   * @throws Exception if any error occurs
   */
  @Test(timeout=10000)
  public void testNotSerializableExceptionFromClient() throws Exception
  {
    JPPFClient client = new JPPFClient();
    try
    {
      JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, 1, NotSerializableTask.class, true);
      List<JPPFTask> results = client.submit(job);
      assertNotNull(results);
      assertEquals(1, results.size());
      JPPFTask task = results.get(0);
      assertNotNull(task.getException());
      assertTrue(task.getException() instanceof NotSerializableException);
    }
    finally
    {
      client.close();
    }
  }

  /**
   * Test that a {@link java.io.NotSerializableException} occurring when a node returns execution results is properly handled.
   * @throws Exception if any error occurs
   */
  @Test(timeout=10000)
  public void testNotSerializableExceptionFromNode() throws Exception
  {
    JPPFClient client = new JPPFClient();
    try
    {
      JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), true, false, 1, NotSerializableTask.class, false);
      List<JPPFTask> results = client.submit(job);
      assertNotNull(results);
      assertEquals(1, results.size());
      JPPFTask task = results.get(0);
      assertTrue(task instanceof JPPFExceptionResult);
      assertNotNull(task.getException());
      assertTrue(task.getException() instanceof NotSerializableException);
    }
    finally
    {
      client.close();
    }
  }

  /**
   * A task which holds a non-serializable object.
   */
  public static class NotSerializableTask extends JPPFTask
  {
    /**
     * A non-serializable object.
     */
    private NotSerializableObject nso = null;
    /**
     *  <code>true</code> if the non-serializable object should be created in the constructor, <code>false</code> if it should be created in the client.
     */
    private final boolean instantiateInClient;

    /**
     * Initialize with the specified flag.
     * @param instantiateInClient <code>true</code> if the non-serializable object should be created in the constructor (client side),
     * <code>false</code> if it should be created in the <code>run()</code> method (node side).
     */
    public NotSerializableTask(final boolean instantiateInClient)
    {
      this.instantiateInClient = instantiateInClient;
      if (instantiateInClient) nso = new NotSerializableObject();
    }

    @Override
    public void run()
    {
      if (!instantiateInClient) nso = new NotSerializableObject();
    }
  }

  /**
   * A task which holds a non-serializable object.
   */
  public static class NotSerializableObject
  {
    /**
     * Any attribute will do.
     */
    public String name = "NotSerializableObject";
  }
}