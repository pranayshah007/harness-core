/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static org.apache.commons.lang3.StringUtils.isBlank;

import dev.morphia.query.UpdateOperations;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class MongoUtils {
  public static <T> UpdateOperations<T> setUnset(UpdateOperations<T> ops, String field, Object value) {
    if (value == null || (value instanceof String && isBlank((String) value))) {
      return ops.unset(field);
    } else {
      return ops.set(field, value);
    }
  }

  public static <T> UpdateOperations<T> setUnsetOnInsert(UpdateOperations<T> ops, String field, Object value) {
    if (value == null || (value instanceof String && isBlank((String) value))) {
      return ops.unset(field);
    } else {
      return ops.setOnInsert(field, value);
    }
  }
}
