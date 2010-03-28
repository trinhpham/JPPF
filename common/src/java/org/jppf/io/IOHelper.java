/*
 * JPPF.
 * Copyright (C) 2005-2010 JPPF Team.
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

package org.jppf.io;

import java.io.File;

import org.jppf.utils.SystemUtils;


/**
 * Collection of utility methods to create and manipulate IO objects.
 * @author Laurent Cohen
 */
public final class IOHelper
{
	/**
	 * Suze of temporary buffers used in I/O transfers.
	 */
	public static final int TEMP_BUFFER_SIZE = 32 * 1024;

	/**
	 * Instantiation of this class is not permitted.
	 */
	private IOHelper()
	{
	}

	/**
	 * Create a data location object based on a comparison of the available heap memory
	 * and the data location object size.
	 * @param size - the requested size of the data location to create.
	 * @return a <code>DataLocation</code> object whose content may be stored in memory
	 * or on another medium, depending on the available memory.
	 * @throws Exception if an IO error occurs.
	 */
	public static DataLocation createDataLocationMemorySensitive(int size) throws Exception
	{
		return createDataLocationMemorySensitive(size, size);
	}

	/**
	 * Create a data location object based on a comparison of the available heap memory
	 * and the data location object size.
	 * @param size - the requested size of the data location to create.
	 * @param memorySize - the size to consider when calculating the available memory.
	 * @return a <code>DataLocation</code> object whose content may be stored in memory
	 * or on another medium, depending on the available memory.
	 * @throws Exception if an IO error occurs.
	 */
	public static DataLocation createDataLocationMemorySensitive(int size, int memorySize) throws Exception
	{
		long freeMem = SystemUtils.maxFreeHeap();
		if ((long) (1.2d * memorySize) < freeMem) return new ByteBufferLocation(size);
		File file = File.createTempFile("jppf", ".tmp");
		file.deleteOnExit();
		return new FileLocation(file, size);
	}

	/**
	 * Read a provider or task data from an input source.
	 * The data may be stored in memory or on another medium depending on its size and the available memory.
	 * @param source - the input source from which to read the data.
	 * @return A data location containing the data provider or task data.
	 * @throws Exception if an error occurs while deserializing.
	 */
	public static DataLocation readData(InputSource source) throws Exception
	{
		int n = source.readInt();
		DataLocation dl = createDataLocationMemorySensitive(n);
		dl.transferFrom(source, true);
		return dl;
	}

	/**
	 * Read a provider or task data from an input source.
	 * The data may be stored in memory or on another medium depending on its size and the available memory.
	 * @param source - the input source from which to read the data.
	 * @param memorySize - the size to consider when calculating the available memory.
	 * @return A data location containing the data provider or task data.
	 * @throws Exception if an error occurs while deserializing.
	 */
	public static DataLocation readData(InputSource source, int memorySize) throws Exception
	{
		int n = source.readInt();
		DataLocation dl = createDataLocationMemorySensitive(n, memorySize);
		dl.transferFrom(source, true);
		return dl;
	}
}
