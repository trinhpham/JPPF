/*
 * Java Parallel Processing Framework.
 * Copyright (C) 2005-2008 JPPF Team.
 * http://www.jppf.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jppf.server.nio.multiplexer.generic;

import static org.jppf.server.nio.multiplexer.generic.MultiplexerTransition.*;
import static org.jppf.utils.StringUtils.getRemoteHost;

import java.nio.ByteBuffer;
import java.nio.channels.*;

import org.apache.commons.logging.*;

/**
 * State of receiving data on a channel.
 * When data is received, it is forwarded to the linked channel, so that it can be
 * sent to the other side of the multiplexer connection.
 * @author Laurent Cohen
 */
public class ReceivingState extends MultiplexerServerState
{
	/**
	 * Logger for this class.
	 */
	private static Log log = LogFactory.getLog(ReceivingState.class);
	/**
	 * Determines whether DEBUG logging level is enabled.
	 */
	private static boolean debugEnabled = log.isDebugEnabled();

	/**
	 * Initialize this state.
	 * @param server the server that handles this state.
	 */
	public ReceivingState(MultiplexerNioServer server)
	{
		super(server);
	}

	/**
	 * Execute the action associated with this channel state.
	 * @param key the selection key corresponding to the channel and selector for this state.
	 * @return a state transition as an <code>NioTransition</code> instance.
	 * @throws Exception if an error occurs while transitioning to another state.
	 * @see org.jppf.server.nio.NioState#performTransition(java.nio.channels.SelectionKey)
	 */
	public MultiplexerTransition performTransition(SelectionKey key) throws Exception
	{
		SelectableChannel channel = key.channel();
		MultiplexerContext context = (MultiplexerContext) key.attachment();
		if (debugEnabled) log.debug("exec() for " + getRemoteHost(channel));
		ByteBuffer message = context.readMultiplexerMessage((ReadableByteChannel) channel);
		if (message != null)
		{
			if (debugEnabled) log.debug("read message for " + getRemoteHost(channel) + " done");
			SelectionKey linkedKey = context.getLinkedKey();
			MultiplexerContext linkedContext = (MultiplexerContext) linkedKey.attachment();
			linkedContext.addPendingMessage(new ByteBufferWrapper(message, context.newReadMessageCount()));
			if (!MultiplexerTransition.TO_SENDING.equals(linkedContext.getState()))
				server.transitionChannel(linkedKey, MultiplexerTransition.TO_SENDING);
			if (!context.isEof()) return TO_SENDING_OR_RECEIVING;
		}
		if (context.isEof())
		{
			context.setEof(false);
			return TO_IDLE;
		}
		return TO_RECEIVING;
	}
}
