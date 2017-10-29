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

package org.jppf.jmxremote.message;

import java.util.Arrays;

/**
 * A specialized message that represents a repsponse to a previous request.
 * @author Laurent Cohen
 */
public class JMXResponse extends AbstractJMXMessage {
  /**
   * Explicit serialVersionUID.
   */
  private static final long serialVersionUID = 1L;
  /**
   * The result of the request.
   */
  private final Object result;
  /**
   * An exception eventually raised when performing the request.
   */
  private final Exception exception;

  /**
   * Initialize this request with the specified ID, request type and parameters.
   * @param messageID the message id.
   * @param requestType the type of request.
   * @param result the request's result.
   */
  public JMXResponse(final long messageID, final JMXMessageType requestType, final Object result) {
    this(messageID, requestType, result, null);
  }

  /**
   * Initialize this request with the specified ID, request type and parameters.
   * @param messageID the message id.
   * @param requestType the type of request.
   * @param exception an exception eventually raised when performing the request.
   */
  public JMXResponse(final long messageID, final JMXMessageType requestType, final Exception exception) {
    this(messageID, requestType, null, exception);
  }

  /**
   * Initialize this request with the specified ID, request type and parameters.
   * @param messageID the message id.
   * @param requestType the type of request.
   * @param result the request's result.
   * @param exception an exception eventually raised when performing the request.
   */
  public JMXResponse(final long messageID, final JMXMessageType requestType, final Object result, final Exception exception) {
    super(messageID, requestType);
    this.result = result;
    this.exception = exception;
  }

  /**
   * @return the request's parameters.
   */
  public Object getResult() {
    return result;
  }

  /**
   * @return an exception eventually raised when performing the request.
   */
  public Exception getException() {
    return exception;
  }

  @Override
  public String toString() {
    return new StringBuilder(getClass().getSimpleName()).append('[')
      .append("messageID=").append(messageID)
      .append(", messageType=").append(messageType)
      .append(", result=").append(Arrays.asList(result))
      .append(", exception=").append(exception)
      .append(']').toString();
  }
}
