/*
 * JPPF.
 * Copyright (C) 2005-2011 JPPF Team.
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

package org.jppf.server.nio.client;

import static org.jppf.server.nio.client.ClientState.*;
import static org.jppf.server.nio.client.ClientTransition.*;

import java.util.*;

import org.jppf.server.nio.*;

/**
 * Utility class used to specify the possible states of a node server connection, as well as the possible
 * transitions between those states.
 * @author Laurent Cohen
 */
final class ClientServerFactory extends NioServerFactory<ClientState, ClientTransition>
{
	/**
	 * Initialize this factory with the specified server.
	 * @param server the server for which to initialize.
	 */
	public ClientServerFactory(ClientNioServer server)
	{
		super(server);
	}

	/**
	 * Create the map of all possible states.
	 * @return a mapping of the states enumeration to the corresponding NioState instances.
	 * @see org.jppf.server.nio.NioServerFactory#createStateMap()
	 */
	@Override
    public Map<ClientState, NioState<ClientTransition>> createStateMap()
	{
		Map<ClientState, NioState<ClientTransition>> map = new EnumMap<ClientState, NioState<ClientTransition>>(ClientState.class);
		map.put(SENDING_RESULTS, new SendingResultsState((ClientNioServer) server));
		map.put(WAITING_JOB, new WaitingJobState((ClientNioServer) server));
		map.put(IDLE, new IdleState((ClientNioServer) server));
		return map;
	}

	/**
	 * Create the map of all possible transitions.
	 * @return a mapping of the transitions enumeration to the corresponding NioTransition instances.
	 * @see org.jppf.server.nio.NioServerFactory#createTransitionMap()
	 */
	@Override
    public Map<ClientTransition, NioTransition<ClientState>> createTransitionMap()
	{
		Map<ClientTransition, NioTransition<ClientState>> map =
			new EnumMap<ClientTransition, NioTransition<ClientState>>(ClientTransition.class);
		map.put(TO_SENDING_RESULTS, transition(SENDING_RESULTS, RW));
		map.put(TO_WAITING_JOB, transition(WAITING_JOB, R));
		map.put(TO_IDLE, transition(IDLE, R));
		return map;
	}


	/**
	 * Create a transition to the specified state for the specified IO operations.
	 * @param state resulting state of the transition.
	 * @param ops the operations allowed.
	 * @return an <code>NioTransition&lt;ClassState&gt;</code> instance.
	 */
	private static NioTransition<ClientState> transition(ClientState state, int ops)
	{
		return new NioTransition<ClientState>(state, ops);
	}
}
