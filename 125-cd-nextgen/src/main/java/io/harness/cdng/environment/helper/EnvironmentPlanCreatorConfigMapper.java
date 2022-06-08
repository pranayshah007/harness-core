/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.yaml.ClusterYaml;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGServiceOverrides;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDC)
public class EnvironmentPlanCreatorConfigMapper {
  public static EnvironmentPlanCreatorConfig toEnvironmentPlanCreatorConfig(
      String mergedEnvYaml, List<InfrastructureConfig> configs, NGServiceOverrides serviceOverride) {
    NGEnvironmentInfoConfig ngEnvironmentInfoConfig =
        EnvironmentMapper.toNGEnvironmentConfig(mergedEnvYaml).getNgEnvironmentInfoConfig();
    return EnvironmentPlanCreatorConfig.builder()
        .environmentRef(ParameterField.createValueField(ngEnvironmentInfoConfig.getIdentifier()))
        .identifier(ngEnvironmentInfoConfig.getIdentifier())
        .projectIdentifier(ngEnvironmentInfoConfig.getProjectIdentifier())
        .orgIdentifier(ngEnvironmentInfoConfig.getOrgIdentifier())
        .description(ngEnvironmentInfoConfig.getDescription())
        .name(ngEnvironmentInfoConfig.getName())
        .tags(ngEnvironmentInfoConfig.getTags())
        .type(ngEnvironmentInfoConfig.getType())
        .variables(ngEnvironmentInfoConfig.getVariables())
        .serviceOverrides(serviceOverride)
        .infrastructureDefinitions(InfrastructureEntityConfigMapper.toInfrastructurePlanCreatorConfig(configs))
        .build();
  }

  public EnvironmentPlanCreatorConfig toEnvPlanCreatorConfigWithGitops(
      String mergedEnvYaml, EnvironmentYamlV2 envYaml, NGServiceOverrides serviceOverride) {
    NGEnvironmentInfoConfig ngEnvironmentInfoConfig =
        EnvironmentMapper.toNGEnvironmentConfig(mergedEnvYaml).getNgEnvironmentInfoConfig();
    return EnvironmentPlanCreatorConfig.builder()
        .environmentRef(envYaml.getEnvironmentRef())
        .identifier(ngEnvironmentInfoConfig.getIdentifier())
        .projectIdentifier(ngEnvironmentInfoConfig.getProjectIdentifier())
        .orgIdentifier(ngEnvironmentInfoConfig.getOrgIdentifier())
        .description(ngEnvironmentInfoConfig.getDescription())
        .name(ngEnvironmentInfoConfig.getName())
        .tags(ngEnvironmentInfoConfig.getTags())
        .type(ngEnvironmentInfoConfig.getType())
        .variables(ngEnvironmentInfoConfig.getVariables())
        .serviceOverrides(serviceOverride)
        .gitOpsClusterRefs(getClusterRefs(envYaml))
        .deployToAll(envYaml.isDeployToAll())
        .build();
  }

  private List<String> getClusterRefs(EnvironmentYamlV2 environmentV2) {
    if (!environmentV2.isDeployToAll()) {
      return environmentV2.getGitOpsClusters()
          .stream()
          .map(ClusterYaml::getRef)
          .map(ParameterField::getValue)
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }
}