/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CI)
public final class CITaskConstants {
  public static final String INIT_K8 = "INIT_K8";
  public static final String INIT_VM = "INIT_VM";
  public static final String INIT_DOCKER = "INIT_DOCKER";
  public static final String EXECUTE_STEP_K8 = "EXECUTE_STEP_K8";
  public static final String EXECUTE_STEP_VM = "EXECUTE_AWS_VM";
  public static final String CLEANUP_K8 = "CLEANUP_K8";
  public static final String CLEANUP_VM = "CLEANUP_VM";
}
