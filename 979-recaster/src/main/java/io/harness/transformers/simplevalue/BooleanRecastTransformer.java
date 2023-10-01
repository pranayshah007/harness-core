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

public class BooleanRecastTransformer extends RecastTransformer implements SimpleValueTransformer {
  public BooleanRecastTransformer() {
    super(ImmutableList.of(boolean.class, Boolean.class, boolean[].class, Boolean[].class));
  }

  @Override
  public Object encode(Object value, CastedField castedField) {
    return value;
  }

  @Override
  public Object decode(final Class targetClass, final Object val, final CastedField castedField) {
    if (val == null) {
      return null;
    }

    if (val instanceof Boolean) {
      return val;
    }

    // handle the case for things like the ok field
    if (val instanceof Number) {
      return ((Number) val).intValue() != 0;
    }

    if (val instanceof List) {
      final Class<?> type = targetClass.isArray() ? targetClass.getComponentType() : targetClass;
      return RecastReflectionUtils.convertToArray(type, (List<?>) val);
    }

    // Handling first class recast encoded value
    if (val instanceof Map) {
      Object decodedObject = ((Map<String, Object>) val).get(Recaster.ENCODED_VALUE);
      return decode(targetClass, decodedObject, castedField);
    }

    return Boolean.parseBoolean(val.toString());
  }
}
