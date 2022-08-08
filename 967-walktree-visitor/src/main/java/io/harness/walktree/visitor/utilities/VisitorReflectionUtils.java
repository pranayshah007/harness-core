/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.walktree.visitor.utilities;

import java.lang.reflect.Field;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VisitorReflectionUtils {
  public Field addValueToField(Object element, Field field, Object value) throws IllegalAccessException {
    field.setAccessible(true);
    field.set(element, value);
    return field;
  }
}
