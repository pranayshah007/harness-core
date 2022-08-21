/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public final class PhysicalDataCenterConstants {
  private PhysicalDataCenterConstants() {}

  public static final int EXECUTION_TIMEOUT_IN_SECONDS = 45;
  public static final String DEFAULT_HOST_VALIDATION_FAILED_MSG = "Host Validation failed";
  public static final String TRUE_STR = "true";
  public static final String DEFAULT_SSH_PORT = "22";
  public static final String DEFAULT_WINRM_PORT = "5986";
  public static final int HOSTS_NUMBER_VALIDATION_LIMIT = 10;
  public static final int DEFAULT_HOST_SOCKET_TIMEOUT_MS = 5000;
}
