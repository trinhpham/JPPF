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

package org.jppf.server.nio.classloader.node;

import static org.jppf.server.nio.classloader.node.NodeClassTransition.*;

import org.jppf.classloader.*;
import org.jppf.nio.ChannelWrapper;
import org.jppf.utils.*;
import org.slf4j.*;

/**
 * This class represents the state of a new class server connection, whose type is yet undetermined.
 * @author Laurent Cohen
 */
class WaitingInitialNodeRequestState extends NodeClassServerState {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(WaitingInitialNodeRequestState.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);

  /**
   * Initialize this state with a specified NioServer.
   * @param server the JPPFNIOServer this state relates to.
   */
  public WaitingInitialNodeRequestState(final NodeClassNioServer server) {
    super(server);
  }

  /**
   * Execute the action associated with this channel state.
   * @param wrapper the selection key corresponding to the channel and selector for this state.
   * @return a state transition as an <code>NioTransition</code> instance.
   * @throws Exception if an error occurs while transitioning to another state.
   */
  @Override
  public NodeClassTransition performTransition(final ChannelWrapper<?> wrapper) throws Exception {
    // we don't know yet which whom we are talking, is it a node or a provider?
    final NodeClassContext context = (NodeClassContext) wrapper.getContext();
    if (context.readMessage(wrapper)) {
      final JPPFResourceWrapper resource = context.deserializeResource();
      if (debugEnabled) log.debug("read initial request from node " + wrapper);
      context.setPeer((Boolean) resource.getData(ResourceIdentifier.PEER, Boolean.FALSE));
      if (debugEnabled) log.debug("initiating node: " + wrapper);
      final String uuid = (String) resource.getData(ResourceIdentifier.NODE_UUID);
      if (debugEnabled) log.debug("received node init request for uuid = {}", uuid);
      if (uuid != null) {
        context.setUuid(uuid);
        server.addNodeConnection(uuid, wrapper);
      }
      // send the uuid of this driver to the node or node peer.
      resource.setState(JPPFResourceWrapper.State.NODE_RESPONSE);
      resource.setProviderUuid(driver.getUuid());
      context.serializeResource();
      return TO_SENDING_INITIAL_NODE_RESPONSE;
    }
    return TO_WAITING_INITIAL_NODE_REQUEST;
  }
}
