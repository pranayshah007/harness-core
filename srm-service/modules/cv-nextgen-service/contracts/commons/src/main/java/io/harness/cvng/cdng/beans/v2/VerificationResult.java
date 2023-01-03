/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import io.harness.cvng.analysis.beans.Risk;

import java.util.Objects;

public enum VerificationResult {
  PASSED,
  FAILED,
  NO_ANALYSIS;

  public static VerificationResult fromRisk(Risk risk) {
    VerificationResult verificationResult = NO_ANALYSIS;
    if (Objects.nonNull(risk)) {
      switch (risk) {
        case HEALTHY:
        case OBSERVE:
        case NEED_ATTENTION:
          verificationResult = PASSED;
          break;
        case UNHEALTHY:
          verificationResult = FAILED;
          break;
        case NO_DATA:
        case NO_ANALYSIS:
          break;
        default:
          throw new IllegalArgumentException("Unrecognised Risk " + risk);
      }
    }
    return verificationResult;
  }
}
