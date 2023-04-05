/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.mappers;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.checkDuplicateConfigFilesIdentifiersWithIn;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.checkDuplicateManifestIdentifiersWithIn;
import static io.harness.ng.core.environment.validator.SvcEnvV2ManifestValidator.validateNoMoreThanOneHelmOverridePresent;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentBasicInfo;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.dto.EnvironmentResponseDTO;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.YamlPipelineUtils;
import io.harness.validation.JavaxValidator;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;

@OwnedBy(PIPELINE)
@UtilityClass
public class EnvironmentMapper {
  private static final String TOO_MANY_HELM_OVERRIDES_PRESENT_ERROR_MESSAGE =
      "You cannot configure multiple Helm Repo Overrides at the Environment Level. Overrides provided: [%s]";

  public Environment toEnvironmentEntity(String accountId, EnvironmentRequestDTO environmentRequestDTO) {
    final Environment environment;
    if (isNotEmpty(environmentRequestDTO.getYaml())) {
      NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environmentRequestDTO);

      validateOrThrow(environmentRequestDTO, ngEnvironmentConfig);
      environment = Environment.builder()
                        .identifier(environmentRequestDTO.getIdentifier())
                        .accountId(accountId)
                        .orgIdentifier(environmentRequestDTO.getOrgIdentifier())
                        .projectIdentifier(environmentRequestDTO.getProjectIdentifier())
                        .name(environmentRequestDTO.getName())
                        .color(Optional.ofNullable(environmentRequestDTO.getColor()).orElse(HARNESS_BLUE))
                        .description(environmentRequestDTO.getDescription())
                        .type(environmentRequestDTO.getType())
                        .tags(convertToList(environmentRequestDTO.getTags()))
                        .build();

      environment.setYaml(environmentRequestDTO.getYaml());
      if (isEmpty(environment.getYaml())) {
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
      return environment;
    }
    environment = toNGEnvironmentEntity(accountId, environmentRequestDTO);
    NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
    environment.setYaml(toYaml(ngEnvironmentConfig));
    return environment;
  }

  private void validateOrThrow(EnvironmentRequestDTO environmentRequestDTO, NGEnvironmentConfig ngEnvironmentConfig) {
    validateOrThrow(ngEnvironmentConfig);
    validateEnvGlobalOverridesOrThrow(ngEnvironmentConfig);
    validateYamlOrThrow(ngEnvironmentConfig, environmentRequestDTO);
  }

  private void validateOrThrow(NGEnvironmentConfig ngEnvironmentConfig) {
    JavaxValidator.validateOrThrow(ngEnvironmentConfig);
  }

  public Environment toNGEnvironmentEntity(String accountId, EnvironmentRequestDTO dto) {
    return Environment.builder()
        .identifier(dto.getIdentifier())
        .accountId(accountId)
        .orgIdentifier(dto.getOrgIdentifier())
        .projectIdentifier(dto.getProjectIdentifier())
        .name(dto.getName())
        .color(Optional.ofNullable(dto.getColor()).orElse(HARNESS_BLUE))
        .description(dto.getDescription())
        .type(dto.getType())
        .tags(convertToList(dto.getTags()))
        .build();
  }

  public Environment toNGEnvironmentEntity(String accountId, NGEnvironmentConfig envConfig, String color) {
    NGEnvironmentInfoConfig ngEnvironmentInfoConfig = envConfig.getNgEnvironmentInfoConfig();
    return Environment.builder()
        .identifier(ngEnvironmentInfoConfig.getIdentifier())
        .accountId(accountId)
        .orgIdentifier(ngEnvironmentInfoConfig.getOrgIdentifier())
        .projectIdentifier(ngEnvironmentInfoConfig.getProjectIdentifier())
        .name(ngEnvironmentInfoConfig.getName())
        .color(Optional.ofNullable(color).orElse(HARNESS_BLUE))
        .description(ngEnvironmentInfoConfig.getDescription())
        .type(ngEnvironmentInfoConfig.getType())
        .tags(convertToList(ngEnvironmentInfoConfig.getTags()))
        .build();
  }

  public EnvironmentResponseDTO writeDTO(Environment environment) {
    return EnvironmentResponseDTO.builder()
        .accountId(environment.getAccountId())
        .orgIdentifier(environment.getOrgIdentifier())
        .projectIdentifier(environment.getProjectIdentifier())
        .identifier(environment.getIdentifier())
        .name(environment.getName())
        .color(Optional.ofNullable(environment.getColor()).orElse(HARNESS_BLUE))
        .description(environment.getDescription())
        .type(environment.getType())
        .deleted(environment.getDeleted())
        .tags(convertToMap(environment.getTags()))
        .version(environment.getVersion())
        .yaml(environment.getYaml())
        .build();
  }

  public EnvironmentResponse toResponseWrapper(Environment environment) {
    return EnvironmentResponse.builder()
        .environment(writeDTO(environment))
        .createdAt(environment.getCreatedAt())
        .lastModifiedAt(environment.getLastModifiedAt())
        .build();
  }

  public EnvironmentBasicInfo toBasicInfo(Environment environment) {
    return EnvironmentBasicInfo.builder()
        .identifier(environment.getIdentifier())
        .name(environment.getName())
        .description(environment.getDescription())
        .type(environment.getType())
        .accountIdentifier(environment.getAccountId())
        .orgIdentifier(environment.getOrgIdentifier())
        .projectIdentifier(environment.getProjectIdentifier())
        .tags(convertToMap(environment.getTags()))
        .color(environment.getColor())
        .build();
  }

