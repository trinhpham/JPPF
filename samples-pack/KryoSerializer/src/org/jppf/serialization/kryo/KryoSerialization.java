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

package org.jppf.serialization.kryo;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.util.*;

import javax.management.ObjectName;

import org.jppf.serialization.*;
import org.jppf.utils.pooling.*;
import org.objenesis.instantiator.ObjectInstantiator;
import org.objenesis.strategy.*;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.*;
import com.esotericsoftware.kryo.util.*;

import de.javakaffee.kryoserializers.*;

/**
 * This implementation uses the <a href="http://code.google.com/p/kryo/">Kryo</a> serialization.
 */
public class KryoSerialization implements JPPFSerialization {
  /**
   * Strategy used to instantiate objects during desrialization.
   */
  @SuppressWarnings("unused")
  private static InstantiatorStrategy str = new InstantiatorStrategy() {
    @Override
    public ObjectInstantiator newInstantiatorOf(@SuppressWarnings("rawtypes") final Class c) {
      return new JPPFInstantiator(c);
    }
  };
  /**
   * A fast dynamic pool of {@link Kryo} instances.
   * Using this provides a huge performance improvement vs creating a new Kryo instance each time we serialize or deserialize an object.
   */
  private static ObjectPool<Kryo> pool = new AbstractObjectPoolQueue<Kryo>() {
    @Override
    protected Kryo create() {
      final Kryo kryo = createKryo();
      return kryo;
    }
  };

  @Override
  public void serialize(final Object o, final OutputStream os) throws Exception {
    final Kryo kryo = pool.get();
    try {
      final Output out = new Output(os);
      kryo.writeClassAndObject(out, o);
      out.flush();
    } finally {
      pool.put(kryo);
    }
  }

  @Override
  public Object deserialize(final InputStream is) throws Exception {
    final Kryo kryo = pool.get();
    final ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl != null)  kryo.setClassLoader(cl);
    try {
      final Input in = new Input(is);
      return kryo.readClassAndObject(in);
    } finally {
      pool.put(kryo);
    }
  }

  /**
   * Creates objects without invoking any constructor.
   */
  private static class JPPFInstantiator implements ObjectInstantiator {
    /**
     * The class to instantiate.
     */
    private final Class<?> c;

    /**
     * Initialize this instantiator with the specified class.
     * @param c the class to instantiate.
     */
    public JPPFInstantiator(final Class<?> c) {
      this.c = c;
    }

    @Override
    public Object newInstance() {
      try {
        return SerializationReflectionHelper.create(c);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
 
  /**
   * Forces the clearing of the class name to class map upon invocation of the {@code reset()} method.
   */
  public static class CustomClassResolver extends DefaultClassResolver {
    @Override
    public void reset() {
      super.reset();
      if (nameToClass != null) nameToClass.clear();
    }
  }

  /**
   * Create a Kryo instance, with its instantiator strategy and a set of
   * common serializers (from kryo-serializers project) initialized.
   * @return an instance of {@link Kryo}.
   */
  private static Kryo createKryo() {
    final Kryo kryo = new Kryo(new CustomClassResolver(), new MapReferenceResolver());
    kryo.setAutoReset(true);
    kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    kryo.register(ObjectName.class, new ObjectNameSerializer());
    kryo.register(Arrays.asList( "" ).getClass(), new ArraysAsListSerializer() );
    kryo.register(Collections.EMPTY_LIST.getClass(), new CollectionsEmptyListSerializer() );
    kryo.register(Collections.EMPTY_MAP.getClass(), new CollectionsEmptyMapSerializer() );
    kryo.register(Collections.EMPTY_SET.getClass(), new CollectionsEmptySetSerializer() );
    kryo.register(Collections.singletonList( "" ).getClass(), new CollectionsSingletonListSerializer() );
    kryo.register(Collections.singleton( "" ).getClass(), new CollectionsSingletonSetSerializer() );
    kryo.register(Collections.singletonMap( "", "" ).getClass(), new CollectionsSingletonMapSerializer() );
    kryo.register(GregorianCalendar.class, new GregorianCalendarSerializer());
    kryo.register(InvocationHandler.class, new JdkProxySerializer());
    UnmodifiableCollectionsSerializer.registerSerializers(kryo);
    SynchronizedCollectionsSerializer.registerSerializers(kryo);
    kryo.register(EnumMap.class, new EnumMapSerializer());
    kryo.register(EnumSet.class, new EnumSetSerializer());
    return kryo;
  }
}
