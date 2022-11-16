/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.redis;

import static java.lang.String.format;

import io.harness.exception.GeneralException;
import io.harness.reflection.CodeUtils;
import io.harness.reflection.HarnessReflections;
import io.harness.serializer.ClassResolver;
import io.harness.serializer.HKryo;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.KryoSerializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.util.IntMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import org.redisson.codec.KryoCodec;

public class AbstractRedissonKryoCodec extends KryoCodec {
  @Override
  protected Kryo createInstance(List<Class<?>> classes, ClassLoader classLoader) {
    return createInstance();
  }

  private synchronized Kryo createInstance() {
    final ClassResolver classResolver = new ClassResolver();
    HKryo kryo = kryo(classResolver);

    try {
      Set<Class<? extends KryoRegistrar>> kryoRegistrars = HarnessReflections.get().getSubTypesOf(KryoRegistrar.class);
      for (Class<? extends KryoRegistrar> clazz : kryoRegistrars) {
        Constructor<?> constructor = clazz.getConstructor();
        final KryoRegistrar kryoRegistrar = (KryoRegistrar) constructor.newInstance();

        final IntMap<Registration> previousState = new IntMap<>(classResolver.getRegistrations());
        kryo.setCurrentLocation(CodeUtils.location(kryoRegistrar.getClass()));
        kryoRegistrar.register(kryo);

        try {
          KryoSerializer.check(previousState, classResolver.getRegistrations());
        } catch (Exception exception) {
          throw new IllegalStateException(
              format("Check for registration of %s failed", clazz.getCanonicalName()), exception);
        }
      }

    } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new GeneralException("Failed initializing kryo", e);
    }

    return kryo;
  }

  protected synchronized HKryo kryo(ClassResolver classResolver) {
    HKryo kryo = new HKryo(classResolver);
    kryo.setDefaultSerializer(getDefaultSerializer());

    return kryo;
  }

  protected Class<? extends Serializer> getDefaultSerializer() {
    return FieldSerializer.class;
  }
}
