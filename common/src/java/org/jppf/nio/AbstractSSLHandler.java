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

package org.jppf.nio;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.*;

import javax.net.ssl.*;

import org.jppf.ssl.SSLHelper2;
import org.jppf.utils.*;
import org.jppf.utils.concurrent.JPPFThreadFactory;
import org.jppf.utils.configuration.JPPFProperties;
import org.slf4j.*;

/**
 * Wrapper for an {@link SSLEngine} and an associated channel.
 * @exclude
 */
public abstract class AbstractSSLHandler implements SSLHandler {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(AbstractSSLHandler.class);
  /**
   * Determines whether DEBUG logging level is enabled.
   */
  private static boolean debugEnabled = log.isDebugEnabled();
  /**
   * Determines whether TRACE logging level is enabled.
   */
  private static boolean traceEnabled = log.isTraceEnabled();
  /**
   * The socket channel from which data is read or to which data is written.
   */
  final SocketChannel channel;
  /**
   * The SSLEngine performs the SSL-related operations before sending data/after receiving data.
   */
  final SSLEngine sslEngine;
  /**
   * Thread pool used to executed the delegated tasks generated by the SSL handshake.
   */
  static final ExecutorService executor = createExecutor();
  /**
   * The data the application is sending.
   */
  final ByteBuffer appSendBuffer;
  /**
   * The SSL data sent by the <code>SSLEngine</code>.
   */
  final ByteBuffer netSendBuffer;
  /**
   * The data the application is receiving.
   */
  final ByteBuffer appReceiveBuffer;
  /**
   * The data recevied yb the <code>SSLEngine</code>.
   */
  final ByteBuffer netReceiveBuffer;
  /**
   * Count of bytes read from the channel, in the scope of a {@link #read()} invocation.
   * This count includes all the SSL overhead: encrypted data, handshaking, renegotiation, etc.
   */
  long channelReadCount;
  /**
   * Count of bytes written to the channel, in the scope of a {@link #write()} invocation.
   * This count includes all the SSL overhead: encrypted data, handshaking, renegotiation, etc.
   */
  long channelWriteCount;

  /**
   * Instantiate this SSLHandler with the specified channel and SSL engine.
   * @param channel the channel from which data is read or to which data is written.
   * @param sslEngine performs the SSL-related operations before sending data/after receiving data.
   * @throws Exception if any error occurs.
   */
  public AbstractSSLHandler(final ChannelWrapper<?> channel, final SSLEngine sslEngine) throws Exception {
    this(channel.getSocketChannel(), sslEngine);
  }

  /**
   * Instantiate this SSLHandler with the specified channel and SSL engine.
   * @param channel the channel from which data is read or to which data is written.
   * @param sslEngine performs the SSL-related operations before sending data/after receiving data.
   * @throws Exception if any error occurs.
   */
  public AbstractSSLHandler(final SocketChannel channel, final SSLEngine sslEngine) throws Exception {
    this.channel = channel;
    this.sslEngine = sslEngine;
    final SSLSession session = sslEngine.getSession();
    this.appSendBuffer = ByteBuffer.wrap(new byte[session.getApplicationBufferSize()]);
    this.netSendBuffer = ByteBuffer.wrap(new byte[session.getPacketBufferSize() + 50]);
    this.appReceiveBuffer = ByteBuffer.wrap(new byte[session.getApplicationBufferSize()]);
    this.netReceiveBuffer = ByteBuffer.wrap(new byte[session.getPacketBufferSize() + 50]);
    if (debugEnabled) log.debug(String.format("creating SSLHandler for channel %s, useClientMode=%b, ssl params = [%s]",
      channel, sslEngine.getUseClientMode(), SSLHelper2.dumpSSLParameters(sslEngine.getSSLParameters())));
  }

  @Override
  public ByteBuffer getAppReceiveBuffer() {
    return appReceiveBuffer;
  }

