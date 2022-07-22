/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.eraro.ErrorCode.FILTER_CREATION_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.bean.FilterCreatorErrorResponse;

@OwnedBy(PIPELINE)
public class FilterCreatorException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  FilterCreatorErrorResponse errorResponse;

  public FilterCreatorException(String message) {
    super(message, null, FILTER_CREATION_ERROR, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public FilterCreatorException(String message, Throwable cause) {
    super(message, cause, FILTER_CREATION_ERROR, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public FilterCreatorException(String message, FilterCreatorErrorResponse errorResponse) {
    super(message, null, FILTER_CREATION_ERROR, Level.ERROR, null, null, errorResponse);
    super.param(MESSAGE_ARG, message);
  }
}
