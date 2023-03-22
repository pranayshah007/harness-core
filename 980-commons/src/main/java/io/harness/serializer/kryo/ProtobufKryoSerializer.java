/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static com.esotericsoftware.kryo.Kryo.NULL;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.protobuf.Message;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@SuppressWarnings("unchecked")
public class ProtobufKryoSerializer<P extends Message> extends Serializer<P> {
  private Method parseFromMethod;

  @Override
  public void write(Kryo kryo, Output output, P protobufMessage) {
    if (protobufMessage == null) {
      output.writeByte(NULL);
      output.flush();
      return;
    }

    byte[] bytes = protobufMessage.toByteArray();

    output.writeInt(bytes.length + 1, true);
    output.writeBytes(bytes);
    output.flush();
  }

  @Override
  public P read(Kryo kryo, Input input, Class<? extends P> aClass) {
    int length = input.readInt(true);
    if (length == NULL) {
      return null;
    }

    byte[] bytes = input.readBytes(length - 1);
    try {
      return (P) (getParseFromMethod(aClass).invoke(aClass, bytes));
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Unable to deserialize protobuf " + e.getMessage(), e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException("Unable to deserialize protobuf " + e.getMessage(), e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Unable to deserialize protobuf " + e.getMessage(), e);
    }
  }

  private Method getParseFromMethod(Class<? extends P> type) throws NoSuchMethodException {
    if (parseFromMethod == null) {
      parseFromMethod = type.getMethod("parseFrom", byte[].class);
      parseFromMethod.setAccessible(true);
    }
    return parseFromMethod;
  }

  @Override
  public boolean getAcceptsNull() {
    return true;
  }

  public P copy(Kryo kryo, P original) {
    if (original == null) {
      return null;
    }
    return (P) original.toBuilder().clone().build();
  }
}
