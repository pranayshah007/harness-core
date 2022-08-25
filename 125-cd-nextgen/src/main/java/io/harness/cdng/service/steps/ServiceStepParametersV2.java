/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
@TypeAlias("serviceStepParameters")
@RecasterAlias("io.harness.cdng.service.steps.ServiceStepParametersV2")
public class ServiceStepParametersV2 implements StepParameters {
  String type;
  String identifier;
  String name;
  String description;
  Map<String, String> tags;
  Boolean gitOpsEnabled;

  // Should be resolved serviceDefinition (including merged runtime inputs) but expression resolution should be skipped
  // as manifests expressions may not be resolved.
  @SkipAutoEvaluation ParameterField<ServiceDefinition> serviceDefinition;

  public static ServiceStepParametersV2 fromServiceV2InfoConfig(NGServiceV2InfoConfig serviceV2InfoConfig) {
    if (serviceV2InfoConfig == null) {
      throw new InvalidRequestException("Exception in creating step inputs for service step.");
    }
    return ServiceStepParametersV2.builder()
        .identifier(serviceV2InfoConfig.getIdentifier())
        .name(serviceV2InfoConfig.getName())
        .type(serviceV2InfoConfig.getServiceDefinition().getType().getYamlName())
        .tags(serviceV2InfoConfig.getTags())
        .gitOpsEnabled(serviceV2InfoConfig.getGitOpsEnabled())
        .description(serviceV2InfoConfig.getDescription())
        .serviceDefinition(ParameterField.createValueField(serviceV2InfoConfig.getServiceDefinition()))
        .build();
  }
}
