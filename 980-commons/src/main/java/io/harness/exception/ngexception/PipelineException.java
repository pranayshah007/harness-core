/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineException extends WingsException {
  public static String PIPELINE_UPDATE_MESSAGE = "Failed to update pipeline";
  public static String PIPELINE_CREATE_MESSAGE = "Failed to create pipeline";
  public static String PIPELINE_Execution_MESSAGE = "Failed to execute pipeline";

  private static final String MESSAGE_ARG = "message";
  public PipelineException(String message, Throwable cause, ErrorCode errorCode) {
    super(message, cause, errorCode, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
