/*
 * Java Parallel Processing Framework.
 *  Copyright (C) 2005-2009 JPPF Team. 
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

import java.security.*;
import java.util.*;

import org.apache.commons.logging.*;
import org.jppf.comm.socket.SocketWrapper;
import org.jppf.node.*;
import org.jppf.utils.*;

/**
 * Instances of this class represent dynamic class loading, and serialization/deserialization, capabilities, associated
 * with a specific client application.<br>
 * The application is identified through a unique uuid. This class effectively acts as a container for the classes of
 * a client application, a provides the methods to enable the transport, serialization and deserialization of these classes.
 * @author Laurent Cohen
 */
public class JPPFContainer
{
	/**
	 * Logger for this class.
	 */
	private static Log log = LogFactory.getLog(JPPFContainer.class);
	/**
	 * Utility for deserialization and serialization.
	 */
	private SerializationHelper helper = null;
	/**
	 * Class loader used for dynamic loading and updating of client classes.
	 */
	private JPPFClassLoader classLoader = null;
	/**
	 * The unique identifier for the submitting application.
	 */
	private List<String> uuidPath = new ArrayList<String>();

	/**
	 * Initialize this container with a specified application uuid.
	 * @param uuidPath the unique identifier of a submitting application.
	 * @throws Exception if an error occurs while initializing.
	 */
	public JPPFContainer(List<String> uuidPath) throws Exception
	{
		this.uuidPath = uuidPath;
		init();
	}

	/**
	 * Initialize this node's resources.
	 * @throws Exception if an error is raised during initialization.
	 */
	public final void init() throws Exception
	{
		initHelper();
	}
	
	/**
	 * Deserialize a number of objects from a socket client.
	 * @param wrapper the socket client from which to read the objects to deserialize.
	 * @param list a list holding the resulting deserialized objects.
	 * @param count the number of objects to deserialize.
	 * @return the new position in the source data after deserialization.
	 * @throws Exception if an error occurs while deserializing.
	 */
	public int deserializeObject(SocketWrapper wrapper, List<Object> list, int count) throws Exception
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(classLoader);
			for (int i=0; i<count; i++)
			{
				JPPFBuffer buf = wrapper.receiveBytes(0);
				byte[] data = buf.getBuffer();
				list.add(helper.getSerializer().deserialize(data));
			}
			return 0;
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	/**
	 * Deserialize an object from a socket client.
	 * @param wrapper the socket client from which to read the objects to deserialize.
	 * @return the new position in the source data after deserialization.
	 * @throws Exception if an error occurs while deserializing.
	 */
	public Object deserializeObject(SocketWrapper wrapper) throws Exception
	{
		return deserializeObject(wrapper.receiveBytes(0).getBuffer());
	}

	/**
	 * Deserialize an object from a socket client.
	 * @param data the array of bytes to deserialize into an object.
	 * @return the new position in the source data after deserialization.
	 * @throws Exception if an error occurs while deserializing.
	 */
	public Object deserializeObject(byte[] data) throws Exception
	{
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(classLoader);
			return helper.getSerializer().deserialize(data);
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(cl);
		}
	}

	/**
	 * Get the main classloader for the node. This method performs a lazy initialization of the classloader.
	 * @return a <code>ClassLoader</code> used for loading the classes of the framework.
	 */
	public JPPFClassLoader getClassLoader()
	{
		if (classLoader == null)
		{
			log.debug("Creating new class loader with uuidPath="+uuidPath);
			AccessController.doPrivileged(new PrivilegedAction<Object>()
			{
				public Object run()
				{
					classLoader = new JPPFClassLoader(NodeRunner.getJPPFClassLoader(), uuidPath);
					return null;
				}
			});
		}
		return classLoader;
	}
	
	/**
	 * Get the main classloader for the node. This method performs a lazy initialization of the classloader.
	 * @throws Exception if an error occcurs while instantiating the class loader.
	 */
	private void initHelper() throws Exception
	{
		Class c = getClassLoader().loadJPPFClass("org.jppf.utils.SerializationHelperImpl");
		Object o = c.newInstance();
		helper = (SerializationHelper) o;
	}
	
	/**
	 * Get the unique identifier for the submitting application.
	 * @return the application uuid as a string.
	 */
	public String getAppUuid()
	{
		return uuidPath.isEmpty() ? null : uuidPath.get(0);
	}

	/**
	 * Set the unique identifier for the submitting application.
	 * @param uuidPath the application uuid as a string.
	 */
	public void setUuidPath(List<String> uuidPath)
	{
		this.uuidPath = uuidPath;
	}
}
