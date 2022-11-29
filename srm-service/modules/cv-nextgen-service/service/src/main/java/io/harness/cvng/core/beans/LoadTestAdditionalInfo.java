/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans;

import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.verificationjob.beans.AdditionalInfo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoadTestAdditionalInfo extends AdditionalInfo {
  String baselineDeploymentTag;
  Long baselineStartTime;
  String currentDeploymentTag;
  long currentStartTime;

  public boolean isBaselineRun() {
    if (baselineDeploymentTag == null) {
      return true;
    }
    return false;
  }
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.TEST;
  }
}
