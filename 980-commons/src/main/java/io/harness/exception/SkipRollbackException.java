/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;

@OwnedBy(HarnessTeam.CDP)
public class SkipRollbackException extends WingsException {
  public SkipRollbackException(String message) {
    super(message, null, ErrorCode.GENERAL_ERROR, Level.INFO, NOBODY, null);
  }
}
