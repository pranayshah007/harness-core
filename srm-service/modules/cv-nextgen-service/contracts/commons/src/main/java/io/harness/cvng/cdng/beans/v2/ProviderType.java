/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import io.harness.cvng.models.VerificationType;

public enum ProviderType {
  ERRORS,
  LOGS,
  METRICS;

  public static ProviderType fromVerificationType(VerificationType verificationType) {
    switch (verificationType) {
      case LOG:
        return LOGS;
      case TIME_SERIES:
        return METRICS;
      default:
        return ERRORS;
    }
  }
}
