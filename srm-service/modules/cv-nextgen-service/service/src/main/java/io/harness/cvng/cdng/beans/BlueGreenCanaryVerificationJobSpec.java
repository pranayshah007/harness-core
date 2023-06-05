/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cvng.verificationjob.entities.CanaryBlueGreenVerificationJob.CanaryBlueGreenVerificationJobBuilder;
import io.harness.cvng.verificationjob.entities.VerificationJob;
import io.harness.pms.yaml.ParameterField;

import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@FieldNameConstants(innerTypeName = "BlueGreenCanaryVerificationJobSpecKeys")
@OwnedBy(HarnessTeam.CV)
@SuperBuilder
@NoArgsConstructor
public abstract class BlueGreenCanaryVerificationJobSpec extends VerificationJobSpec {
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH, value = "Example: 50, You can put max upto 50.")
  ParameterField<String> trafficSplitPercentage;

  protected CanaryBlueGreenVerificationJobBuilder addFieldValues(
      CanaryBlueGreenVerificationJobBuilder canaryVerificationJobBuilder) {
    canaryVerificationJobBuilder.sensitivity(
        VerificationJob.RuntimeParameter.builder().isRuntimeParam(false).value(getSensitivity().getValue()).build());
    if (getTrafficSplitPercentage() != null && getTrafficSplitPercentage().getValue() != null) {
      canaryVerificationJobBuilder =
          canaryVerificationJobBuilder.trafficSplitPercentageV2(VerificationJob.RuntimeParameter.builder()
                                                                    .isRuntimeParam(false)
                                                                    .value(getTrafficSplitPercentage().getValue())
                                                                    .build());
    }
    return canaryVerificationJobBuilder;
  }

  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(sensitivity, generateErrorMessageFromParam(VerificationJobSpecKeys.sensitivity));
    if (getTrafficSplitPercentage() != null && getTrafficSplitPercentage().getValue() != null) {
      int trafficSplitPercentage = Integer.parseInt(getTrafficSplitPercentage().getValue());
      if (trafficSplitPercentage > 0 && trafficSplitPercentage <= 50) {
        throw new IllegalArgumentException("trafficSplitPercentage needs to be between 1 to 50");
      }
    }
  }
}
