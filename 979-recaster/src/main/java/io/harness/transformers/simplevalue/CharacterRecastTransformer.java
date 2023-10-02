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
import java.util.Map;

public class CharacterRecastTransformer extends RecastTransformer implements SimpleValueTransformer {
  public CharacterRecastTransformer() {
    super(ImmutableList.of(char.class, Character.class));
  }

  @Override
  public Object decode(Class<?> targetClass, Object object, CastedField castedField) {
    if (object == null) {
      return null;
    }

    if (object instanceof Character) {
      return object;
    }

    // Handling first class recast encoded value
    if (object instanceof Map) {
      Object decodedObject = ((Map<String, Object>) object).get(Recaster.ENCODED_VALUE);
      return decode(targetClass, decodedObject, castedField);
    }

    return String.valueOf(object).toCharArray()[0];
  }

  @Override
  public Object encode(Object object, CastedField castedField) {
    return object == null || object.equals('\0') ? null : String.valueOf(object).toCharArray()[0];
  }
}
