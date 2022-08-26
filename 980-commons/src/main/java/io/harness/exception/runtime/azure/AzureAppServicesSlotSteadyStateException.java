/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(CDP)
public class AzureAppServicesSlotSteadyStateException extends AzureAppServicesRuntimeException {
  @Getter private String action;
  @Getter private long timeoutIntervalInMin;

  public AzureAppServicesSlotSteadyStateException(String message) {
    super(message);
  }

  public AzureAppServicesSlotSteadyStateException(
      String message, String action, long timeoutIntervalInMin, Throwable cause) {
    super(message, cause);
    this.action = action;
    this.timeoutIntervalInMin = timeoutIntervalInMin;
  }
}
