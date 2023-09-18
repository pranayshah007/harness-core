/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.ENV_SERVICE_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_GLOBAL_OVERRIDE;
import static io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType.INFRA_SERVICE_OVERRIDE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesType;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class EnvironmentMapper {
  private static final List<ServiceOverridesType> reverseOverridePriority =
      List.of(ENV_GLOBAL_OVERRIDE, ENV_SERVICE_OVERRIDE, INFRA_GLOBAL_OVERRIDE, INFRA_SERVICE_OVERRIDE);

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
      @Nullable EnvironmentGroupEntity envGroup,
      Map<ServiceOverridesType, NGServiceOverrideConfigV2> overridesV2Configs, boolean isOverrideV2Enabled) {
    List<NGVariable> svcOverrideVariables = ngServiceOverrides.getServiceOverrideInfoConfig() == null
        ? new ArrayList<>()
        : ngServiceOverrides.getServiceOverrideInfoConfig().getVariables();
    final Map<String, Object> variables = isOverrideV2Enabled
        ? overrideVariablesV2(overridesV2Configs)
        : overrideVariables(ngEnvironmentConfig.getNgEnvironmentInfoConfig().getVariables(), svcOverrideVariables);

    return EnvironmentOutcome.builder()
        .identifier(IdentifierRefHelper.getRefFromIdentifierOrRef(environment.getAccountId(),
            environment.getOrgIdentifier(), environment.getProjectIdentifier(), environment.getIdentifier()))
        .name(StringUtils.defaultIfBlank(environment.getName(), ""))
        .description(StringUtils.defaultIfBlank(environment.getDescription(), ""))
        .tags(TagMapper.convertToMap(environment.getTags()))
        .type(environment.getType())
        .v1Type(environment.getType() == EnvironmentType.Production ? "PROD" : "NON_PROD")
        .environmentRef(IdentifierRefHelper.getRefFromIdentifierOrRef(environment.getAccountId(),
            environment.getOrgIdentifier(), environment.getProjectIdentifier(), environment.getIdentifier()))
        .variables(variables)
        .envGroupRef(envGroup != null ? IdentifierRefHelper.getRefFromIdentifierOrRef(envGroup.getAccountId(),
                         envGroup.getOrgIdentifier(), envGroup.getProjectIdentifier(), envGroup.getIdentifier())
                                      : null)
        .envGroupName(envGroup != null ? envGroup.getName() : null)
        .build();
  }

  private Map<String, Object> overrideVariablesV2(
      Map<ServiceOverridesType, NGServiceOverrideConfigV2> overridesV2Configs) {
    Map<String, NGVariable> finalNGVariables = new HashMap<>();

    for (ServiceOverridesType overrideType : reverseOverridePriority) {
      if (overridesV2Configs.containsKey(overrideType)
          && isNotEmpty(overridesV2Configs.get(overrideType).getSpec().getVariables())) {
        finalNGVariables.putAll(overridesV2Configs.get(overrideType)
                                    .getSpec()
                                    .getVariables()
                                    .stream()
                                    .collect(Collectors.toMap(NGVariable::getName, Function.identity())));
      }
    }
    return NGVariablesUtils.getMapOfVariables(new ArrayList<>(finalNGVariables.values()));
  }
}
