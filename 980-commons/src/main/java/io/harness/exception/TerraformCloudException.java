/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.TERRAFORM_CLOUD_ERROR;

import io.harness.eraro.Level;

public class TerraformCloudException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public TerraformCloudException(String message) {
    this(message, null);
  }

  public TerraformCloudException(String message, Throwable th) {
    super(message, th, TERRAFORM_CLOUD_ERROR, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }
}
