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

import java.util.*;
import java.util.concurrent.locks.*;

import org.jppf.nio.*;
import org.jppf.server.JPPFDriver;
import org.jppf.server.nio.classloader.*;
import org.jppf.utils.*;
import org.jppf.utils.collections.*;
import org.slf4j.*;

/**
 * Instances of this class serve class loading requests from the JPPF nodes.
 * @author Laurent Cohen
 */
public class ClientClassNioServer extends ClassNioServer<ClientClassState, ClientClassTransition> {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(ClientClassNioServer.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);
  /**
   * A mapping of the remote resource provider connections handled by this socket server, to their unique uuid.<br>
   * Provider connections represent connections form the clients only. The mapping to a uuid is required to determine in
   * which application classpath to look for the requested resources.
   */
  protected final CollectionMap<String, ChannelWrapper<?>> providerConnections = new VectorHashtable<>();
  /**
   * Maintainsa a mapping of requested classes to the nodes that requested them.
   */
  private final CollectionMap<CacheClassKey, ResourceRequest> requestMap = new ArrayListHashMap<>();
  /**
   * Usd to synchronize access to the requests map.
   */
  private final Lock lockRequests = new ReentrantLock();

  /**
   * Initialize this class server.
   * @param driver reference to the driver.
   * @param useSSL determines whether an SSLContext should be created for this server.
   * @throws Exception if the underlying server socket can't be opened.
   */
  public ClientClassNioServer(final JPPFDriver driver, final boolean useSSL) throws Exception {
    super(JPPFIdentifiers.CLIENT_CLASSLOADER_CHANNEL, driver, useSSL);
    if (debugEnabled) log.debug("created {}", this);
  }

  @Override
  protected NioServerFactory<ClientClassState, ClientClassTransition> createFactory() {
    return new ClientClassServerFactory(this);
  }

  @Override
  public NioContext<ClientClassState> createNioContext(final Object...params) {
    return new ClientClassContext();
  }

  @Override
  public void postAccept(final ChannelWrapper<?> channel) {
    if (debugEnabled) log.debug("performing postAccept of {}", channel);
    try {
      if (!channel.getContext().isPeer()) {
        synchronized(channel) {
          transitionManager.transitionChannel(channel, ClientClassTransition.TO_WAITING_INITIAL_PROVIDER_REQUEST);
          if (transitionManager.checkSubmitTransition(channel)) transitionManager.submitTransition(channel);
        }
      }
    } catch (final Exception e) {
      if (debugEnabled) log.debug(e.getMessage(), e);
      else log.warn(ExceptionUtils.getMessage(e));
      closeConnection(channel);
    }
    if (debugEnabled) log.debug("finished postAccept of {}", channel);
  }

  /**
   * Close the specified connection.
   * @param channel the channel representing the connection.
   */
  public static void closeConnection(final ChannelWrapper<?> channel) {
    closeConnection(channel, true);
  }

  /**
   * Close the specified connection.
   * @param channel the channel representing the connection.
   * @param removeJobConnection <code>true</code> to remove the corresponding job connection as well, <code>false</code> otherwise.
   */
  public static void closeConnection(final ChannelWrapper<?> channel, final boolean removeJobConnection) {
    if (channel == null) {
      log.warn("attempt to close null channel - skipping this step");
      return;
    }
    final ClientClassNioServer server = JPPFDriver.getInstance().getClientClassServer();
    final ClientClassContext context = (ClientClassContext) channel.getContext();
    if (debugEnabled) log.debug("closing {}", context);
    final String uuid = context.getUuid();
    if (uuid != null) server.removeProviderConnection(uuid, channel);
    else if (debugEnabled) log.debug("null uuid for {}", context);
    try {
      channel.close();
    } catch(final Exception e) {
      if (debugEnabled) log.debug(e.getMessage(), e);
      else log.warn(e.getMessage());
    }
    if (removeJobConnection) {
      final String connectionUuid = context.getConnectionUuid();
      JPPFDriver.getInstance().getClientNioServer().closeClientConnection(connectionUuid);
    }
  }

