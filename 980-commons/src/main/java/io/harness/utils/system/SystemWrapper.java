/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils.system;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

/**
 * A wrapper for {@link System} operations to help when writing unit test using Mockito.
 *
 * When we do {@code Mockito.mockStatic(System.class)} the JVM freezes, the class was created to solve/workaround that.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class SystemWrapper {
  private static final String DEPLOY_MODE = System.getenv("DEPLOY_MODE");
  private static final String DEPLOY_VERSION = System.getenv("DEPLOY_VERSION");

  public static long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public static String getenv(String name) {
    return System.getenv(name);
  }

  public static boolean checkIfEnvOnPremOrCommunity() {
    return (DEPLOY_MODE != null && (DEPLOY_MODE.equals("ONPREM") || DEPLOY_MODE.equals("KUBERNETES_ONPREM")))
        || (DEPLOY_VERSION != null && DEPLOY_VERSION.equals("COMMUNITY"));
  }
}
