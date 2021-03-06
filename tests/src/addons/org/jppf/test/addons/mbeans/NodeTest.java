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

package org.jppf.test.addons.mbeans;

import java.util.concurrent.atomic.AtomicLong;

import javax.management.*;

import org.jppf.utils.concurrent.DeadlockDetector;

/**
 * Implementation of {@link NodeTestMBean}.
 * @author Laurent Cohen
 */
public class NodeTest extends NotificationBroadcasterSupport implements NodeTestMBean {
  /**
   * Explicit serialVersionUID.
   */
  private static final long serialVersionUID = 1L;
  /**
   * Notification sequence number.
   */
  private static AtomicLong sequence = new AtomicLong(0L);

  /**
   * Default constructor.
   */
  public NodeTest() {
    System.out.println("initialized NodeTest");
    DeadlockDetector.setup("node");
  }

  @Override
  public void sendUserObject(final Object userObject) throws Exception {
    final Notification notif = new Notification("NodeTest", NodeTestMBean.MBEAN_NAME, sequence.incrementAndGet());
    notif.setUserData(userObject);
    sendNotification(notif);
  }

  @Override
  public long getTotalNotifications() throws Exception {
    return sequence.get();
  }

  @Override
  public synchronized void sendNotification(final Notification notification) {
    System.out.printf("sending notification: type=%s, sequence=%d, userData=%s%n", notification.getType(), notification.getSequenceNumber(), notification.getUserData());
    super.sendNotification(notification);
  }
}
