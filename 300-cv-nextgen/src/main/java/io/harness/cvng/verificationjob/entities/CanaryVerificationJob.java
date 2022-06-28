/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import io.harness.cvng.beans.job.CanaryVerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.verificationjob.CVVerificationJobConstants;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "CanaryVerificationJobKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class CanaryVerificationJob extends CanaryBlueGreenVerificationJob {
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.CANARY;
  }

  @Override
  public void fromDTO(VerificationJobDTO verificationJobDTO) {
    CanaryVerificationJobDTO canaryVerificationJobDTO = (CanaryVerificationJobDTO) verificationJobDTO;
    this.setSensitivity(canaryVerificationJobDTO.getSensitivity(),
        VerificationJobDTO.isRuntimeParam(canaryVerificationJobDTO.getSensitivity()));
    this.setTrafficSplitPercentageV2(canaryVerificationJobDTO.getTrafficSplitPercentage(),
        VerificationJobDTO.isRuntimeParam(canaryVerificationJobDTO.getTrafficSplitPercentage()));
    addCommonFileds(verificationJobDTO);
  }

  @Override
  public VerificationJobDTO getVerificationJobDTO() {
    CanaryVerificationJobDTO canaryVerificationJobDTO = new CanaryVerificationJobDTO();
    canaryVerificationJobDTO.setSensitivity(
        getSensitivity() == null ? CVVerificationJobConstants.RUNTIME_STRING : getSensitivity().name());
    canaryVerificationJobDTO.setTrafficSplitPercentage(getTrafficSplitPercentage() == null
            ? CVVerificationJobConstants.RUNTIME_STRING
            : String.valueOf(getTrafficSplitPercentage()));
    populateCommonFields(canaryVerificationJobDTO);
    return canaryVerificationJobDTO;
  }
}
