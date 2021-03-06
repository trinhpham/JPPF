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

import java.io.*;
import java.lang.reflect.*;
import java.net.SocketPermission;
import java.security.*;
import java.util.*;

import javax.management.*;

import org.jppf.management.ObjectNameCache;
import org.jppf.utils.*;
import org.jppf.utils.configuration.JPPFProperties;
import org.jppf.utils.streams.StreamUtils;
import org.slf4j.*;

/**
 * This class is used to generate and obtain the permissions that constitute the security policy for a JPPF node.
 * @author Laurent Cohen
 */
public final class PermissionsFactory {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(PermissionsFactory.class);
  /**
   * Determines whether the debug level is enabled in the log configuration, without the cost of a method call.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);
  /**
   * Encapsulates the set of all permissions granted to a node.
   */
  private static List<Permission> permList = null;
  /**
   * Permissions granted to the executed task code.
   */
  private static JPPFPermissions normalPermissions = null;
  /**
   * Permissions granted to the JPPF node code.
   */
  private static JPPFPermissions extendedPermissions = null;

  /**
   * Instantiation of this class is not permitted.
   */
  private PermissionsFactory() {
  }

  /**
   * Reset the current permissions to enable their reload.
   * @see java.security.Permissions
   */
  public static synchronized void resetPermissions() {
    permList = null;
  }

  /**
   * Get the set of permissions granted to a node.
   * @param classLoader the ClassLoader used to retrieve the policy file.
   * @return a Permissions object.
   * @see java.security.Permissions
   */
  public static synchronized PermissionCollection getPermissions(final ClassLoader classLoader) {
    if (permList == null) {
      final ClassLoader cl = (classLoader == null) ? PermissionsFactory.class.getClassLoader() : classLoader;
      createPermissions(cl);
      if (debugEnabled) log.debug("created normal permissions");
    }
    if (debugEnabled) log.debug("getting normal permissions");
    return normalPermissions;
  }

  /**
   * Get the set of permissions granted to a node.
   * @param classLoader the ClassLoader used to retrieve the policy file.
   * @return a Permissions object.
   * @see java.security.Permissions
   */
  public static synchronized PermissionCollection getExtendedPermissions(final ClassLoader classLoader) {
    if (permList == null) {
      final ClassLoader cl = (classLoader == null) ? PermissionsFactory.class.getClassLoader() : classLoader;
      createPermissions(cl);
      if (debugEnabled) log.debug("created extended permissions");
    }
    if (debugEnabled) log.debug("getting extended permissions");
    return extendedPermissions;
  }

  /**
   * Initialize the permissions granted to a node.
   * @param classLoader the ClassLoader used to retrieve the policy file.
   */
  private static synchronized void createPermissions(final ClassLoader classLoader) {
    if (permList != null) return;
    permList = new ArrayList<>();
    createDynamicPermissions();
    createManagementPermissions();
    readStaticPermissions(classLoader);
    normalPermissions = new JPPFPermissions();
    extendedPermissions = new JPPFPermissions();
    for (Permission p : permList) {
      normalPermissions.add(p);
      extendedPermissions.add(p);
    }
    extendedPermissions.add(new RuntimePermission("exitVM"));
    extendedPermissions.add(new RuntimePermission("setSecurityManager"));
  }

