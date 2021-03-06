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

package org.jppf.server.node.remote;

import static org.jppf.node.protocol.BundleParameter.*;

import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;

import org.jppf.*;
import org.jppf.comm.socket.SocketWrapper;
import org.jppf.io.*;
import org.jppf.node.protocol.*;
import org.jppf.serialization.ObjectSerializer;
import org.jppf.server.node.*;
import org.jppf.utils.LoggingUtils;
import org.slf4j.*;

/**
 * This class performs the I/O operations requested by the JPPFNode, for reading the task bundles and sending the results back.
 * @author Laurent Cohen
 */
public class RemoteNodeIO extends AbstractNodeIO {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(RemoteNodeIO.class);
  /**
   * Determines whether the debug level is enabled in the logging configuration, without the cost of a method call.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);
  /**
   * Determines whether the trace level is enabled in the logging configuration, without the cost of a method call.
   */
  private static boolean traceEnabled = log.isTraceEnabled();

  /**
   * Initialize this TaskIO with the specified node.
   * @param node the node who owns this TaskIO.
   */
  public RemoteNodeIO(final JPPFNode node) {
    super(node);
  }

  @Override
  protected Object[] deserializeObjects() throws Exception {
    final ObjectSerializer ser = node.getHelper().getSerializer();
    if (debugEnabled) log.debug("waiting for next request. Serializer = " + ser + " (class loader = " + ser.getClass().getClassLoader() + ")");
    Object[] result = null;
    TaskBundle bundle = null;
    node.setReading(true);
    try {
      bundle = (TaskBundle) IOHelper.unwrappedData(getSocketWrapper(), ser);
      node.setExecuting(true);
      if (debugEnabled) log.debug("got bundle " + bundle);
      if (!bundle.isHandshake()) node.getExecutionManager().setBundle(bundle);
      result = deserializeObjects(bundle);
    } finally {
      node.setReading(false);
    }
    if (node.isOffline() && !bundle.isHandshake()) waitChannelClosed(getSocketWrapper());
    return result;
  }

  @Override
  protected Object[] deserializeObjects(final TaskBundle bundle) throws Exception {
    final int count = bundle.getTaskCount();
    //List<Object> list = new ArrayList<>(count + 2);
    final Object[] list = new Object[count + 2];
    list[0] = bundle;
    try {
      initializeBundleData(bundle);
      if (debugEnabled) log.debug("bundle task count = " + count + ", handshake = " + bundle.isHandshake());
      if (!bundle.isHandshake()) {
        //JPPFRemoteContainer cont = (JPPFRemoteContainer) node.getContainer(bundle.getUuidPath().getList());
        final boolean clientAccess = !bundle.getParameter(FROM_PERSISTENCE, false);
        final JPPFRemoteContainer cont = (JPPFRemoteContainer) node.getClassLoaderManager().getContainer(bundle.getUuidPath().getList(), clientAccess, (Object[]) null);
        cont.setNodeConnection((RemoteNodeConnection) node.getNodeConnection());
        cont.getClassLoader().setRequestUuid(bundle.getUuid());
        if (!node.isOffline() && !bundle.getSLA().isRemoteClassLoadingEnabled()) cont.getClassLoader().setRemoteClassLoadingDisabled(true);
        node.getLifeCycleEventHandler().fireJobHeaderLoaded(bundle, cont.getClassLoader());
        cont.deserializeObjects(list, 1+count, node.getExecutionManager().getExecutor());
      }
      else  getSocketWrapper().receiveBytes(0); // skip null data provider
      if (debugEnabled) log.debug("got all data");
    } catch(final Throwable t) {
      log.error("Exception occurred while deserializing the tasks", t);
      bundle.setTaskCount(0);
      bundle.setParameter(NODE_EXCEPTION_PARAM, t);
    }
    return list;
  }

  /**
   * Performs the actions required if reloading the classes is necessary.
   * @throws Exception if any error occurs.
   */
  @Override
  protected void handleReload() throws Exception {
    node.setClassLoader(null);
    node.initHelper();
    getSocketWrapper().setSerializer(node.getHelper().getSerializer());
  }

  @Override
  protected void sendResults(final TaskBundle bundle, final List<Task<?>> tasks) throws Exception {
    if (debugEnabled) log.debug("writing results for " + bundle);
    //System.out.println("writing results for " + bundle);
    final SocketWrapper socketWrapper = getSocketWrapper();
    if (socketWrapper == null) throw new SocketException("no connection to the server");
    final ExecutorService executor = node.getExecutionManager().getExecutor();
    finalizeBundleData(bundle, tasks);
    final List<Future<DataLocation>> futureList = new ArrayList<>(tasks.size() + 1);
    final JPPFContainer cont = node.getContainer(bundle.getUuidPath().getList());
    futureList.add(executor.submit(new ObjectSerializationTask(bundle, cont.getSerializer(), cont.getClassLoader())));
    for (Task<?> task : tasks) futureList.add(executor.submit(new ObjectSerializationTask(task, cont.getSerializer(), cont.getClassLoader())));
    final OutputDestination dest = new SocketWrapperOutputDestination(socketWrapper);
    int count = 0;
    for (final Future<DataLocation> f: futureList) {
      final DataLocation dl = f.get();
      if (traceEnabled) log.trace("writing "  + (count == 0 ? "header" : "task[" + count + ']') + " with size = " + dl.getSize());
      IOHelper.writeData(dl, dest);
      count++;
    }
    socketWrapper.flush();
    if (debugEnabled) log.debug("wrote full results");
  }

  /**
   * Wait until the connection is closed by the other end.
   * @param socketWrapper the connection to check.
   */
  private void waitChannelClosed(final SocketWrapper socketWrapper) {
    try {
      socketWrapper.readInt();
    } catch (@SuppressWarnings("unused") final Exception ignore) {
    } catch (final Error e) {
      if (debugEnabled) log.debug("error closing socket: ", e);
    }
    if (traceEnabled) log.trace("server closed the connection");
    try {
      node.closeDataChannel();
    } catch (@SuppressWarnings("unused") final Exception ignore) {
    } catch (final Error e) {
      if (debugEnabled) log.debug("error closing data channel: ", e);
    }
    if (traceEnabled) log.trace("closed the data channel");
  }

  /**
   * Get the socket wrapper associated with the node connection.
   * @return a {@link SocketWrapper} instance.
   */
  private SocketWrapper getSocketWrapper() {
    final SocketWrapper socketWrapper = ((RemoteNodeConnection) node.getNodeConnection()).getChannel();
    // happens when an android node gets in suspended state due to low battery charge, while the node is reading a job from the server
    if ((socketWrapper == null) && node.isSuspended()) throw new JPPFSuspendedNodeException("node connection was closed by another thread");
    return socketWrapper;
  }
}
