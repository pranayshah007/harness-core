/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import static java.lang.String.format;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.util.IntMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class KryoSerializerWrapper {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Inject @Named("referenceTrueKryoSerializer") private KryoSerializer referenceTrueKryoSerializer;

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

  public byte[] asBytes(Object obj) {
    try {
      return referenceTrueKryoSerializer.asBytes(obj);
    } catch (KryoException kryoException) {
      log.info("Using kryo serializer on 'asBytes' method with setReference of false");
      return referenceFalseKryoSerializer.asBytes(obj);
    }
  }

  public Object asObject(byte[] bytes) {
    try {
      return referenceTrueKryoSerializer.asObject(bytes);
    } catch (KryoException kryoException) {
      log.info("Using kryo serializer on 'asObject' method with setReference of false");
      return referenceFalseKryoSerializer.asObject(bytes);
    }
  }
}
