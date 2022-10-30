/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.GENERAL_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.helm.HelmCliCommandType;

import java.util.EnumSet;
import javax.validation.constraints.NotNull;
import lombok.Getter;

@OwnedBy(CDP)
public class HelmClientException extends WingsException {
  @Getter private final HelmCliCommandType helmCliCommandType;

  public HelmClientException(String message, @NotNull HelmCliCommandType helmCliCommandType) {
    super(message, null, GENERAL_ERROR, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    this.helmCliCommandType = helmCliCommandType;
    super.param("message", message);
  }

  public HelmClientException(String message, Throwable cause, @NotNull HelmCliCommandType helmCliCommandType) {
    super(message, cause, GENERAL_ERROR, Level.ERROR, null, EnumSet.of(FailureType.APPLICATION_ERROR));
    this.helmCliCommandType = helmCliCommandType;
    super.param("message", message);
  }

  public HelmClientException(
      String message, EnumSet<ReportTarget> reportTargets, @NotNull HelmCliCommandType helmCliCommandType) {
    super(message, null, GENERAL_ERROR, Level.ERROR, reportTargets, EnumSet.of(FailureType.APPLICATION_ERROR));
    this.helmCliCommandType = helmCliCommandType;
    super.param("message", message);
  }

  public HelmClientException(String message, EnumSet<ReportTarget> reportTargets, Throwable t,
      @NotNull HelmCliCommandType helmCliCommandType) {
    super(message, t, GENERAL_ERROR, Level.ERROR, reportTargets, EnumSet.of(FailureType.APPLICATION_ERROR));
    this.helmCliCommandType = helmCliCommandType;
    super.param("message", message);
  }
}
