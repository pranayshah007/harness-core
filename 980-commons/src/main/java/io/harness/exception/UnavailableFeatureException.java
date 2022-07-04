/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.eraro.ErrorCode.FEATURE_UNAVAILABLE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

@OwnedBy(HarnessTeam.PL)
public class UnavailableFeatureException extends WingsException {
  private static final String MESSAGE_KEY = "message";

  public UnavailableFeatureException(String message) {
    super(message, null, FEATURE_UNAVAILABLE, Level.ERROR, null, null);
    super.param(MESSAGE_KEY, message);
  }
}
