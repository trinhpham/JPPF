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
package org.jppf.server.node;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

import org.jppf.classloader.AbstractJPPFClassLoader;
import org.jppf.io.*;
import org.jppf.serialization.*;
import org.jppf.utils.*;
import org.jppf.utils.configuration.JPPFProperties;
import org.jppf.utils.hooks.HookFactory;
import org.slf4j.*;

/**
 * Instances of this class represent dynamic class loading, and serialization/deserialization, capabilities, associated
 * with a specific client application.<br>
 * The application is identified through a unique uuid. This class effectively acts as a container for the classes of
 * a client application, a provides the methods to enable the transport, serialization and deserialization of these classes.
 * @author Laurent Cohen
 * @exclude
 */
public abstract class JPPFContainer {
  /**
   * Logger for this class.
   */
  private static Logger log = LoggerFactory.getLogger(JPPFContainer.class);
  /**
   * Determines whether the debug level is enabled in the logging configuration, without the cost of a method call.
   */
  private static boolean debugEnabled = LoggingUtils.isDebugEnabled(log);
  /**
   * Determines whether the trace level is enabled in the logging configuration, without the cost of a method call.
   */
  private static boolean traceEnabled = log.isTraceEnabled();
  /**
   * Utility for deserialization and serialization.
   */
  protected SerializationHelper helper = null;
  /**
   * Utility for deserialization and serialization.
   */
  protected ObjectSerializer serializer = null;
  /**
   * Class loader used for dynamic loading and updating of client classes.
   */
  protected AbstractJPPFClassLoader classLoader = null;
  /**
   * The unique identifier for the submitting application.
   */
  protected List<String> uuidPath = new ArrayList<>();
  /**
   * Used to prevent parallel deserialization.
   */
  private Lock lock = new ReentrantLock();
  /**
   * Determines whether tasks deserialization should be sequential rather than parallel.
   */
  private final boolean sequentialDeserialization = JPPFConfiguration.get(JPPFProperties.SEQUENTIAL_SERIALiZATION);
  /**
   * Whether the node has access to the client that submitted the job.
   */
  private final boolean clientAccess;

  /**
   * Initialize this container with a specified application uuid.
   * @param uuidPath the unique identifier of a submitting application.
   * @param classLoader the class loader for this container.
   * @param clientAccess whether the node has access to the client that submitted the job.
   * @throws Exception if an error occurs while initializing.
   */
  public JPPFContainer(final List<String> uuidPath, final AbstractJPPFClassLoader classLoader, final boolean clientAccess) throws Exception {
    if (debugEnabled) log.debug(String.format("new JPPFContainer with uuidPath=%s, classLoader=%s, clientAccess=%b", uuidPath, classLoader, clientAccess));
    this.uuidPath = uuidPath;
    this.classLoader = classLoader;
    this.clientAccess = clientAccess;
    init();
  }

  /**
   * Initialize this node's resources.
   * @throws Exception if an error is raised during initialization.
   */
  public final void init() throws Exception {
    initHelper();
  }

  /**
   * Deserialize a number of objects from the I/O channel.
   * @param list a list holding the resulting deserialized objects.
   * @param count the number of objects to deserialize.
   * @param executor the number of objects to deserialize.
   * @return the new position in the source data after deserialization.
   * @throws Throwable if an error occurs while deserializing.
   */
  public abstract int deserializeObjects(Object[] list, int count, ExecutorService executor) throws Throwable;

  /**
   * Get the main class loader for this container.
   * @return a <code>ClassLoader</code> used for loading the classes of the framework.
   */
  public AbstractJPPFClassLoader getClassLoader() {
    return classLoader;
  }

  /**
   * Get the main class loader for this container.
   * @param classLoader a <code>ClassLoader</code> used for loading the classes of the framework.
   */
  public void setClassLoader(final AbstractJPPFClassLoader classLoader) {
    this.classLoader = classLoader;
    try {
      initHelper();
    } catch (final Exception e) {
      log.error("error setting new class loader", e);
    }
  }

  /**
   * Get the main classloader for the node. This method performs a lazy initialization of the classloader.
   * @throws Exception if an error occurs while instantiating the class loader.
   */
  protected void initHelper() throws Exception {
    AbstractJPPFClassLoader cl = getClassLoader();
    if (!clientAccess) cl = (AbstractJPPFClassLoader) cl.getParent();
    final String name = "org.jppf.utils.SerializationHelperImpl";
    if (debugEnabled) log.debug("loading class {} with classloader {}", name, cl);
    final Class<?> c = cl.loadJPPFClass(name);
    helper = (SerializationHelper) c.newInstance();
    serializer = helper.getSerializer();
  }

  /**
   * Get the unique identifier for the submitting application.
   * @return the application uuid as a string.
   */
  public String getAppUuid() {
    return uuidPath.isEmpty() ? null : uuidPath.get(0);
  }

  /**
   * Set the unique identifier for the submitting application.
   * @param uuidPath the application uuid as a string.
   */
  public void setUuidPath(final List<String> uuidPath) {
    this.uuidPath = uuidPath;
  }

  /**
   * Instances of this class are used to deserialize objects from an
   * incoming message in parallel.
   */
  protected class ObjectDeserializationTask implements Callable<ObjectDeserializationTask> {
    /**
     * The data received over the network connection.
     */
    private final DataLocation dl;
    /**
     * Index of the object to deserialize in the incoming IO message; used for debugging purposes.
     */
    private final int index;
    /**
     * The deserialized object.
     */
    private Object object;

    /**
     * Initialize this task with the specified data buffer.
     * @param dl the data read from the network connection, stored in a memory-sensitive location.
     * @param index index of the object to deserialize in the incoming IO message; used for debugging purposes.
     */
    public ObjectDeserializationTask(final DataLocation dl, final int index) {
      this.dl = dl;
      this.index = index;
    }

    /**
     * Execute this task.
     * @return a deserialized object.
     */
    @Override
    public ObjectDeserializationTask call() {
      final ClassLoader cl = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(getClassLoader());
        if (traceEnabled) log.debug("deserializing object index = " + index);
        if (sequentialDeserialization) lock.lock();
        try {
          object = IOHelper.unwrappedData(dl, serializer);
        } finally {
          if (sequentialDeserialization) lock.unlock();
        }
      } catch (final Throwable t) {
        final String desc = (index == 0 ? "data provider" : "task at index " + index) + " could not be deserialized";
        if (debugEnabled) log.debug("{} : {}", desc, ExceptionUtils.getStackTrace(t));
        else log.error("{} : {}", desc, ExceptionUtils.getMessage(t));
        if (index > 0) object = HookFactory.invokeSingleHook(SerializationExceptionHook.class, "buildExceptionResult", desc, t);
      } finally {
        Thread.currentThread().setContextClassLoader(cl);
      }
      return this;
    }

    /**
     * @return the index of the object to deserialize in the incoming IO message; used for debugging purposes.
     */
    public int getIndex() {
      return index;
    }

    /**
     * @return the deserialized object.
     */
    public Object getObject() {
      return object;
    }
  }

  /**
   * Return the utility object for serialization and deserialization.
   * @return an {@link ObjectSerializer} instance.
   */
  public ObjectSerializer getSerializer() {
    return serializer;
  }
}
