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

package org.jppf.server.nio.nodeserver;

import org.jppf.nio.NioState;

/**
 * Common abstract superclass for all states of a node that executes tasks.
 * @author Laurent Cohen
 */
abstract class NodeServerState extends NioState<NodeTransition>
{
  /**
   * The server that handles this state.
   */
  protected final NodeNioServer server;

  /**
   * Initialize this state.
   * @param server the server that handles this state.
   */
  public NodeServerState(final NodeNioServer server)
  {
    this.server = server;
  }
}
