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

package org.jppf.server.nio.classloader.client;

import static org.jppf.server.nio.classloader.client.ClientClassTransition.*;

import java.net.ConnectException;

import org.jppf.nio.ChannelWrapper;
import org.jppf.utils.LoggingUtils;
import org.slf4j.*;

/**
 * State of sending an initial request to a peer server. This server is seen as a node by the peer,
 * whereas the peer is seen as a client. Therefore, the information sent must allow the remote peer to
 * register a node class loader channel.
 * @author Laurent Cohen
 */
public class SendingPeerInitiationRequestState extends ClientClassServerState {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(SendingPeerInitiationRequestState.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);

  /**
   * Initialize this state with a specified NioServer.
   * @param server the NioServer this state relates to.
   */
  public SendingPeerInitiationRequestState(final ClientClassNioServer server) {
    super(server);
  }

  /**
   * Execute the action associated with this channel state.
   * @param channel the selection key corresponding to the channel and selector for this state.
   * @return a state transition as an <code>NioTransition</code> instance.
   * @throws Exception if an error occurs while transitioning to another state.
   * @see org.jppf.nio.NioState#performTransition(java.nio.channels.SelectionKey)
   */
  @Override
  public ClientClassTransition performTransition(final ChannelWrapper<?> channel) throws Exception {
    final ClientClassContext context = (ClientClassContext) channel.getContext();
    if (channel.isReadable() && !channel.isLocal() && !context.isSecure()) {
      throw new ConnectException("provider " + channel + " has been disconnected");
    }
    //if (context.writePeerInitiationMessage(channel))
    if (context.writeMessage(channel)) {
      if (debugEnabled) log.debug("sent peer initiation to server " + channel);
      context.setMessage(null);
      return TO_WAITING_PEER_INITIATION_RESPONSE;
    }
    return TO_SENDING_PEER_INITIATION_REQUEST;
  }
}
