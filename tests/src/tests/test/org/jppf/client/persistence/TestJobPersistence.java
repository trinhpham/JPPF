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

package test.org.jppf.client.persistence;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jppf.client.*;
import org.jppf.client.event.*;
import org.jppf.client.persistence.*;
import org.jppf.node.protocol.Task;
import org.jppf.utils.*;
import org.jppf.utils.configuration.JPPFProperties;
import org.junit.Test;

import test.org.jppf.test.setup.*;
import test.org.jppf.test.setup.common.*;

/**
 * Test for the <code>JobPersistence</code> interface.
 * @author Laurent Cohen
 */
public class TestJobPersistence extends Setup1D1N {
  /**
   * @throws Exception if any error occurs.
   */
  //@Test(timeout = 100 * 1000)
  public void testInLoop() throws Exception {
    for (int i=1; i<=10; i++) {
      printOut("testing job recovery iteration #%d", i);
      testJobRecovery();
      printOut("==> job recovery iteration #%d done", i);
    }
  }

  /**
   * Test the recovery of a job by closing the JPPF client before it completes.
   * @throws Exception if any error occurs.
   */
  @Test(timeout=20000)
  public void testJobRecovery() throws Exception {
    String key = null;
    JobPersistence<String> pm = null;
    final TypedProperties config = JPPFConfiguration.getProperties();
    final long duration = 1000L;
    JPPFClient client = null;
    try {
      // send tasks 1 at a time
      config.set(JPPFProperties.LOAD_BALANCING_ALGORITHM, "manual")
        .set(JPPFProperties.LOAD_BALANCING_PROFILE, "test")
        .setInt(JPPFProperties.LOAD_BALANCING_PROFILE.getName() + ".test.size", 1);
      client = BaseSetup.createClient(null, false);
      final int nbTasks = 3;
      final AtomicBoolean resultsReceived = new AtomicBoolean(false);
      JPPFJob job = BaseTestHelper.createJob(ReflectionUtils.getCurrentMethodName(), false, false, nbTasks, SimpleTask.class, duration);
      pm = new DefaultFilePersistenceManager("root", "job_", ".ser");
      key = pm.computeKey(job);
      assertEquals(key, job.getUuid());
      job.setPersistenceManager(pm);
      job.addJobListener(new JobListenerAdapter() {
        @Override
        public void jobReturned(final JobEvent event) {
          synchronized(resultsReceived) {
            resultsReceived.set(true);
            resultsReceived.notifyAll();
          }
        }
      });
      client.submitJob(job);
      synchronized(resultsReceived) {
        while (!resultsReceived.get()) resultsReceived.wait(100L);
      }
      client.close();
      final int n = job.getResults().size();
      assertTrue(n < nbTasks);
      final String uuid = job.getUuid();
      job = null;

      client = BaseSetup.createClient(null);
      final JPPFJob job2 = pm.loadJob(key);
      assertEquals(uuid, job2.getUuid());
      //int n2 = job2.getResults().size();
      //assertEquals(n, n2);
      client.submitJob(job2);
      final List<Task<?>> results = job2.awaitResults();
      assertEquals(nbTasks, results.size());
      assertEquals(nbTasks, job2.getResults().size());
    } finally {
      config.remove(JPPFProperties.LOAD_BALANCING_ALGORITHM);
      config.remove(JPPFProperties.LOAD_BALANCING_PROFILE);
      config.remove(JPPFProperties.LOAD_BALANCING_PROFILE.getName() + ".test.size");
      if ((pm != null) && (key != null)) pm.deleteJob(key);
      if (client != null) client.close();
    }
  }
}
