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

package org.jppf.server.nio.heartbeat;

import java.io.EOFException;
import java.net.InetSocketAddress;
import java.nio.channels.*;

import javax.net.ssl.*;

import org.jppf.nio.*;
import org.jppf.ssl.SSLHelper;
import org.jppf.utils.*;
import org.slf4j.*;

/**
 * The NIO server that handles heartbeat connections.
 * @author Laurent Cohen
 */
public final class HeartbeatNioServer extends StatelessNioServer {
  /**
   * Logger for this class.
   */
  private static final Logger log = LoggerFactory.getLogger(HeartbeatNioServer.class);
  /**
   * Determines whether the debug level is enabled in the log configuration, without the cost of a method call.
   */
  private static final boolean debugEnabled = log.isDebugEnabled();
  /**
   * The message handler for this server.
   */
  private final HeartbeatMessageHandler messageHandler;

  /**
   * @param identifier the channel identifier for channels handled by this server.
   * @param useSSL determines whether an SSLContext should be created for this server.
   * @throws Exception if any error occurs.
   */
  public HeartbeatNioServer(final int identifier, final boolean useSSL) throws Exception {
    super(identifier, useSSL);
    messageHandler = new HeartbeatMessageHandler(this);
  }

  @Override
  protected void handleRead(final SelectionKey key) throws Exception {
    HeartbeatMessageReader.read((HeartbeatContext) key.attachment());
  }

  @Override
  protected void handleWrite(final SelectionKey key) throws Exception {
    updateInterestOpsNoWakeup(key, SelectionKey.OP_WRITE, false);
    HeartbeatMessageWriter.write((HeartbeatContext) key.attachment());
  }

  @Override
  protected void handleSelectionException(final SelectionKey key, final Exception e) throws Exception {
    final HeartbeatContext context = (HeartbeatContext) key.attachment();
    if (e instanceof CancelledKeyException) {
      if ((context != null) && !context.isClosed()) {
        log.error("error on {} :\n{}", context, ExceptionUtils.getStackTrace(e));
        closeConnection(context);
      }
    } else if (e instanceof EOFException) {
      if (debugEnabled) log.debug("error on {} :\n{}", context, ExceptionUtils.getStackTrace(e));
      closeConnection(context);
    } else {
      log.error("error on {} :\n{}", context, ExceptionUtils.getStackTrace(e));
      if (context != null) closeConnection(context);
    }
  }

  @Override
  public ChannelWrapper<?> accept(final ServerSocketChannel serverSocketChannel, final SocketChannel channel, final SSLHandler sslHandler, final boolean ssl,
    final boolean peer, final Object... params) {
    try {
      if (debugEnabled) log.debug("accepting socketChannel = {}", channel);
      final HeartbeatContext context = createContext(channel, ssl);
      registerChannel(context, channel);
      messageHandler.addChannel(context);
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
    return null;
  }

  /**
   * Create a new channel context.
   * @param channel the associated socket channel.
   * @param ssl whether the connection is secure.
   * @return a new {@link HeartbeatContext} instance.
   * @throws Exception if any error occurs.
   */
  private HeartbeatContext createContext(final SocketChannel channel, final boolean ssl)
    throws Exception {
    final HeartbeatContext context = createNioContext(channel);
    if (debugEnabled) log.debug(String.format("creating channel wrapper for ssl=%b, context=%s", ssl, context));
    context.setSsl(ssl);
    if (ssl) {
      if (debugEnabled) log.debug("creating SSLEngine for {}", context);
      configureSSL(context);
    }
    return context;
  }

  /**
   * Configure SSL for the specified channel accepted by the specified server.
   * @param context the channel to configure.
   * @throws Exception if any error occurs.
   */
  @SuppressWarnings("unchecked")
  private static void configureSSL(final HeartbeatContext context) throws Exception {
    if (debugEnabled) log.debug(String.format("configuring SSL for %s", context));
    final SocketChannel channel = context.getSocketChannel();
    final SSLContext sslContext = SSLHelper.getSSLContext(JPPFIdentifiers.NODE_HEARTBEAT_CHANNEL);
    final InetSocketAddress addr = (InetSocketAddress) channel.getRemoteAddress();
    final SSLEngine engine = sslContext.createSSLEngine(addr.getHostString(), addr.getPort());
    final SSLParameters params = SSLHelper.getSSLParameters();
    engine.setUseClientMode(false);
    engine.setSSLParameters(params);
    if (debugEnabled) log.debug(String.format("created SSLEngine: useClientMode = %b, parameters = %s", engine.getUseClientMode(), engine.getSSLParameters()));
    final SSLHandler sslHandler = new SSLHandlerImpl(channel, engine);
    context.setSSLHandler(sslHandler);
  }

  @Override
  public HeartbeatContext createNioContext(final Object...params) {
    return new HeartbeatContext(this, (SocketChannel) params[0]);
  }


  /**
   * Close the specified channel.
   * @param context the channel to close.
   */
  void closeConnection(final HeartbeatContext context) {
    try {
      messageHandler.removeChannel(context);
      final SelectionKey key = context.getSocketChannel().keyFor(selector);
      if (key != null) {
        key.cancel();
        key.channel().close();
      }
    } catch (final Exception e) {
      log.error("error closing channel {}: {}", context, ExceptionUtils.getStackTrace(e));
    }
  }

  @Override
  public void removeAllConnections() {
    if (!isStopped()) return;
    super.removeAllConnections();
  }

  /**
   * @return the message handler for this server.
   */
  public HeartbeatMessageHandler getMessageHandler() {
    return messageHandler;
  }
}
