/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils;

import java.util.Comparator;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
@UtilityClass
public class CVNGObjectUtils {
  /**
   * Handle nulls in a graceful manner and returns null if both the elements are null.
   */
  @Nullable
  public static <T> T max(T t1, T t2, Comparator<T> comparator) {
    if (t1 == null) {
      return t2;
    } else if (t2 == null) {
      return t1;
    } else {
      int v = comparator.compare(t1, t2);
      if (v > 0) {
        return t1;
      } else {
        return t2;
      }
    }
  }
}
