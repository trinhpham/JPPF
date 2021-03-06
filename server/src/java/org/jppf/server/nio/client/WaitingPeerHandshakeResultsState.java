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

package org.jppf.server.nio.client;

import static org.jppf.server.nio.client.ClientTransition.*;

import java.util.List;

import org.jppf.nio.ChannelWrapper;
import org.jppf.node.protocol.TaskBundle;
import org.jppf.server.nio.classloader.client.ClientClassNioServer;
import org.jppf.server.protocol.ServerTaskBundleClient;
import org.jppf.utils.LoggingUtils;
import org.slf4j.*;

/**
 * This class performs performs the work of reading a task bundle execution response from a node.
 * @author Laurent Cohen
 */
class WaitingPeerHandshakeResultsState extends ClientServerState {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(WaitingPeerHandshakeResultsState.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);

  /**
   * Initialize this state.
   * @param server the server that handles this state.
   */
  public WaitingPeerHandshakeResultsState(final ClientNioServer server) {
    super(server);
  }

  /**
   * Execute the action associated with this channel state.
   * @param channel the selection key corresponding to the channel and selector for this state.
   * @return a state transition as an <code>NioTransition</code> instance.
   * @throws Exception if an error occurs while transitioning to another state.
   */
  @Override
  public ClientTransition performTransition(final ChannelWrapper<?> channel) throws Exception {
    final ClientContext context = (ClientContext) channel.getContext();
    if (context.getClientMessage() == null) context.setClientMessage(context.newMessage());
    if (context.readMessage(channel)) {
      final ServerTaskBundleClient bundleWrapper = context.deserializeBundle();
      final TaskBundle header = bundleWrapper.getJob();
      if (debugEnabled) log.debug("read handshake bundle " + header + " from client " + channel);
      //context.setConnectionUuid((String) header.getParameter(BundleParameter.CONNECTION_UUID));
      header.getUuidPath().incPosition();
      final String uuid = header.getUuidPath().getCurrentElement();
      context.setUuid(uuid);
      // wait until a class loader channel is up for the same client uuid
      final ClientClassNioServer classServer = driver.getClientClassServer();
      List<ChannelWrapper<?>> list = classServer.getProviderConnections(uuid);
      while ((list == null) || list.isEmpty()) {
        Thread.sleep(1L);
        list = classServer.getProviderConnections(uuid);
      }
      final String driverUUID = driver.getUuid();
      header.getUuidPath().add(driverUUID);
      if (debugEnabled) log.debug("uuid path=" + header.getUuidPath());

      context.setClientMessage(null);
      context.setBundle(bundleWrapper);
      header.clear();
      return TO_WAITING_JOB;
    }
    return TO_WAITING_PEER_HANDSHAKE_RESULTS;
  }
}
