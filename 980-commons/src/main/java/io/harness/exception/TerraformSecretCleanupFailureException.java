/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.eraro.ErrorCode.TERRAFORM_VAULT_SECRET_CLEANUP_FAILURE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

import java.util.EnumSet;

@OwnedBy(CDP)
public class TerraformSecretCleanupFailureException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public TerraformSecretCleanupFailureException(String message) {
    super(message, null, TERRAFORM_VAULT_SECRET_CLEANUP_FAILURE, Level.ERROR, null,
        EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }

  public TerraformSecretCleanupFailureException(String message, Throwable cause) {
    super(message, cause, TERRAFORM_VAULT_SECRET_CLEANUP_FAILURE, Level.ERROR, null,
        EnumSet.of(FailureType.APPLICATION_ERROR));
    param(MESSAGE_KEY, message);
  }
}
