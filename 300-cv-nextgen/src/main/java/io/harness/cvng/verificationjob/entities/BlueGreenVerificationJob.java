/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob.entities;

import io.harness.cvng.beans.job.BlueGreenVerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.verificationjob.CVVerificationJobConstants;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "BlueGreenVerificationJobKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class BlueGreenVerificationJob extends CanaryBlueGreenVerificationJob {
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.BLUE_GREEN;
  }

  @Override
  public void fromDTO(VerificationJobDTO verificationJobDTO) {
    BlueGreenVerificationJobDTO blueGreenVerificationJobDTO = (BlueGreenVerificationJobDTO) verificationJobDTO;
    this.setSensitivity(blueGreenVerificationJobDTO.getSensitivity(),
        VerificationJobDTO.isRuntimeParam(blueGreenVerificationJobDTO.getSensitivity()));
    this.setTrafficSplitPercentageV2(blueGreenVerificationJobDTO.getTrafficSplitPercentage(),
        VerificationJobDTO.isRuntimeParam(blueGreenVerificationJobDTO.getTrafficSplitPercentage()));
    addCommonFileds(verificationJobDTO);
  }

  @Override
  public VerificationJobDTO getVerificationJobDTO() {
    BlueGreenVerificationJobDTO blueGreenVerificationJobDTO = new BlueGreenVerificationJobDTO();
    blueGreenVerificationJobDTO.setSensitivity(
        getSensitivity() == null ? CVVerificationJobConstants.RUNTIME_STRING : getSensitivity().name());
    blueGreenVerificationJobDTO.setTrafficSplitPercentage(getTrafficSplitPercentage() == null
            ? CVVerificationJobConstants.RUNTIME_STRING
            : String.valueOf(getTrafficSplitPercentage()));
    populateCommonFields(blueGreenVerificationJobDTO);
    return blueGreenVerificationJobDTO;
  }
}
