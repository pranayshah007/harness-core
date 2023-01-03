/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import java.util.List;
import javax.annotation.Nullable;

public class Utils {
  private Utils() {}

  @Nullable
  public static <T1, T2 extends T1> T2 getFirstInstance(List<T1> inputs, Class<T2> cls) {
    return (T2) inputs.stream().filter(cls::isInstance).findFirst().orElse(null);
  }

  public static boolean isInstanceOf(Class<?> type, Object obj) {
    return type.isInstance(obj);
  }

  public static boolean isNotInstanceOf(Class<?> type, Object obj) {
    return !isInstanceOf(type, obj);
  }
}
