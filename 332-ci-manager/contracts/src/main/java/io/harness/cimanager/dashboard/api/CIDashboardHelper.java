/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app.beans.entities;

public class CIDashboardHelper {
  public static final double MAX_VALUE = truncate(Double.MAX_VALUE);

  public static double truncate(double input) {
    return Math.floor(input * 100) / 100;
  }
}
