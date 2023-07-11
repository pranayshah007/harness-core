/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans;

import io.harness.cvng.verificationjob.entities.VerificationJob.VerificationJobBuilder;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import com.google.common.base.Preconditions;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@AllArgsConstructor
@TypeAlias("verifyStepParameters")
public class CVNGStepParameter implements SpecParameters {
  ParameterField<String> serviceIdentifier;
  ParameterField<String> envIdentifier;
  ParameterField<String> deploymentTag;
  ParameterField<String> sensitivity;
  ParameterField<Boolean> failOnNoAnalysis;
  ParameterField<Boolean> shouldUseCDNodes;
  ParameterField<String> testNodeRegExPattern;
  ParameterField<String> controlNodeRegExPattern;
  VerificationJobSpec spec;
  ParameterField<String> baseline;
  MonitoredServiceNode monitoredService;

  public String getServiceIdentifier() {
    Preconditions.checkNotNull(serviceIdentifier.getValue());
    return serviceIdentifier.getValue();
  }
  public String getEnvIdentifier() {
    Preconditions.checkNotNull(envIdentifier.getValue());
    return envIdentifier.getValue();
  }
  @ApiModelProperty(hidden = true)
  public VerificationJobBuilder getVerificationJobBuilder() {
    return spec.getVerificationJobBuilder();
  }
}
