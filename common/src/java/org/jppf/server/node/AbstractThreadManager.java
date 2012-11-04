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

package org.jppf.server.node;

import java.lang.management.*;

import org.jppf.node.*;

/**
 * This class manages the thread for the node's execution manager.
 * @author Laurent Cohen
 * @author Martin JANDA
 */
public abstract class AbstractThreadManager implements ThreadManager
{
  /**
   * The platform MBean used to gather statistics about the JVM threads.
   */
  protected ThreadMXBean threadMXBean = null;
  /**
   * Determines whether the thread cpu time measurement is supported and enabled.
   */
  protected boolean cpuTimeEnabled = false;

  /**
   * Initialize this execution manager with the specified node.
   */
  protected AbstractThreadManager()
  {
    threadMXBean = ManagementFactory.getThreadMXBean();
    cpuTimeEnabled = threadMXBean.isThreadCpuTimeSupported();
    if (cpuTimeEnabled) threadMXBean.setThreadCpuTimeEnabled(true);
  }

  @Override
  public NodeExecutionInfo computeExecutionInfo()
  {
    NodeExecutionInfo info = new NodeExecutionInfo();
    long[] ids = getThreadIds();
    for (long id: ids) info.add(computeExecutionInfo(id));
    return info;
  }

  @Override
  public NodeExecutionInfo computeExecutionInfo(final long threadID)
  {
    return (!cpuTimeEnabled) ? new NodeExecutionInfo() : new NodeExecutionInfo(threadMXBean.getThreadCpuTime(threadID), threadMXBean.getThreadUserTime(threadID));
  }

  /**
   * Get the ids of all managed threads.
   * @return the ids as an array of long values, may be an empty but never null array.
   */
  protected abstract long[] getThreadIds();

  @Override
  public long getCpuTime(final long threadId)
  {
    return cpuTimeEnabled ? threadMXBean.getThreadCpuTime(threadId) : -1L;
  }

  @Override
  public boolean isCpuTimeEnabled()
  {
    return cpuTimeEnabled;
  }
}