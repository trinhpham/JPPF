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

package org.jppf.management;

import org.jppf.utils.*;
import org.jppf.utils.concurrent.ThreadSynchronization;
import org.slf4j.*;

/**
 * This class is intended to be used as a thread that attempts to (re-)connect to
 * the management server.
 * @exclude
 */
public class JMXConnectionThread extends ThreadSynchronization implements Runnable {
  /**
   * Logger for this class.
   */
  static Logger log = LoggerFactory.getLogger(JMXConnectionThread.class);
  /**
   * Determines whether debug log statements are enabled.
   */
  static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);
  /**
   * The connection that holds this thread.
   */
  private final JMXConnectionWrapper connectionWrapper;
  /**
   * The thread running this object.
   */
  private Thread thread;

  /**
   * Initialize this thread with the specified connection.
   * @param connectionWrapper the connection that holds this thread.
   */
  public JMXConnectionThread(final JMXConnectionWrapper connectionWrapper) {
    this.connectionWrapper = connectionWrapper;
  }

  @Override
  public void run() {
    try {
      synchronized(this) {
        this.thread = Thread.currentThread();
      }
      while (!isStopped() && !connectionWrapper.closed.get()) {
        try {
          if (debugEnabled) log.debug(connectionWrapper.getId() + " about to perform connection attempts");
          connectionWrapper.performConnection();
          if (debugEnabled) log.debug(connectionWrapper.getId() + " about to suspend connection attempts");
        } catch(final Exception e) {
          if (debugEnabled) log.debug(connectionWrapper.getId()+ " JMX URL = " + connectionWrapper.getURL(), e);
          goToSleep(10L);
        }
      }
    } finally {
      synchronized(this) {
        this.thread = null;
      }
    }
  }

  /**
   * Stop this thread.
   */
  public synchronized void close() {
    setStopped(true);
    synchronized(this) {
      if (this.thread != null) {
        this.thread.interrupt();
      }
    }
  }
}
