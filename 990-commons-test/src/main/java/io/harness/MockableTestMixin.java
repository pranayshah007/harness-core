/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import java.lang.reflect.Field;
import org.apache.commons.lang3.reflect.FieldUtils;

public interface MockableTestMixin {
  /**
   * Mock the given field in this class.
   * @deprecated
   * This is evil and should not be used since it causes test flakiness.
   * Because of the nature of static fields when you set it in one test, that leaks the set value to all other tests
   * which means that you don't have reproducible tests and the value of the staic field being set depends on the order
   * of test execution.
   */
  @Deprecated
  default void setStaticFieldValue(final Class<?> clz, final String fieldName, final Object value)
      throws IllegalAccessException {
    final Field f = FieldUtils.getField(clz, fieldName, true);
    FieldUtils.removeFinalModifier(f);
    f.set(null, value);
  }
}