  public static List<EnvironmentResponse> toResponseWrapper(List<Environment> envList) {
    return envList.stream().map(EnvironmentMapper::toResponseWrapper).collect(Collectors.toList());
  }

  public static NGEnvironmentConfig toNGEnvironmentConfig(Environment environmentEntity) {
    if (isNotEmpty(environmentEntity.getYaml())) {
      try {
        return YamlUtils.read(environmentEntity.getYaml(), NGEnvironmentConfig.class);
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create environment config due to " + e.getMessage());
      }
    }
    return NGEnvironmentConfig.builder()
        .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                     .name(environmentEntity.getName())
                                     .identifier(environmentEntity.getIdentifier())
                                     .orgIdentifier(environmentEntity.getOrgIdentifier())
                                     .projectIdentifier(environmentEntity.getProjectIdentifier())
                                     .description(environmentEntity.getDescription())
                                     .tags(convertToMap(environmentEntity.getTags()))
                                     .type(environmentEntity.getType())
                                     .build())
        .build();
  }

  // To be used for EnvironmentV2
  public static NGEnvironmentConfig toNGEnvironmentConfig(String yaml) {
    try {
      return YamlUtils.read(yaml, NGEnvironmentConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create environment config due to " + e.getMessage());
    }
  }

  public static NGEnvironmentConfig toNGEnvironmentConfig(EnvironmentRequestDTO dto) {
    if (isNotEmpty(dto.getYaml())) {
      try {
        return YamlUtils.read(dto.getYaml(), NGEnvironmentConfig.class);
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create environment config due to " + e.getMessage());
      }
    }
    return NGEnvironmentConfig.builder()
        .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                     .name(dto.getName())
                                     .identifier(dto.getIdentifier())
                                     .orgIdentifier(dto.getOrgIdentifier())
                                     .projectIdentifier(dto.getProjectIdentifier())
                                     .description(dto.getDescription())
                                     .tags(dto.getTags())
                                     .type(dto.getType())
                                     .build())
        .build();
  }

  public static String toYaml(@Valid NGEnvironmentConfig ngEnvironmentConfig) {
    try {
      return YamlPipelineUtils.getYamlString(ngEnvironmentConfig);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create environment entity due to " + e.getMessage());
    }
  }

  private void validateEnvGlobalOverridesOrThrow(NGEnvironmentConfig ngEnvironmentConfig) {
    if (ngEnvironmentConfig.getNgEnvironmentInfoConfig() != null
        && ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() != null) {
      final NGEnvironmentGlobalOverride environmentGlobalOverride =
          ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride();
      checkDuplicateManifestIdentifiersWithIn(environmentGlobalOverride.getManifests());
      validateNoMoreThanOneHelmOverridePresent(
          environmentGlobalOverride.getManifests(), TOO_MANY_HELM_OVERRIDES_PRESENT_ERROR_MESSAGE);
      checkDuplicateConfigFilesIdentifiersWithIn(environmentGlobalOverride.getConfigFiles());
    }
  }

  private void validateYamlOrThrow(NGEnvironmentConfig fromYaml, EnvironmentRequestDTO environmentRequest) {
    Map<String, Pair<String, String>> mismatchedEntries = new HashMap<>();
    if (StringUtils.compare(
            fromYaml.getNgEnvironmentInfoConfig().getOrgIdentifier(), environmentRequest.getOrgIdentifier())
        != 0) {
      mismatchedEntries.put("Org Identifier",
          new Pair<>(fromYaml.getNgEnvironmentInfoConfig().getOrgIdentifier(), environmentRequest.getOrgIdentifier()));
    }

    if (StringUtils.compare(
            fromYaml.getNgEnvironmentInfoConfig().getProjectIdentifier(), environmentRequest.getProjectIdentifier())
        != 0) {
      mismatchedEntries.put("Project Identifier ",
          new Pair<>(
              fromYaml.getNgEnvironmentInfoConfig().getProjectIdentifier(), environmentRequest.getProjectIdentifier()));
    }

    if (StringUtils.compare(fromYaml.getNgEnvironmentInfoConfig().getIdentifier(), environmentRequest.getIdentifier())
        != 0) {
      mismatchedEntries.put("Environment Identifier",
          new Pair<>(fromYaml.getNgEnvironmentInfoConfig().getIdentifier(), environmentRequest.getIdentifier()));
    }

    // not using StringUtils.compare() here as we replace environment name with identifier when name field is empty
    if (isNotEmpty(environmentRequest.getName()) && isNotEmpty(fromYaml.getNgEnvironmentInfoConfig().getName())
        && !environmentRequest.getName().equals(fromYaml.getNgEnvironmentInfoConfig().getName())) {
      mismatchedEntries.put("Environment name",
          new Pair<>(fromYaml.getNgEnvironmentInfoConfig().getName(), environmentRequest.getName()));
    }

    if (StringUtils.compare(
            fromYaml.getNgEnvironmentInfoConfig().getType().toString(), environmentRequest.getType().toString())
        != 0) {
      mismatchedEntries.put("Environment type",
          new Pair<>(
              fromYaml.getNgEnvironmentInfoConfig().getType().toString(), environmentRequest.getType().toString()));
    }
    if (isNotEmpty(mismatchedEntries)) {
      throw new InvalidRequestException(String.format(
          "For the environment [name: %s, identifier: %s], Found mismatch in following fields between yaml and requested value respectively: %s",
          environmentRequest.getName(), environmentRequest.getIdentifier(), mismatchedEntries));
    }
  }
}
