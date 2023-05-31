/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

public class MathUtils {
  public static double getPercentageChange(long current, long previous) {
    if (previous == 0 && current > 0) {
      return 100;
    } else if (previous == 0 && current == 0) {
      return 0;
    }
    return ((double) (current - previous) * 100) / previous;
  }
}
