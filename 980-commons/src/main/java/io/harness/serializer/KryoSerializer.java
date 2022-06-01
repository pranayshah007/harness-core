/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static java.lang.String.format;

import io.harness.reflection.CodeUtils;

import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.esotericsoftware.kryo.util.IntMap;
import com.google.api.client.util.Base64;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Set;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class KryoSerializer {
  public static void check(IntMap<Registration> previousState, IntMap<Registration> newState) {
    for (IntMap.Entry entry : newState.entries()) {
      final Registration newRegistration = (Registration) entry.value;
      final Registration previousRegistration = previousState.get(newRegistration.getId());

      if (previousRegistration == null) {
        continue;
      }

      if (previousRegistration.getType() == newRegistration.getType()) {
        continue;
      }

      throw new IllegalStateException(format("The id %d changed its class from %s to %s", newRegistration.getId(),
          previousRegistration.getType().getCanonicalName(), newRegistration.getType().getCanonicalName()));
    }
  }

  private final KryoPool pool;
  private final boolean skipHarnessClassOriginRegistrarCheck;

  @Inject
  public KryoSerializer(Set<Class<? extends KryoRegistrar>> registrars) {
    this(registrars, false);
  }

  /**
   * Creates a new kryo serializer.
   * @param registrars the set of registrars
   * @param skipHarnessClassOriginRegistrarCheck if true, classes can be registered by registrars from other sources -
   *     only meant for UTs.
   */
  @VisibleForTesting
  public KryoSerializer(Set<Class<? extends KryoRegistrar>> registrars, boolean skipHarnessClassOriginRegistrarCheck) {
    this.pool = new KryoPool.Builder(() -> kryo(registrars)).softReferences().build();
    this.skipHarnessClassOriginRegistrarCheck = skipHarnessClassOriginRegistrarCheck;
  }

  private HKryo kryo(Collection<Class<? extends KryoRegistrar>> registrars) {
    final ClassResolver classResolver = new ClassResolver();
    HKryo kryo = new HKryo(classResolver, this.skipHarnessClassOriginRegistrarCheck);
    try {
      for (Class<? extends KryoRegistrar> kryoRegistrarClass : registrars) {
        final IntMap<Registration> previousState = new IntMap<>(classResolver.getRegistrations());
        kryo.setCurrentLocation(CodeUtils.location(kryoRegistrarClass));

        Constructor<?> constructor = kryoRegistrarClass.getConstructor();
        final KryoRegistrar kryoRegistrar = (KryoRegistrar) constructor.newInstance();
        kryoRegistrar.register(kryo);
        check(previousState, classResolver.getRegistrations());
      }

    } catch (
        NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException exception) {
      log.error("Unexpected exception", exception);
    }

    return kryo;
  }

  public String asString(Object obj) {
    return Base64.encodeBase64String(asBytes(obj));
  }

  public byte[] asBytes(Object obj) {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    writeToStream(obj, outputStream);
    return outputStream.toByteArray();
  }

  public byte[] asDeflatedBytes(Object obj) {
    try {
      final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      final DeflaterOutputStream outputStream = new DeflaterOutputStream(byteStream);
      writeToStream(obj, outputStream);
      outputStream.finish();
      return byteStream.toByteArray();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private void writeToStream(Object obj, OutputStream outputStream) {
    try {
      Output output = new Output(outputStream);
      pool.run(kryo -> {
        kryo.writeClassAndObject(output, obj);
        return null;
      });
      output.flush();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  public <T> T clone(T object) {
    return pool.run(kryo -> kryo.copy(object));
  }

  public Object asObject(byte[] bytes) {
    Input input = new Input(bytes);
    Object obj = pool.run(kryo -> kryo.readClassAndObject(input));
    input.close();
    return obj;
  }

  public Object asInflatedObject(byte[] bytes) {
    Input input = new Input(new InflaterInputStream(new Input(bytes)));
    Object obj = pool.run(kryo -> kryo.readClassAndObject(input));
    input.close();
    return obj;
  }

  public Object asObject(String base64) {
    return asObject(Base64.decodeBase64(base64));
  }

  public boolean isRegistered(Class cls) {
    return pool.run(kryo -> kryo.getClassResolver().getRegistration(cls) != null);
  }
}
