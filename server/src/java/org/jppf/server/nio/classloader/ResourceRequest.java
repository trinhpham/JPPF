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

package org.jppf.server.nio.classloader;

import org.jppf.classloader.JPPFResourceWrapper;
import org.jppf.server.nio.*;

/**
 * Instances of this class represent a class loading request from a node to a client channel.
 */
public class ResourceRequest
{
  /**
   * The node class loader channel.
   */
  private final ChannelWrapper<?> channel;
  /**
   * The resource to lookup in the client.
   */
  private JPPFResourceWrapper resource;

  /**
   * Initialize this request with the specified node channel and resource.
   * @param channel the node class loader channel.
   * @param resource the resource to lookup in the client.
   */
  public ResourceRequest(final ChannelWrapper<?> channel, final JPPFResourceWrapper resource)
  {
    this.channel = channel;
    this.resource = resource;
  }

  /**
   * Get the node class loader channel.
   * @return a {@link ChannelWrapper} instance.
   */
  public ChannelWrapper<?> getChannel()
  {
    return channel;
  }

  /**
   * Get the resource to lookup in the client.
   * @return a {@link JPPFResourceWrapper} instance.
   */
  public JPPFResourceWrapper getResource()
  {
    return resource;
  }

  /**
   * Set the resource to lookup in the client.
   * @param resource a {@link JPPFResourceWrapper} instance.
   */
  public void setResource(final JPPFResourceWrapper resource)
  {
    Object o = null;
    for (String id: new String[] { "definition", "resource_list", "resource_map" }) {
      if ((o = resource.getData(id)) != null) {
        this.resource.setData(id, o);
      }
    }
    Long callableId = (Long) resource.getData("callable.id");
    if (callableId == null) callableId = -1L;
    Long thisCallableId = (Long) this.resource.getData("callable.id");
    if (thisCallableId == null) thisCallableId = -1L;
    if (callableId.equals(thisCallableId) && (callableId >= 0) && ((o = resource.getCallable()) != null)) this.resource.setCallable((byte[]) o);
    this.resource.setState(resource.getState());
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('[');
    sb.append("channel=").append(channel.getClass().getSimpleName()).append("[id=").append(channel.getId()).append(']');
    sb.append(", resource=").append(resource);
    sb.append(']');
    return sb.toString();
  }
}
