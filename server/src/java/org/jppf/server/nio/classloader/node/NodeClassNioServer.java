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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.jppf.nio.*;
import org.jppf.server.JPPFDriver;
import org.jppf.server.nio.classloader.ClassNioServer;
import org.jppf.server.nio.nodeserver.*;
import org.jppf.utils.*;
import org.jppf.utils.concurrent.ThreadUtils;
import org.jppf.utils.configuration.JPPFProperties;
import org.slf4j.*;

/**
 * Instances of this class serve class loading requests from the JPPF nodes.
 * @author Laurent Cohen
 */
public class NodeClassNioServer extends ClassNioServer<NodeClassState, NodeClassTransition> {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(NodeClassNioServer.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);
  /**
   * The thread polling the local channel.
   */
  protected ChannelSelectorThread selectorThread = null;
  /**
   * The local channel, if any.
   */
  protected ChannelWrapper<?> localChannel = null;
  /**
   * Mapping of channels to their uuid.
   */
  //protected final Map<String, ChannelWrapper<?>> nodeConnections = new HashMap<>();
  protected final Map<String, ChannelWrapper<?>> nodeConnections = new ConcurrentHashMap<>();

  /**
   * Initialize this class server.
   * @param driver reference to the driver.
   * @param useSSL determines whether an SSLContext should be created for this server.
   * @throws Exception if the underlying server socket can't be opened.
   */
  public NodeClassNioServer(final JPPFDriver driver, final boolean useSSL) throws Exception {
    super(JPPFIdentifiers.NODE_CLASSLOADER_CHANNEL, driver, useSSL);
  }

  /**
   * Initialize the local channel connection.
   * @param localChannel the local channel to use.
   */
  public void initLocalChannel(final ChannelWrapper<?> localChannel) {
    if (JPPFConfiguration.get(JPPFProperties.LOCAL_NODE_ENABLED)) {
      this.localChannel = localChannel;
      final ChannelSelector channelSelector = new LocalChannelSelector(localChannel);
      localChannel.setSelector(channelSelector);
      selectorThread = new ChannelSelectorThread(channelSelector, this);
      localChannel.setInterestOps(0);
      ThreadUtils.startThread(selectorThread, "ClassChannelSelector");
      postAccept(localChannel);
    }
  }

  @Override
  protected NioServerFactory<NodeClassState, NodeClassTransition> createFactory() {
    return new NodeClassServerFactory(this);
  }

  @Override
  public NioContext<NodeClassState> createNioContext(final Object...params) {
    return new NodeClassContext();
  }

  @Override
  public void postAccept(final ChannelWrapper<?> channel) {
    try {
      if (debugEnabled) log.debug("accepting channel {}", channel);
      synchronized(channel) {
        transitionManager.transitionChannel(channel, NodeClassTransition.TO_WAITING_INITIAL_NODE_REQUEST);
        if (transitionManager.checkSubmitTransition(channel)) transitionManager.submitTransition(channel);
      }
    } catch (final Exception e) {
      if (debugEnabled) log.debug(e.getMessage(), e);
      else log.warn(ExceptionUtils.getMessage(e));
      closeConnection(channel);
    }
  }

  /**
   * Get a channel from its uuid.
   * @param uuid the uuid key to look up in the the map.
   * @return channel the corresponding channel.
   */
  public ChannelWrapper<?> getNodeConnection(final String uuid) {
    return nodeConnections.get(uuid);
  }

  /**
   * Put the specified uuid / channel pair into the uuid map.
   * @param uuid the uuid key to add to the map.
   * @param channel the corresponding channel.
   */
  public void addNodeConnection(final String uuid, final ChannelWrapper<?> channel) {
    if (debugEnabled) log.debug("adding node connection: uuid=" + uuid + ", channel=" + channel);
    nodeConnections.put(uuid, channel);
  }

  /**
   * Remove the specified uuid entry from the uuid map.
   * @param uuid the uuid key to remove from the map.
   * @return channel the corresponding channel.
   */
  public ChannelWrapper<?> removeNodeConnection(final String uuid) {
    if (debugEnabled) log.debug("removing node connection: uuid=" + uuid);
    return nodeConnections.remove(uuid);
  }

  /**
   * Close the specified connection.
   * @param channel the channel representing the connection.
   */
  public static void closeConnection(final ChannelWrapper<?> channel) {
    if (channel == null) {
      log.warn("attempt to close null channel - skipping this step");
      return;
    }
    final NodeClassNioServer server = JPPFDriver.getInstance().getNodeClassServer();
    final NodeClassContext context = (NodeClassContext) channel.getContext();
    final String uuid = context.getUuid();
    if (uuid != null) server.removeNodeConnection(uuid);
    try {
      channel.close();
    } catch(final Exception e) {
      if (debugEnabled) log.debug(e.getMessage(), e);
      else log.warn(e.getMessage());
    }
    if (context.isPeer()) {
      try {
        final NodeNioServer jobNodeServer = JPPFDriver.getInstance().getNodeNioServer();
        final AbstractNodeContext ctx = jobNodeServer.getConnection(uuid);
        if (ctx != null) ctx.handleException(ctx.getChannel(), null);
      } catch(final Exception e) {
        if (debugEnabled) log.debug(e.getMessage(), e);
        else log.warn(e.getMessage());
      }
    }
  }

  /**
   * Called when the node failed to respond to a heartbeat message.
   * @param channel the channel to close.
   */
  public void connectionFailed(final ChannelWrapper<?> channel) {
    if (channel != null) {
      if (debugEnabled) log.debug("about to close channel = " + channel + " with uuid = " + channel.getContext().getUuid());
      closeConnection(channel);
    }
  }

  /**
   * Close and remove all connections accepted by this server.
   * @see org.jppf.nio.NioServer#removeAllConnections()
   */
  @Override
  public synchronized void removeAllConnections() {
    if (!isStopped()) return;
    final List<ChannelWrapper<?>> list  = new ArrayList<>(nodeConnections.values());
    nodeConnections.clear();
    super.removeAllConnections();
    for (ChannelWrapper<?> channel: list) {
      try {
        closeConnection(channel);
      } catch (final Exception e) {
        log.error("error closing channel {} : {}", channel, ExceptionUtils.getStackTrace(e));
      }
    }
  }

  @Override
  public boolean isIdle(final ChannelWrapper<?> channel) {
    return NodeClassState.IDLE_NODE == channel.getContext().getState();
  }

  @Override
  public List<ChannelWrapper<?>> getAllConnections() {
    final List<ChannelWrapper<?>> list = super.getAllConnections();
    if (localChannel != null) list.add(localChannel);
    return list;
  }
}
