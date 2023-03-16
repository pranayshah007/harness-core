/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.cdng.infra.yaml.InfrastructurePlanCreatorConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;

@OwnedBy(PIPELINE)
@UtilityClass
public class InfrastructureEntityConfigMapper {
  @NotNull
  public String toYaml(InfrastructureConfig infrastructureConfig) {
    try {
      return YamlPipelineUtils.getYamlString(infrastructureConfig);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create infrastructure entity due to " + e.getMessage());
    }
  }

  @NotNull
  public InfrastructureConfig toInfrastructureConfig(InfrastructureEntity infrastructureEntity) {
    Infrastructure infrastructure = null;
    boolean allowSimultaneousDeployments = false;
    ServiceDefinitionType deploymentType = null;
    if (isNotEmpty(infrastructureEntity.getYaml())) {
      try {
        final InfrastructureConfig config =
            YamlPipelineUtils.read(infrastructureEntity.getYaml(), InfrastructureConfig.class);
        validateFieldsOrThrow(config, infrastructureEntity);
        infrastructure = config.getInfrastructureDefinitionConfig().getSpec();
        allowSimultaneousDeployments = config.getInfrastructureDefinitionConfig().isAllowSimultaneousDeployments();
        deploymentType = config.getInfrastructureDefinitionConfig().getDeploymentType();
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create infrastructure config due to " + e.getMessage());
      }
    }

    return InfrastructureConfig.builder()
        .infrastructureDefinitionConfig(InfrastructureDefinitionConfig.builder()
                                            .name(infrastructureEntity.getName())
                                            .description(infrastructureEntity.getDescription())
                                            .tags(convertToMap(infrastructureEntity.getTags()))
                                            .identifier(infrastructureEntity.getIdentifier())
                                            .orgIdentifier(infrastructureEntity.getOrgIdentifier())
                                            .projectIdentifier(infrastructureEntity.getProjectIdentifier())
                                            .environmentRef(infrastructureEntity.getEnvIdentifier())
                                            .type(infrastructureEntity.getType())
                                            .spec(infrastructure)
                                            .deploymentType(deploymentType)
                                            .allowSimultaneousDeployments(allowSimultaneousDeployments)
                                            .build())
        .build();
  }

  @NotNull
  public List<InfrastructurePlanCreatorConfig> toInfrastructurePlanCreatorConfig(
      List<InfrastructureConfig> infrastructureConfigs) {
    return infrastructureConfigs.stream()
        .map(config
            -> InfrastructurePlanCreatorConfig.builder()
                   .ref(config.getInfrastructureDefinitionConfig().getIdentifier())
                   .infrastructureDefinitionConfig(config.getInfrastructureDefinitionConfig())
                   .build())
        .collect(Collectors.toList());
  }

  private static void validateFieldsOrThrow(InfrastructureConfig fromYaml, InfrastructureEntity requestedEntity) {
    Map<String, Pair<String, String>> mismatchedEntries = new HashMap<>();
    if (StringUtils.compare(
            fromYaml.getInfrastructureDefinitionConfig().getOrgIdentifier(), requestedEntity.getOrgIdentifier())
        != 0) {
      mismatchedEntries.put("Org Identifier",
          new Pair<>(
              fromYaml.getInfrastructureDefinitionConfig().getOrgIdentifier(), requestedEntity.getOrgIdentifier()));
    }
    if (StringUtils.compare(
            fromYaml.getInfrastructureDefinitionConfig().getProjectIdentifier(), requestedEntity.getProjectIdentifier())
        != 0) {
      mismatchedEntries.put("Project Identifier",
          new Pair<>(fromYaml.getInfrastructureDefinitionConfig().getProjectIdentifier(),
              requestedEntity.getProjectIdentifier()));
    }
    if (StringUtils.compare(
            fromYaml.getInfrastructureDefinitionConfig().getIdentifier(), requestedEntity.getIdentifier())
        != 0) {
      mismatchedEntries.put("InfrastructureDefinition Identifier",
          new Pair<>(fromYaml.getInfrastructureDefinitionConfig().getIdentifier(), requestedEntity.getIdentifier()));
    }
    if (StringUtils.compare(fromYaml.getInfrastructureDefinitionConfig().getName(), requestedEntity.getName()) != 0) {
      mismatchedEntries.put("InfraStructureDefinition Name",
          new Pair<>(fromYaml.getInfrastructureDefinitionConfig().getName(), requestedEntity.getName()));
    }
    if (StringUtils.compare(
            fromYaml.getInfrastructureDefinitionConfig().getType().toString(), requestedEntity.getType().toString())
        != 0) {
      mismatchedEntries.put("InfrastructureDefinition type",
          new Pair<>(
              fromYaml.getInfrastructureDefinitionConfig().getType().toString(), requestedEntity.getType().toString()));
    }
    if (isNotEmpty(mismatchedEntries)) {
      throw new InvalidRequestException(String.format(
          "For the infrastructure [name: %s, identifier: %s], Found mismatch in following fields between yaml and requested value respectively: %s",
          requestedEntity.getName(), requestedEntity.getIdentifier(), mismatchedEntries));
    }
  }
}