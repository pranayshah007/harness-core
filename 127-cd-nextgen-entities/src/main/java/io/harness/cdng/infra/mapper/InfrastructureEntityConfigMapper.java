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
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.base.Preconditions;
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

  public static InfrastructureConfig toInfrastructureConfig(String yaml) {
    Preconditions.checkArgument(isNotEmpty(yaml));
    try {
      return YamlPipelineUtils.read(yaml, InfrastructureConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create infrastructure config due to " + e.getMessage());
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

  static void validateFieldsOrThrow(InfrastructureConfig fromYaml, InfrastructureEntity requestedEntity) {
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

  static void checkForFieldMismatch(InfrastructureConfig fromYaml, InfrastructureRequestDTO requestDTO) {
    Map<String, Pair<String, String>> mismatchedEntries = new HashMap<>();
    InfrastructureDefinitionConfig infraConfigFromYaml = fromYaml.getInfrastructureDefinitionConfig();
    if (compareIfNonEmpty(infraConfigFromYaml.getOrgIdentifier(), requestDTO.getOrgIdentifier())) {
      mismatchedEntries.put(
          "Org Identifier", new Pair<>(infraConfigFromYaml.getOrgIdentifier(), requestDTO.getOrgIdentifier()));
    }
    if (compareIfNonEmpty(infraConfigFromYaml.getProjectIdentifier(), requestDTO.getProjectIdentifier())) {
      mismatchedEntries.put("Project Identifier",
          new Pair<>(infraConfigFromYaml.getProjectIdentifier(), requestDTO.getProjectIdentifier()));
    }
    if (compareIfNonEmpty(infraConfigFromYaml.getIdentifier(), requestDTO.getIdentifier())) {
      mismatchedEntries.put("InfrastructureDefinition Identifier",
          new Pair<>(infraConfigFromYaml.getIdentifier(), requestDTO.getIdentifier()));
    }
    if (compareIfNonEmpty(infraConfigFromYaml.getName(), requestDTO.getName())) {
      mismatchedEntries.put(
          "InfraStructureDefinition Name", new Pair<>(infraConfigFromYaml.getName(), requestDTO.getName()));
    }
    if (infraConfigFromYaml.getType() != null && requestDTO.getType() != null
        && requestDTO.getType() != infraConfigFromYaml.getType()) {
      mismatchedEntries.put("InfrastructureDefinition type",
          new Pair<>(infraConfigFromYaml.getType().toString(), requestDTO.getType().toString()));
    }
    if (isNotEmpty(mismatchedEntries)) {
      throw new InvalidRequestException(String.format(
          "For the infrastructure [name: %s, identifier: %s], Found mismatch in following fields between yaml and requested value respectively: %s",
          requestDTO.getName(), requestDTO.getIdentifier(), mismatchedEntries));
    }
  }

  static boolean compareIfNonEmpty(String s1, String s2) {
    return EmptyPredicate.isNotEmpty(s1) && EmptyPredicate.isNotEmpty(s2) && StringUtils.compare(s1, s2) != 0;
  }
}