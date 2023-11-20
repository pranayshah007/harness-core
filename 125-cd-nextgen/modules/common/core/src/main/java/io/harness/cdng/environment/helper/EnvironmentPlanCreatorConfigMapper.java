/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.environment.helper;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.gitops.steps.ClusterAgentRef;
import io.harness.cdng.infra.mapper.InfrastructureEntityConfigMapper;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.pms.yaml.ParameterField;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@UtilityClass
@OwnedBy(CDC)
public class EnvironmentPlanCreatorConfigMapper {
  public static EnvironmentPlanCreatorConfig toEnvironmentPlanCreatorConfig(
      String mergedEnvYaml, List<InfrastructureConfig> configs, NGServiceOverrideConfig serviceOverrideConfig) {
    NGEnvironmentInfoConfig ngEnvironmentInfoConfig = fetchEnvironmentConfig(mergedEnvYaml);
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
        .serviceOverrideConfig(serviceOverrideConfig)
        .environmentGlobalOverride(ngEnvironmentInfoConfig.getNgEnvironmentGlobalOverride())
        .infrastructureDefinitions(InfrastructureEntityConfigMapper.toInfrastructurePlanCreatorConfig(configs))
        .build();
  }

  public static NGEnvironmentInfoConfig fetchEnvironmentConfig(@NotNull String envYaml) {
    NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(envYaml);
    if (ngEnvironmentConfig == null || ngEnvironmentConfig.getNgEnvironmentInfoConfig() == null) {
      throw new InvalidRequestException("Environment used is not valid");
    }

    return ngEnvironmentConfig.getNgEnvironmentInfoConfig();
  }

  public EnvironmentPlanCreatorConfig toEnvPlanCreatorConfigWithGitops(
      String mergedEnvYaml, EnvironmentYamlV2 envYaml, NGServiceOverrideConfig serviceOverrideConfig) {
    NGEnvironmentInfoConfig config = fetchEnvironmentConfig(mergedEnvYaml);
    return EnvironmentPlanCreatorConfig.builder()
        .environmentRef(envYaml.getEnvironmentRef())
        .identifier(config.getIdentifier())
        .projectIdentifier(config.getProjectIdentifier())
        .orgIdentifier(config.getOrgIdentifier())
        .description(config.getDescription())
        .name(config.getName())
        .tags(config.getTags())
        .type(config.getType())
        .variables(config.getVariables())
        .serviceOverrideConfig(serviceOverrideConfig)
        .environmentGlobalOverride(config.getNgEnvironmentGlobalOverride())
        .gitOpsClusterRefs(getClusterRefs(envYaml))
        .deployToAll(envYaml.getDeployToAll().getValue() != null && envYaml.getDeployToAll().getValue())
        .build();
  }

  public EnvironmentPlanCreatorConfig toEnvPlanCreatorConfigWithGitOpsFromEnv(String mergedEnvYaml,
      String envIdentifier, NGServiceOverrideConfig serviceOverrideConfig, List<ClusterAgentRef> clusterRefs) {
    NGEnvironmentInfoConfig config = fetchEnvironmentConfig(mergedEnvYaml);
    return EnvironmentPlanCreatorConfig.builder()
        .environmentRef(ParameterField.createValueField(envIdentifier))
        .identifier(config.getIdentifier())
        .projectIdentifier(config.getProjectIdentifier())
        .orgIdentifier(config.getOrgIdentifier())
        .description(config.getDescription())
        .name(config.getName())
        .tags(config.getTags())
        .type(config.getType())
        .variables(config.getVariables())
        .serviceOverrideConfig(serviceOverrideConfig)
        .environmentGlobalOverride(config.getNgEnvironmentGlobalOverride())
        .gitOpsClusterRefs(clusterRefs)
        .deployToAll(false)
        .build();
  }

  public List<ClusterAgentRef> getClusterRefs(EnvironmentYamlV2 environmentV2) {
    if (environmentV2.getDeployToAll().getValue() == null || !environmentV2.getDeployToAll().getValue()) {
      return environmentV2.getGitOpsClusters()
          .getValue()
          .stream()
          .map(cluster
              -> ClusterAgentRef.builder()
                     .clusterId(cluster.getIdentifier().getValue())
                     .agentId(cluster.getAgentIdentifier().getValue())
                     .build())
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }
}