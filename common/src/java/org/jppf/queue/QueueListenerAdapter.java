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

package org.jppf.queue;

/**
 * Queue listener adapter, providing no-op implemzntations for the {@code QueueListener} interface methods.
 * Subclasses only need to override the methods they're interested in.
 * @param <T> the type of jobs that are queued.
 * @param <U> the type of bundles the jobs are split into.
 * @param <V> the type of resulting bundles the jobs are split into.
 * @author Laurent Cohen
 * @since 4.1
 */
public class QueueListenerAdapter<T, U, V> implements QueueListener<T, U, V> {
  @Override
  public void bundleAdded(final QueueEvent<T, U, V> event) {
  }

  @Override
  public void bundleRemoved(final QueueEvent<T, U, V> event) {
  }
}