  /**
   * Initialize the permissions that depend on the JPPF configuration.
   */
  private static void createDynamicPermissions() {
    try {
      final TypedProperties config = JPPFConfiguration.getProperties();
      String host = config.get(JPPFProperties.SERVER_HOST);
      final boolean sslEnabled = config.get(JPPFProperties.SSL_ENABLED);
      // for backward compatibility with v2.x configurations
      //int port = config.getInt("jppf.server.port", sslEnabled ? 11111 : 11143);
      final int port = config.get(sslEnabled ? JPPFProperties.SERVER_SSL_PORT : JPPFProperties.SERVER_PORT);
      addPermission(new SocketPermission(host + ':' + port, "connect,listen"), "dynamic");
      host = config.get(JPPFProperties.DISCOVERY_GROUP);
      //port = props.getInt("jppf.discovery.port", 11111);
      addPermission(new SocketPermission(host + ":0-", "accept,connect,listen,resolve"), "dynamic");
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  /**
   * Initialize the permissions for the JMX-based management of the node.
   */
  private static void createManagementPermissions() {
    try {
      final TypedProperties props = JPPFConfiguration.getProperties();
      //String host = props.getString("jppf.management.host", "localhost");
      final int port = props.get(JPPFProperties.MANAGEMENT_PORT);
      // TODO: find a way to be more restrictive on RMI permissions
      //addPermission(new SocketPermission(host + ":1024-", "accept,connect,listen,resolve"), "management");
      addPermission(new SocketPermission("localhost:" + port, "accept,connect,listen,resolve"), "management");
      //p.add(new MBeanServerPermission("createMBeanServer"));
      addPermission(new MBeanServerPermission("*"), "management");
      addPermission(new MBeanPermission("*", "*", ObjectNameCache.getObjectName("*:*"), "*"), "management");
      // TODO: find a way to be more restrictive on RMI permissions
      addPermission(new SocketPermission("*:1024-", "accept,connect,listen,resolve"), "management");
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  /**
   * Read the static permissions stored in a policy file, if any is defined.
   * @param classLoader the ClassLoader used to retrieve the policy file.
   */
  private static void readStaticPermissions(final ClassLoader classLoader) {
    InputStream is = null;
    LineNumberReader reader = null;
    try {
      final String file = JPPFConfiguration.get(JPPFProperties.POLICY_FILE);
      if (file == null) return;
      try {
        is = new FileInputStream(file);
      } catch (final FileNotFoundException e) {
        if (debugEnabled) log.debug("jppf policy file '{}' not found locally: {}", file, e);
      }
      if (is == null) is = classLoader.getResourceAsStream(file);
      if (is == null) {
        if (debugEnabled) log.debug("jppf policy file '" + file + "' not found on the driver side");
        return;
      }
      reader = new LineNumberReader(new InputStreamReader(is));
      int count = 0;
      final boolean end = false;
      while (!end) {
        String line = reader.readLine();
        if (line == null) break;
        count++;
        line = line.trim();
        if ("".equals(line) || line.startsWith("//")) continue;
        if (!line.startsWith("permission")) {
          err(file, count, " should start with \"permission\"");
          continue;
        }
        line = line.substring("permission".length());
        if (!line.endsWith(";")) {
          err(file, count, " should end with \";\"");
          continue;
        }
        line = line.substring(0, line.length() - 1);
        line = line.trim();
        final Permission p = parsePermission(line, file, count);
        if (p != null) addPermission(p, "static");
      }
    } catch (final Exception e) {
      log.error(e.getMessage(), e);
    } finally {
      StreamUtils.closeSilent(reader);
    }
  }

  /**
   * Convenience method used to log information when permission are added to the list of permissions
   * @param p the permission to add.
   * @param type the type of permission, static, dynamic or mbean.
   * @throws Exception if an error is raised when adding the permission.
   */
  private static void addPermission(final Permission p, final String type) throws Exception {
    if (debugEnabled) log.debug("adding " + type + " permission: " + p);
    permList.add(p);
  }

  /**
   * Parse a permission entry in the policy file.
   * @param source the string containing the permission entry.
   * @param file the name of the policy file the permission belongs to.
   * @param line the line number at which the permission entry is.
   * @return a <code>Permission</code> object built from the permission entry.
   */
  private static Permission parsePermission(final String source, final String file, final int line) {
    String className = null;
    String name = null;
    String actions = null;

    int idx = source.indexOf('"');
    if (idx < 0) {
      err(file, line, "permission entry has no name/action, or missing opening quote");
      return null;
    }
    className = source.substring(0, idx).trim();

    idx++;
    int idx2 = source.indexOf('"', idx);
    if (idx2 < 0) {
      err(file, line, "missing closing quote on permission name");
      return null;
    }
    name = source.substring(idx, idx2);

    idx = source.indexOf('"', idx2 + 1);
    if (idx >= 0) {
      idx++;
      idx2 = source.indexOf('"', idx);
      if (idx2 < 0) {
        err(file, line, "missing closing quote on permission action");
        return null;
      }
      actions = source.substring(idx, idx2);
    }
    name = expandProperties(name, file, line);
    if (name == null) return null;
    if (actions != null) {
      actions = expandProperties(actions, file, line);
      if (actions == null) return null;
    }
    return instantiatePermission(className, name, actions, file, line);
  }

  /**
   * Instantiate a Permission object given its class name, permission name and actions list.
   * @param className the name of the permission class.
   * @param name the name of the permission to grant.
   * @param actions a comma separated list of actions, may be null.
   * @param file the name of the policy file the permission belongs to.
   * @param line the line number at which the permission entry is.
   * @return a <code>Permission</code> object built from the permission components.
   */
  private static Permission instantiatePermission(final String className, final String name, final String actions, final String file, final int line) {
    Permission permission = null;
    Class<?> c = null;
    try {
      c = Class.forName(className);
    } catch (@SuppressWarnings("unused") final ClassNotFoundException e) {
      return new UnresolvedPermission(className, name, actions, null);
    }

    Constructor<?> constructor = null;
    Object[] params = null;
    Exception ex = null;
    String msg = null;
    try {
      if (actions != null) {
        constructor = c.getConstructor(String.class, String.class);
        params = new Object[] { name, actions };
      } else {
        constructor = c.getConstructor(String.class);
        params = new Object[] { name };
      }
      permission = (Permission) constructor.newInstance(params);
    } catch (final InstantiationException e) {
      ex = e;
      msg = "could not instantiate";
    } catch (final IllegalAccessException e) {
      ex = e;
      msg = "could not instantiate";
    } catch (final InvocationTargetException e) {
      ex = e;
      msg = "could not instantiate";
    } catch (final NoSuchMethodException e) {
      ex = e;
      msg = "could not find a proper constructor for";
    }
    if (ex != null) {
      msg = msg + " permission with class=\"" + className + "\", name=\"" + name + "\", actions=\"" + actions + "\" [" + ex.getMessage() + "]";
      err(file, line, msg);
      return null;
    }
    return permission;
  }

  /**
   * Expand the properties in a permission name of action list.
   * @param source string containing the token.
   * @param file the name of the policy file the permission belongs to.
   * @param line the line number at which the permission entry is.
   * @return the token with its properties expanded.
   */
  private static String expandProperties(final String source, final String file, final int line) {
    final StringBuilder sb = new StringBuilder();
    final int length = source.length();
    int pos = 0;
    while (pos < length) {
      int idx = source.indexOf("${", pos);
      if (idx < 0) {
        if (pos <= length - 1) sb.append(source.substring(pos));
        break;
      }
      if (idx > pos) sb.append(source.substring(pos, idx));
      pos = idx + 2;
      idx = source.indexOf('}', pos);
      if (idx < 0) {
        err(file, line, "missing closing \"}\" on property expansion");
        return null;
      }
      String s = source.substring(pos, idx);
      if (s == null) return source;
      s = s.trim();
      if ("".equals(s)) return source;
      final String value = System.getProperty(s);
      if (value == null) return source;
      sb.append(value);
      pos = idx + 1;
    }
    return sb.toString();
  }

  /**
   * Print an error message.
   * @param file the policy file in which the error was detected.
   * @param line the line number at which the error was detected.
   * @param msg the error description.
   */
  private static void err(final String file, final int line, final String msg) {
    System.err.println("Policy file '" + file + "', line " + line + " : " + msg);
  }
}
