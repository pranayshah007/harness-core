/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envGroup.yaml.EnvGroupPlanCreatorConfig;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.data.structure.CollectionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.envGroup.EnvironmentGroupOutcome;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class EnvironmentMapper {
  public EnvironmentStepParameters toEnvironmentStepParameters(
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig) {
    Map<String, Object> serviceOverrides = new HashMap<>();
    if (environmentPlanCreatorConfig.getServiceOverrides() != null
        && isNotEmpty(environmentPlanCreatorConfig.getServiceOverrides().getVariables())) {
      serviceOverrides =
          NGVariablesUtils.getMapOfVariables(environmentPlanCreatorConfig.getServiceOverrides().getVariables());
    }
    return EnvironmentStepParameters.builder()
        .environmentRef(environmentPlanCreatorConfig.getEnvironmentRef())
        .name(environmentPlanCreatorConfig.getName())
        .identifier(environmentPlanCreatorConfig.getIdentifier())
        .description(environmentPlanCreatorConfig.getDescription())
        .tags(environmentPlanCreatorConfig.getTags())
        .type(environmentPlanCreatorConfig.getType())
        .serviceOverrides(serviceOverrides)
        .variables(NGVariablesUtils.getMapOfVariables(environmentPlanCreatorConfig.getVariables()))
        .build();
  }

  public EnvironmentStepParameters toEnvironmentStepParameters(EnvGroupPlanCreatorConfig envGroupPlanCreatorConfig) {
    return EnvironmentStepParameters.builder()
        .name(envGroupPlanCreatorConfig.getName())
        .identifier(envGroupPlanCreatorConfig.getIdentifier())
        .description(envGroupPlanCreatorConfig.getDescription())
        .tags(envGroupPlanCreatorConfig.getTags())
        .envGroupRef(envGroupPlanCreatorConfig.getEnvironmentGroupRef())
        .build();
  }

  public EnvironmentOutcome toEnvironmentOutcome(EnvironmentStepParameters stepParameters) {
    overrideServiceVariables(stepParameters.getVariables(), stepParameters.getServiceOverrides());
    return EnvironmentOutcome.builder()
        .identifier(stepParameters.getIdentifier())
        .name(stepParameters.getName() != null ? stepParameters.getName() : "")
        .description(stepParameters.getDescription() != null ? stepParameters.getDescription() : "")
        .tags(CollectionUtils.emptyIfNull(stepParameters.getTags()))
        .type(stepParameters.getType())
        .environmentRef(stepParameters.getEnvironmentRef().getValue())
        .variables(stepParameters.getVariables())
        .build();
  }

  private void overrideServiceVariables(Map<String, Object> variables, Map<String, Object> serviceOverrides) {
    if (variables != null && serviceOverrides != null) {
      variables.putAll(serviceOverrides);
    }
  }

  private Map<String, Object> overrideVariables(List<NGVariable> variables1, List<NGVariable> variables2) {
    if (EmptyPredicate.isNotEmpty(variables1) && EmptyPredicate.isNotEmpty(variables2)) {
      Map<String, Object> v1 = NGVariablesUtils.getMapOfVariables(variables1);
      v1.putAll(NGVariablesUtils.getMapOfVariables(variables2));
      return v1;
    }
    return new HashMap<>();
  }

  public EnvironmentGroupOutcome toEnvironmentGroupOutcome(EnvironmentStepParameters stepParameters) {
    return EnvironmentGroupOutcome.builder()
        .identifier(stepParameters.getIdentifier())
        .name(stepParameters.getName() != null ? stepParameters.getName() : "")
        .description(stepParameters.getDescription() != null ? stepParameters.getDescription() : "")
        .tags(CollectionUtils.emptyIfNull(stepParameters.getTags()))
        .build();
  }

  public static EnvironmentOutcome toEnvironmentOutcome(Environment environment, NGEnvironmentConfig ngEnvironmentConfig, NGServiceConfig ngServiceConfig) {
    final Map<String, Object> variables = overrideVariables(
        ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec().getVariables(),
        ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables());
    return EnvironmentOutcome.builder()
        .identifier(environment.getIdentifier())
        .name(StringUtils.defaultIfBlank(environment.getName(), ""))
        .description(StringUtils.defaultIfBlank(environment.getDescription(), ""))
        .tags(TagMapper.convertToMap(environment.getTags()))
        .type(environment.getType())
        .environmentRef(environment.getIdentifier())
        .variables(variables)
        .build();
  }
}
