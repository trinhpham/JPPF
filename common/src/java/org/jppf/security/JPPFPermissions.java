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

package org.jppf.security;

import java.security.*;
import java.util.*;

/**
 * Implementation of node-specific permissions collection.
 * @author Laurent Cohen
 */
public class JPPFPermissions extends PermissionCollection {
  /**
   * Explicit serialVersionUID.
   */
  private static final long serialVersionUID = 1L;
  /**
   * The list of permissions in this collection.
   */
  private List<Permission> permissions = new Vector<>();

  /**
   * Adds a permission object to the current collection of permission objects.
   * @param permission the Permission object to add.
   */
  @Override
  public synchronized void add(final Permission permission) {
    if (permission == null) return;
    permissions.add(permission);
  }

  /**
   * Returns an enumeration of all the Permission objects in the collection.
   * @return an enumeration of all the Permissions.
   */
  @Override
  public synchronized Enumeration<Permission> elements() {
    return new Enumerator();
  }

  /**
   * Checks to see if the specified permission is implied by the collection of Permission objects held in this PermissionCollection.
   * @param permission the Permission object to compare.
   * @return true if "permission" is implied by the permissions in the collection, false if not.
   */
  @Override
  public synchronized boolean implies(final Permission permission) {
    final List<Permission> perms = Collections.unmodifiableList(permissions);
    for (Permission p : perms) {
      if (p.implies(permission)) return true;
    }
    return false;
  }

  /**
   * Marks this PermissionCollection object as "readonly". After a PermissionCollection object is marked as readonly,
   * no new Permission objects can be added to it using add.
   */
  @Override
  public void setReadOnly() {
    //super.setReadOnly();
  }

  /**
   * Enumerator for the permissions in the collection.
   */
  private class Enumerator implements Enumeration<Permission> {
    /**
     * Index of the current enumerated element.
     */
    private int index = 0;
    /**
     * Total number of elements.
     */
    private int count = 0;
    /**
     * The list of permissions in this collection.
     */
    private List<Permission> enumPermissions = null;

    /**
     * Default constructor.
     */
    public Enumerator() {
      synchronized (JPPFPermissions.this) {
        enumPermissions = new Vector<>();
        enumPermissions.addAll(permissions);
      }
      //enumPermissions = permissions;
      count = enumPermissions.size();
    }

    /**
     * Test if this enumeration contains more elements.
     * @return true if and only if this enumeration object contains at least one more element to provide; false otherwise.
     */
    @Override
    public boolean hasMoreElements() {
      return count > index;
    }

    /**
     * Returns the next element of this enumeration if this enumeration object has at least one more element to provide.
     * @return the next element of this enumeration.
     * @throws NoSuchElementException - if no more elements exist.
     */
    @Override
    public Permission nextElement() throws NoSuchElementException {
      if (!hasMoreElements()) throw new NoSuchElementException("no more element in this enumeration");
      return enumPermissions.get(index++);
    }
  }
}
