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

package org.jppf.server.nio.nodeserver;

import static org.jppf.server.nio.nodeserver.NodeTransition.*;
import static org.jppf.server.protocol.BundleParameter.*;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.List;

import org.jppf.io.DataLocation;
import org.jppf.management.*;
import org.jppf.server.nio.*;
import org.jppf.server.protocol.*;
import org.jppf.server.scheduler.bundle.*;
import org.jppf.utils.*;
import org.slf4j.*;

/**
 * This class implements the state of receiving a task bundle from the node as a
 * response to sending the initial bundle.
 * @author Laurent Cohen
 */
class WaitInitialBundleState extends NodeServerState
{
  /**
   * Logger for this class.
   */
  protected static final Logger log = LoggerFactory.getLogger(WaitInitialBundleState.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  protected static final boolean debugEnabled = log.isDebugEnabled();

  /**
   * Initialize this state.
   * @param server the server that handles this state.
   */
  public WaitInitialBundleState(final NodeNioServer server)
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
  public NodeTransition performTransition(final ChannelWrapper<?> channel) throws Exception  {
    AbstractNodeContext context = (AbstractNodeContext) channel.getContext();
    //if (debugEnabled) log.debug("exec() for " + channel);
    if (context.getMessage() == null) context.setMessage(context.newMessage());
    if (context.readMessage(channel)) {
      if (debugEnabled) log.debug("read bundle for " + channel + " done");
      Pair<JPPFTaskBundle, List<DataLocation>> received = context.deserializeBundle();
      JPPFTaskBundle bundle = received.first();
      boolean offline =  (bundle.getParameter(NODE_OFFLINE, false));
      if (offline) ((RemoteNodeContext) context).setOffline(true);
      else if (!bundle.isHandshake()) throw new IllegalStateException("handshake bundle expected.");

      String uuid = bundle.getParameter(NODE_UUID_PARAM);
      context.setUuid(uuid);
      Bundler bundler = server.getBundler().copy();
      JPPFSystemInformation systemInfo = bundle.getParameter(SYSTEM_INFO_PARAM);
      if (systemInfo != null) {
        context.setNodeInfo(systemInfo);
        if (bundler instanceof NodeAwareness) ((NodeAwareness) bundler).setNodeConfiguration(systemInfo);
      } else if (debugEnabled) log.debug("no system info received for node " + channel);

      if (bundler instanceof ContextAwareness) ((ContextAwareness) bundler).setJPPFContext(server.getJPPFContext());
      bundler.setup();
      context.setBundler(bundler);
      boolean isPeer = bundle.getParameter(IS_PEER, false);
      context.setPeer(isPeer);
      if (JPPFConfiguration.getProperties().getBoolean("jppf.management.enabled", true)) {
        if ((uuid != null) && !bundle.getParameter(NODE_OFFLINE, false)) {
          String host = getChannelHost(channel);
          int port = bundle.getParameter(NODE_MANAGEMENT_PORT_PARAM, -1);
          boolean sslEnabled = channel.isLocal() ? false : context.getSSLHandler() != null;
          byte type = isPeer ? JPPFManagementInfo.PEER : JPPFManagementInfo.NODE;
          if (channel.isLocal()) type |= JPPFManagementInfo.LOCAL;
          JPPFManagementInfo info = new JPPFManagementInfo(host, port, uuid, type, sslEnabled);
          if (systemInfo != null) info.setSystemInfo(systemInfo);
          context.setManagementInfo(info);
        }
      }
      server.nodeConnected(context);
      // make sure the context is reset so as not to resubmit the last bundle executed by the node.
      if (bundle.getParameter(NODE_OFFLINE_OPEN_REQUEST, false)) return processOfflineReopen(received, context);
      context.setMessage(null);
      context.setBundle(null);
      return context.isPeer() ? TO_IDLE_PEER : TO_IDLE;
    }
    return TO_WAIT_INITIAL;
  }

  /**
   * Process a request from the node to send the results of a job executed offline.
   * @param received holds the received bundle along with the tasks.
   * @param context the context associated witht he node channel.
   * @return the {@link TO_WAITING_RESULTS} transition name.
   * @throws Exception if any error occurs.
   */
  private NodeTransition processOfflineReopen(final Pair<JPPFTaskBundle, List<DataLocation>> received, final AbstractNodeContext context) throws Exception {
    JPPFTaskBundle bundle = received.first();
    String jobUuid = bundle.getParameter(JOB_UUID);
    long id = bundle.getParameter(NODE_BUNDLE_ID);
    ServerTaskBundleNode nodeBundle = server.getOfflineNodeHandler().removeNodeBundle(jobUuid, id);
    if (debugEnabled) log.debug("processing offline reopen with jobUuid=" + jobUuid + ", id=" + id + ", nodeBundle=" + nodeBundle + ", node=" + context.getChannel());
    context.setBundle(nodeBundle);
    WaitingResultsState wrs = (WaitingResultsState) server.getFactory().getState(NodeState.WAITING_RESULTS);
    return wrs.process(received, context);
  }

  /**
   * Extract the remote host name from the specified channel.
   * @param channel the channel that carries the host information.
   * @return the remote host name as a string.
   * @throws Exception if any error occurs.
   */
  private String getChannelHost(final ChannelWrapper<?> channel) throws Exception {
    if (channel instanceof SelectionKeyWrapper) {
      SelectionKeyWrapper skw = (SelectionKeyWrapper) channel;
      SocketChannel ch = (SocketChannel) skw.getChannel().channel();
      return  ((InetSocketAddress) (ch.getRemoteAddress())).getHostString();
    }
    return null;
  }
}
