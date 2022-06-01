/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * A wrapper for {@link System} operations to help when writing unit test using Mockito.
 *
 * When we do {@code Mockito.mockStatic(System.class)} the JVM freezes, the class was created to solve/workaround that.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class SystemWrapper {
  public static long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public static String getenv(String name) {
    return System.getenv(name);
  }
}
