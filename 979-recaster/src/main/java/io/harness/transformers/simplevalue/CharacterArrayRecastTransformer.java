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

public class CharacterArrayRecastTransformer extends RecastTransformer implements SimpleValueTransformer {
  public CharacterArrayRecastTransformer() {
    super(ImmutableList.of(char[].class, Character[].class));
  }

  @Override
  public Object decode(Class<?> targetClass, Object object, CastedField castedField) {
    if (object == null) {
      return null;
    }

    final char[] chars = object.toString().toCharArray();
    if (targetClass.isArray() && targetClass.equals(Character[].class)) {
      return convertToWrapperArray(chars);
    }

    // Handling first class recast encoded value
    if (object instanceof Map) {
      Object decodedObject = ((Map<String, Object>) object).get(Recaster.ENCODED_VALUE);
      return decode(targetClass, decodedObject, castedField);
    }

    return chars;
  }

  @Override
  public Object encode(Object object, CastedField castedField) {
    if (object == null) {
      return null;
    } else {
      if (object instanceof char[]) {
        return new String((char[]) object);
      } else {
        final StringBuilder builder = new StringBuilder();
        final Character[] array = (Character[]) object;
        for (final Character character : array) {
          builder.append(character);
        }
        return builder.toString();
      }
    }
  }

  Object convertToWrapperArray(final char[] values) {
    final int length = values.length;
    final Object array = Array.newInstance(Character.class, length);
    for (int i = 0; i < length; i++) {
      Array.set(array, i, values[i]);
    }
    return array;
  }
}