  /**
   * Add a provider connection to the map of existing available providers.
   * @param uuid the provider uuid as a string.
   * @param channel the provider's communication channel.
   */
  public void addProviderConnection(final String uuid, final ChannelWrapper<?> channel) {
    if (debugEnabled) log.debug("adding provider connection: uuid=" + uuid + ", channel=" + channel);
    providerConnections.putValue(uuid, channel);
  }

  /**
   * Add a provider connection to the map of existing available providers.
   * @param uuid the provider uuid as a string.
   * @param channel the provider's communication channel.
   */
  public void removeProviderConnection(final String uuid, final ChannelWrapper<?> channel) {
    if (debugEnabled) log.debug("removing provider connection: uuid=" + uuid + ", channel=" + channel);
    providerConnections.removeValue(uuid, channel);
  }

  /**
   * Get all the provider connections for the specified client uuid.
   * @param uuid the uuid of the client for which to get connections.
   * @return a list of connection channels.
   */
  public List<ChannelWrapper<?>> getProviderConnections(final String uuid) {
    final Collection<ChannelWrapper<?>> channels = providerConnections.getValues(uuid);
    return channels == null ? null : new ArrayList<>(channels);
  }

  /**
   * Remove all the provider connections for the specified client uuid.
   * @param uuid the uuid of the client for which to remove connections.
   */
  public void removeProviderConnections(final String uuid) {
    final Collection<ChannelWrapper<?>> channels = providerConnections.removeKey(uuid);
    if (channels != null) {
      for (final ChannelWrapper<?> channel: channels) {
        try {
          closeConnection(channel);
        } catch (final Exception e) {
          log.error("error closing channel {} : {}", channel, ExceptionUtils.getStackTrace(e));
        }
      }
    }
  }

  /**
   * Get all the provider connections handled by this server.
   * @return a list of connection channels.
   */
  @Override
  public List<ChannelWrapper<?>> getAllConnections() {
    final Collection<ChannelWrapper<?>> channels = providerConnections.allValues();
    return channels == null ? null : new ArrayList<>(channels);
  }

  /**
   * Close and remove all connections accepted by this server.
   * @see org.jppf.nio.NioServer#removeAllConnections()
   */
  @Override
  public synchronized void removeAllConnections() {
    if (!isStopped()) return;
    final List<ChannelWrapper<?>> list = providerConnections.allValues();
    providerConnections.clear();
    super.removeAllConnections();
    if (list != null) {
      for (final ChannelWrapper<?> channel: list) {
        try {
          closeConnection(channel);
        } catch (final Exception e) {
          log.error("error closing channel {} : {}", channel, ExceptionUtils.getStackTrace(e));
        }
      }
    }
  }

  @Override
  public boolean isIdle(final ChannelWrapper<?> channel) {
    return ClientClassState.IDLE_PROVIDER == channel.getContext().getState();
  }

  /**
   * Add the specified request for the specified client.
   * @param uuid the uuid of the client to send the request to.
   * @param request the request to send.
   * @return <code>true</code> if a request for the same client and resource name already exists, <code>false</code> otherwise.
   */
  public boolean addResourceRequest(final String uuid, final ResourceRequest request) {
    final CacheClassKey key = new CacheClassKey(uuid, AbstractClassContext.getResourceName(request.getResource()));
    if (debugEnabled) log.debug("adding resource request for {}", key);
    lockRequests.lock();
    try {
      final boolean result = requestMap.containsKey(key);
      requestMap.putValue(key, request);
      return result;
    } finally {
      lockRequests.unlock();
    }
  }

  /**
   * Remove all requests for the specified client and resource name.
   * @param uuid the uuid of the client to send the request to.
   * @param name the name of the resource.
   * @return <code>true</code> if a request for the same client and resource name already exists, <code>false</code> otherwise.
   */
  public Collection<ResourceRequest> removeResourceRequest(final String uuid, final String name) {
    final CacheClassKey key = new CacheClassKey(uuid, name);
    if (debugEnabled) log.debug("removing resource request for {}", key);
    lockRequests.lock();
    try {
      final Collection<ResourceRequest> c = requestMap.removeKey(key);
      if (debugEnabled) log.debug("removing resource request for {} : {} requests", key, c == null ? "null" : c.size());
      return c;
    } finally {
      lockRequests.unlock();
    }
  }
}
