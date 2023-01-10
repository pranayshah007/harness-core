/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.TERRAGRUNT_EXECUTION_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

import java.util.EnumSet;

@OwnedBy(CDP)
public class TerragruntCommandExecutionException extends WingsException {
  public TerragruntCommandExecutionException(String message, EnumSet<ReportTarget> reportTargets) {
    super(message, null, TERRAGRUNT_EXECUTION_ERROR, Level.ERROR, reportTargets, null);
    super.getParams().put("message", message);
  }
}
