/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.health;

import static io.harness.eraro.ErrorCode.HEALTH_ERROR;

import io.harness.eraro.Level;
import io.harness.exception.WingsException;

public class HealthException extends WingsException {
  private static final String REASON_KEY = "reason";

  public HealthException(String reason) {
    super(reason, null, HEALTH_ERROR, Level.ERROR, null, null);
    param(REASON_KEY, reason);
  }

  public HealthException(Throwable cause) {
    super(null, cause, HEALTH_ERROR, Level.ERROR, null, null);
  }

  public HealthException(String reason, Throwable cause) {
    super(reason, cause, HEALTH_ERROR, Level.ERROR, null, null);
    param(REASON_KEY, reason);
  }
}
