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
import static io.harness.data.structure.HarnessStringUtils.join;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@OwnedBy(PIPELINE)
@UtilityClass
public class EnvironmentMapper {
  ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
  Validator validator = factory.getValidator();

  public Environment toEnvironmentEntity(String accountId, EnvironmentRequestDTO environmentRequestDTO,
      boolean ngSvcManifestOverrideEnabled, boolean ngSvcConfigFilesOverrideEnabled) {
    final Environment environment;
    if (isNotEmpty(environmentRequestDTO.getYaml())) {
      NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environmentRequestDTO);

      validate(ngEnvironmentConfig);
      validateEnvGlobalOverrides(ngEnvironmentConfig, ngSvcManifestOverrideEnabled, ngSvcConfigFilesOverrideEnabled);

      environment = toNGEnvironmentEntity(accountId, ngEnvironmentConfig, environmentRequestDTO.getColor());
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

  private void validate(NGEnvironmentConfig ngEnvironmentConfig) {
    Set<ConstraintViolation<NGEnvironmentConfig>> violations = validator.validate(ngEnvironmentConfig);
    if (isEmpty(violations)) {
      return;
    }
    final List<String> messages = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toList());
    throw new InvalidRequestException(join(",", messages));
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
    return envList.stream().map(env -> toResponseWrapper(env)).collect(Collectors.toList());
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

  private void validateEnvGlobalOverrides(NGEnvironmentConfig ngEnvironmentConfig, boolean ngSvcManifestOverrideEnabled,
      boolean ngSvcConfigFileOverrideEnabled) {
    if (ngEnvironmentConfig.getNgEnvironmentInfoConfig() != null
        && ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() != null) {
      if (!ngSvcManifestOverrideEnabled
          && isNotEmpty(
              ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getManifests())) {
        throw new InvalidRequestException(
            "Manifest Override is not supported with FF NG_SERVICE_MANIFEST_OVERRIDE disabled");
      }
      if (!ngSvcConfigFileOverrideEnabled
          && isNotEmpty(
              ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride().getConfigFiles())) {
        throw new InvalidRequestException(
            "Config Files Override is not supported with FF disabled NG_SERVICE_CONFIG_FILES_OVERRIDE");
      }

      final NGEnvironmentGlobalOverride environmentGlobalOverride =
          ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride();
      checkDuplicateManifestIdentifiersWithIn(environmentGlobalOverride.getManifests());
      checkDuplicateConfigFilesIdentifiersWithIn(environmentGlobalOverride.getConfigFiles());
    }
  }

  public static void checkDuplicateManifestIdentifiersWithIn(List<ManifestConfigWrapper> manifests) {
    if (isEmpty(manifests)) {
      return;
    }
    final Stream<String> identifierStream =
        manifests.stream().map(ManifestConfigWrapper::getManifest).map(ManifestConfig::getIdentifier);
    Set<String> duplicateIds = getDuplicateIdentifiers(identifierStream);
    if (isNotEmpty(duplicateIds)) {
      throw new InvalidRequestException(format("Found duplicate manifest identifiers [%s]",
          duplicateIds.stream().map(Object::toString).collect(Collectors.joining(","))));
    }
  }

  public static void checkDuplicateConfigFilesIdentifiersWithIn(List<ConfigFileWrapper> configFiles) {
    if (isEmpty(configFiles)) {
      return;
    }
    final Stream<String> identifierStream =
        configFiles.stream().map(ConfigFileWrapper::getConfigFile).map(ConfigFile::getIdentifier);
    Set<String> duplicateIds = getDuplicateIdentifiers(identifierStream);
    if (isNotEmpty(duplicateIds)) {
      throw new InvalidRequestException(format("Found duplicate configFiles identifiers [%s]",
          duplicateIds.stream().map(Object::toString).collect(Collectors.joining(","))));
    }
  }

  @NotNull
  private static Set<String> getDuplicateIdentifiers(Stream<String> identifierStream) {
    Set<String> uniqueIds = new HashSet<>();
    Set<String> duplicateIds = new HashSet<>();
    identifierStream.forEach(id -> {
      if (!uniqueIds.add(id)) {
        duplicateIds.add(id);
      }
    });
    return duplicateIds;
  }
}
