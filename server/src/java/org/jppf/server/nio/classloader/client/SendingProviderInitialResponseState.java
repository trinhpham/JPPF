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
 * State of sending the initial response to a newly created provider channel.
 * @author Laurent Cohen
 */
public class SendingProviderInitialResponseState extends ClientClassServerState {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(SendingProviderInitialResponseState.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);

  /**
   * Initialize this state with a specified NioServer.
   * @param server the NioServer this state relates to.
   */
  public SendingProviderInitialResponseState(final ClientClassNioServer server) {
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
    if (channel.isReadable() && !channel.isLocal()) {
      throw new ConnectException("provider " + channel + " has been disconnected");
    }
    if (context.writeMessage(channel)) {
      if (debugEnabled) log.debug("sent management to provider: " + channel);
      return context.isPeer() ? TO_IDLE_PEER_PROVIDER : TO_IDLE_PROVIDER;
    }
    return TO_SENDING_INITIAL_PROVIDER_RESPONSE;
  }
}
