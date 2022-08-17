package io.harness.serializer;

import static java.lang.String.format;

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.Registration;
import com.esotericsoftware.kryo.util.IntMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.OutputStream;
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

  public String asString(Object obj) {
    try {
      return referenceFalseKryoSerializer.asString(obj);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.asString(obj);
    }
  }

  public byte[] asBytes(Object obj) {
    try {
      return referenceFalseKryoSerializer.asBytes(obj);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.asBytes(obj);
    }
  }

  public byte[] asDeflatedBytes(Object obj) {
    try {
      return referenceFalseKryoSerializer.asDeflatedBytes(obj);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.asDeflatedBytes(obj);
    }
  }

  private void writeToStream(Object obj, OutputStream outputStream) {
    try {
      referenceFalseKryoSerializer.writeToStream(obj, outputStream);
    } catch (KryoException kryoException) {
      referenceTrueKryoSerializer.writeToStream(obj, outputStream);
    }
  }

  public <T> T clone(T obj) {
    try {
      return referenceFalseKryoSerializer.clone(obj);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.clone(obj);
    }
  }

  public Object asObject(byte[] bytes) {
    try {
      return referenceFalseKryoSerializer.asObject(bytes);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.asObject(bytes);
    }
  }

  public Object asInflatedObject(byte[] bytes) {
    try {
      return referenceFalseKryoSerializer.asInflatedObject(bytes);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.asInflatedObject(bytes);
    }
  }

  public Object asObject(String base64) {
    try {
      return referenceFalseKryoSerializer.asObject(base64);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.asObject(base64);
    }
  }

  public boolean isRegistered(Class cls) {
    try {
      return referenceFalseKryoSerializer.isRegistered(cls);
    } catch (KryoException kryoException) {
      return referenceTrueKryoSerializer.isRegistered(cls);
    }
  }
}
