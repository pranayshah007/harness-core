/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.data.structure.CollectionUtils;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class EnvironmentMapper {
  public EnvironmentStepParameters toEnvironmentStepParameters(
      EnvironmentPlanCreatorConfig environmentPlanCreatorConfig) {
    Map<String, Object> serviceOverrides = new HashMap<>();
    if (areSvcOverrideVariablesPresent(environmentPlanCreatorConfig)) {
      serviceOverrides = NGVariablesUtils.getMapOfVariables(
          environmentPlanCreatorConfig.getServiceOverrideConfig().getServiceOverrideInfoConfig().getVariables());
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

  private boolean areSvcOverrideVariablesPresent(EnvironmentPlanCreatorConfig environmentPlanCreatorConfig) {
    return environmentPlanCreatorConfig != null && environmentPlanCreatorConfig.getServiceOverrideConfig() != null
        && environmentPlanCreatorConfig.getServiceOverrideConfig().getServiceOverrideInfoConfig() != null
        && environmentPlanCreatorConfig.getServiceOverrideConfig().getServiceOverrideInfoConfig().getVariables()
        != null;
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

  private Map<String, Object> overrideVariables(List<NGVariable> base, List<NGVariable> override) {
    if (isEmpty(base)) {
      return NGVariablesUtils.getMapOfVariables(override);
    }

    final Map<String, Object> v1 = NGVariablesUtils.getMapOfVariables(base);
    v1.putAll(NGVariablesUtils.getMapOfVariables(override));
    return v1;
  }

  public EnvironmentOutcome toEnvironmentOutcome(Environment environment,
      @NonNull NGEnvironmentConfig ngEnvironmentConfig, @NonNull NGServiceOverrideConfig ngServiceOverrides,
      @Nullable EnvironmentGroupEntity envGroup) {
    List<NGVariable> svcOverrideVariables = ngServiceOverrides.getServiceOverrideInfoConfig() == null
        ? new ArrayList<>()
        : ngServiceOverrides.getServiceOverrideInfoConfig().getVariables();
    final Map<String, Object> variables =
        overrideVariables(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables(), svcOverrideVariables);
    return EnvironmentOutcome.builder()
        .identifier(IdentifierRefHelper.getRefFromIdentifierOrRef(environment.getAccountId(),
            environment.getOrgIdentifier(), environment.getProjectIdentifier(), environment.getIdentifier()))
        .name(StringUtils.defaultIfBlank(environment.getName(), ""))
        .description(StringUtils.defaultIfBlank(environment.getDescription(), ""))
        .tags(TagMapper.convertToMap(environment.getTags()))
        .type(environment.getType())
        .environmentRef(IdentifierRefHelper.getRefFromIdentifierOrRef(environment.getAccountId(),
            environment.getOrgIdentifier(), environment.getProjectIdentifier(), environment.getIdentifier()))
        .variables(variables)
        .envGroupRef(envGroup != null ? IdentifierRefHelper.getRefFromIdentifierOrRef(envGroup.getAccountId(),
                         envGroup.getOrgIdentifier(), envGroup.getProjectIdentifier(), envGroup.getIdentifier())
                                      : null)
        .envGroupName(envGroup != null ? envGroup.getName() : null)
        .build();
  }
}
