/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

import io.harness.cvng.beans.job.VerificationJobType;

import java.util.Objects;

public enum AppliedDeploymentAnalysisType {
  CANARY,
  NO_ANALYSIS,
  ROLLING,
  TEST,
  SIMPLE;

  public static AppliedDeploymentAnalysisType fromVerificationJobType(VerificationJobType verificationJobType) {
    AppliedDeploymentAnalysisType appliedDeploymentAnalysisType = NO_ANALYSIS;
    if (Objects.nonNull(verificationJobType)) {
      switch (verificationJobType) {
        case CANARY:
          appliedDeploymentAnalysisType = CANARY;
          break;
        case AUTO:
        case BLUE_GREEN:
        case ROLLING:
          appliedDeploymentAnalysisType = ROLLING;
          break;
        case TEST:
          appliedDeploymentAnalysisType = TEST;
          break;
        case SIMPLE:
          appliedDeploymentAnalysisType = SIMPLE;
          break;
        default:
          throw new IllegalArgumentException("Unrecognised VerificationJobType " + verificationJobType);
      }
    }
    return appliedDeploymentAnalysisType;
  }
}
