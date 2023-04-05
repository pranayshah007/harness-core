/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import java.util.EnumSet;

@TargetModule(HarnessModule._955_DELEGATE_BEANS)
public enum FailureType {
  EXPIRED(""),
  DELEGATE_PROVISIONING(""),
  CONNECTIVITY(""),
  AUTHENTICATION(""),
  VERIFICATION_FAILURE(""),
  APPLICATION_ERROR(""),
  AUTHORIZATION_ERROR(""),
  TIMEOUT_ERROR(""),
  POLICY_EVALUATION_FAILURE(""),
  INPUT_TIMEOUT_FAILURE(""),
  APPROVAL_REJECTION(""),
  DELEGATE_RESTART(""),
  USER_MARKED_FAILURE("");
  String errorMessage;

  public static final EnumSet<FailureType> TIMEOUT = EnumSet.of(TIMEOUT_ERROR);

  FailureType(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  String getErrorMessageFromType(Exception e) {
    return this.errorMessage + " due to: " + e.getMessage();
  }
}
