/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

import java.util.EnumSet;

@OwnedBy(CDP)
public class OciHelmDockerApiException extends WingsException {
  public OciHelmDockerApiException(String message) {
    super(message, null, GENERAL_ERROR, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    super.param("message", message);
  }

  public OciHelmDockerApiException(String message, Throwable cause) {
    super(message, cause, GENERAL_ERROR, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    super.param("message", message);
  }
}
