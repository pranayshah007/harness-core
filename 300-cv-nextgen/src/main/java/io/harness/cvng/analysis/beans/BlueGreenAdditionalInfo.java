/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import static io.harness.cvng.analysis.CVAnalysisConstants.AFTER;
import static io.harness.cvng.analysis.CVAnalysisConstants.BEFORE;

import io.harness.cvng.beans.job.VerificationJobType;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BlueGreenAdditionalInfo extends CanaryBlueGreenAdditionalInfo {
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.BLUE_GREEN;
  }

  @Override
  public void setFieldNames() {
    this.setCanaryInstancesLabel(AFTER);
    this.setPrimaryInstancesLabel(BEFORE);
  }
}