  @Override
  public ByteBuffer getAppSendBuffer() {
    return appSendBuffer;
  }

  @Override
  public ByteBuffer getNetReceiveBuffer() {
    return netReceiveBuffer;
  }

  @Override
  public ByteBuffer getNetSendBuffer() {
    return netSendBuffer;
  }

  /**
   * Print the state of all buffers to a string.
   * This method is intended for logging and debugging purposes.
   * @return a string representation of the buffers states.
   */
  String printBuffers() {
    final StringBuilder sb = toString("appSendBuf=", new StringBuilder(), appSendBuffer);
    toString(", netSendBuf=", sb, netSendBuffer);
    toString(", appReceiveBuf=", sb, appReceiveBuffer);
    return toString(", netReceiveBuf=", sb, netReceiveBuffer).toString();
  }

  /**
   * Print the state of all buffers to a string.
   * This method is intended for logging and debugging purposes.
   * @return a string representation of the buffers states.
   */
  String printReceiveBuffers() {
    final StringBuilder sb = toString("appReceiveBuf=", new StringBuilder(), appReceiveBuffer);
    return toString(", netReceiveBuf=", sb, netReceiveBuffer).toString();
  }

  /**
   * Print the state of all send buffers to a string.
   * This method is intended for logging and debugging purposes.
   * @return a string representation of the send buffers states.
   */
  String printSendBuffers() {
    final StringBuilder sb = toString("appSendBuf=", new StringBuilder(), appSendBuffer);
    return toString(", netSendBuf=", sb, netSendBuffer).toString();
  }

  @Override
  public long getChannelReadCount() {
    return channelReadCount;
  }

  @Override
  public long getChannelWriteCount() {
    return channelWriteCount;
  }


  /**
   * Run delegated tasks for the handshake.
   */
  void performDelegatedTasks() {
    Runnable delegatedTask;
    final CompletionService<?> completer = new ExecutorCompletionService<>(executor);
    int total = 0;
    while ((delegatedTask = sslEngine.getDelegatedTask()) != null) {
      if (traceEnabled) log.trace("running delegated task " + delegatedTask);
      completer.submit(delegatedTask, null);
      total++;
    }
    for (int i=0; i<total; i++) {
      try {
        completer.take();
      } catch (final Exception e) {
        if (traceEnabled) log.trace(e.getMessage(), e);
        else log.warn(ExceptionUtils.getMessage(e));
      }
    }
  }

  /**
   * Create the executor which runs the SSLEngine delegated tasks.
   * @return an {@link ExecutorService} instance.
   */
  private static ExecutorService createExecutor() {
    final int n = JPPFConfiguration.get(JPPFProperties.SSL_THREAD_POOL_SIZE);
    final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    final JPPFThreadFactory tf = new JPPFThreadFactory("SSLDelegatedTasks");
    final ThreadPoolExecutor exec = new ThreadPoolExecutor(n, n, 10L, TimeUnit.SECONDS, queue, tf);
    exec.allowCoreThreadTimeOut(true);
    return exec;
  }

  @Override
  public String toString() {
    return new StringBuilder(getClass().getSimpleName()).append('[')
      .append("channel=").append(channel)
      .append(", channelReadCount=").append(channelReadCount)
      .append(", channelWriteCount=").append(channelWriteCount)
      .append(']')
      .toString();
  }

  @Override
  public SSLEngine getSslEngine() {
    return sslEngine;
  }

  /**
   * 
   * @param prefix .
   * @param sb .
   * @param buf .
   * @return .
   */
  static StringBuilder toString(final String prefix, final StringBuilder sb, final ByteBuffer buf) {
    return sb.append(prefix).append(buf.getClass().getSimpleName()).append('[')
      .append("pos=").append(buf.position())
      .append(", lim=").append(buf.limit())
      .append(", cap=").append(buf.capacity())
      .append(']');
  }
}
