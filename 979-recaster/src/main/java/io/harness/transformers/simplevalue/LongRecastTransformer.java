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
import io.harness.utils.RecastReflectionUtils;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;

public class LongRecastTransformer extends RecastTransformer implements SimpleValueTransformer {
  public LongRecastTransformer() {
    super(ImmutableList.of(long.class, Long.class, long[].class, Long[].class));
  }

  @Override
  public Object decode(Class<?> targetClass, Object object, CastedField castedField) {
    if (object == null) {
      return null;
    }

    if (object instanceof Long) {
      return object;
    }

    if (object instanceof Number) {
      return ((Number) object).longValue();
    }

    if (object instanceof List) {
      final Class<?> type = targetClass.isArray() ? targetClass.getComponentType() : targetClass;
      return RecastReflectionUtils.convertToArray(type, (List<?>) object);
    }

    // Handling first class recast encoded value
    if (object instanceof Map) {
      Object decodedObject = ((Map<String, Object>) object).get(Recaster.ENCODED_VALUE);
      return decode(targetClass, decodedObject, castedField);
    }

    return Long.parseLong(object.toString());
  }

  @Override
  public Object encode(Object object, CastedField castedField) {
    return object;
  }
}
