/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
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

@OwnedBy(PL)
public class RedissonKryoCodec extends KryoCodec {
  private final Set<Class<? extends KryoRegistrar>> kryoRegistrars;
  public RedissonKryoCodec() {
    kryoRegistrars = HarnessReflections.get().getSubTypesOf(KryoRegistrar.class);
  }
  @Override
  protected Kryo createInstance(List<Class<?>> classes, ClassLoader classLoader) {
    return kryo();
  }

  private Kryo kryo() {
    final ClassResolver classResolver = new ClassResolver();
    HKryo kryo = new HKryo(classResolver);
    kryo.setDefaultSerializer(getDefaultSerializer());

    try {
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

  protected Class<? extends Serializer> getDefaultSerializer() {
    return FieldSerializer.class;
  }
}
