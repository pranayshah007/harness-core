/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.transformers.simplevalue;

import io.harness.beans.CastedField;
import io.harness.core.Recaster;
import io.harness.transformers.RecastTransformer;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Array;
import java.util.Map;

public class ByteRecastTransformer extends RecastTransformer implements SimpleValueTransformer {
  public ByteRecastTransformer() {
    super(ImmutableList.of(byte.class, Byte.class, byte[].class, Byte[].class));
  }

  @Override
  public Object decode(final Class targetClass, final Object val, final CastedField cf) {
    if (val == null) {
      return null;
    }

    if (val.getClass().equals(targetClass)) {
      return val;
    }

    if (val instanceof Number) {
      return ((Number) val).byteValue();
    }

    if (targetClass.isArray() && val.getClass().equals(byte[].class)) {
      return convertToWrapperArray((byte[]) val);
    }

    // Handling first class recast encoded value
    if (val instanceof Map) {
      Object decodedObject = ((Map<String, Object>) val).get(Recaster.ENCODED_VALUE);
      return decode(targetClass, decodedObject, cf);
    }

    return Byte.parseByte(val.toString());
  }

  @Override
  public Object encode(final Object value, final CastedField cf) {
    if (value instanceof Byte[]) {
      return convertToPrimitiveArray((Byte[]) value);
    }
    return value;
  }

  Object convertToPrimitiveArray(final Byte[] values) {
    final int length = values.length;
    final Object array = Array.newInstance(byte.class, length);
    for (int i = 0; i < length; i++) {
      Array.set(array, i, values[i]);
    }
    return array;
  }

  Object convertToWrapperArray(final byte[] values) {
    final int length = values.length;
    final Object array = Array.newInstance(Byte.class, length);
    for (int i = 0; i < length; i++) {
      Array.set(array, i, values[i]);
    }
    return array;
  }
}
