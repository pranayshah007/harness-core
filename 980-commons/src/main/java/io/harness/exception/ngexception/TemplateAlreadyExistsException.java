/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.ngexception;

import static io.harness.eraro.ErrorCode.TEMPLATE_ALREADY_EXISTS_EXCEPTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;

@OwnedBy(HarnessTeam.CDC)
public class TemplateAlreadyExistsException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public TemplateAlreadyExistsException(String message) {
    super(message, null, TEMPLATE_ALREADY_EXISTS_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public TemplateAlreadyExistsException(String message, Throwable cause) {
    super(message, cause, TEMPLATE_ALREADY_EXISTS_EXCEPTION, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
