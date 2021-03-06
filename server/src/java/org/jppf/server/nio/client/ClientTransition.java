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

package org.jppf.server.nio.client;

/**
 * Enumeration of the possible state transitions for a client job channel.
 * @author Laurent Cohen
 */
public enum ClientTransition {
  /**
   * Transition from a state to WAITING_HANDSHAKE.
   */
  TO_WAITING_HANDSHAKE,
  /**
   * Transition from a state to SENDING_HANDSHAKE_RESULTS.
   */
  TO_SENDING_HANDSHAKE_RESULTS,
  /**
   * Transition from a state to SENDING_RESULTS.
   */
  TO_SENDING_RESULTS,
  /**
   * Transition from a state to WAITING_JOB.
   */
  TO_WAITING_JOB,
  /**
   * Transition from a state to IDLE.
   */
  TO_IDLE,
  /**
   * Transition from a state to IDLE.
   */
  TO_IDLE_PEER,
  /**
   * Transition to {@link ClientState#SENDING_PEER_HANDSHAKE}.
   */
  TO_SENDING_PEER_HANDSHAKE,
  /**
   * Transition to {@link ClientState#WAITING_PEER_HANDSHAKE_RESULTS}.
   */
  TO_WAITING_PEER_HANDSHAKE_RESULTS
}
