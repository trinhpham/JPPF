/*
 * JPPF.
 * Copyright (C) 2005-2013 JPPF Team.
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

import static org.jppf.server.nio.classloader.ClassTransition.*;
import static org.jppf.utils.StringUtils.build;

import org.jppf.classloader.JPPFResourceWrapper;
import org.jppf.server.nio.*;
import org.jppf.server.nio.classloader.*;
import org.slf4j.*;

/**
 * This class represents the state of waiting for the response from a provider.
 * @author Laurent Cohen
 */
class WaitingProviderResponseState extends ClassServerState
{
  /**
   * Logger for this class.
   */
  private static final Logger log = LoggerFactory.getLogger(WaitingProviderResponseState.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static final boolean debugEnabled = log.isDebugEnabled();
  /**
   * The class cache.
   */
  private final ClassCache classCache = driver.getInitializer().getClassCache();

  /**
   * Initialize this state with a specified NioServer.
   * @param server the NioServer this state relates to.
   */
  public WaitingProviderResponseState(final ClassNioServer server)
  {
    super(server);
  }

  /**
   * Execute the action associated with this channel state.
   * @param channel the selection key corresponding to the channel and selector for this state.
   * @return a state transition as an <code>NioTransition</code> instance.
   * @throws Exception if an error occurs while transitioning to another state.
   * @see org.jppf.server.nio.NioState#performTransition(java.nio.channels.SelectionKey)
   */
  @Override
  @SuppressWarnings("unchecked")
  public ClassTransition performTransition(final ChannelWrapper<?> channel) throws Exception
  {
    ClassContext context = (ClassContext) channel.getContext();
    if (context.readMessage(channel))
    {
      ResourceRequest request = context.getCurrentRequest();
      JPPFResourceWrapper resource = context.deserializeResource();
      if (debugEnabled) log.debug(build("read response from provider: ", channel, ", sending to node ", request.getChannel(), ", resource: ", resource.getName()));
      if ((resource.getDefinition() != null) && ClassContext.isSingleResource(resource))
        classCache.setCacheContent(resource.getUuidPath().getFirst(), resource.getName(), resource.getDefinition());
      resource.setState(JPPFResourceWrapper.State.NODE_RESPONSE);

      if (debugEnabled) log.debug(build("client ", channel, " sending response ", resource, " to node ", request.getChannel()));
      context.sendNodeResponse(request, resource);
      context.setCurrentRequest(null);
      context.setMessage(null);
      context.setResource(null);
      return context.hasPendingRequest() ? TO_SENDING_PROVIDER_REQUEST : TO_IDLE_PROVIDER;
    }
    return TO_WAITING_PROVIDER_RESPONSE;
  }
}
