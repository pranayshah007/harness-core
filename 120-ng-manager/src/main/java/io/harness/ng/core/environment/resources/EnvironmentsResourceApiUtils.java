/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.join;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import static java.lang.String.format;

import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.spec.server.ng.model.EnvironmentRequest;
import io.harness.spec.server.ng.model.EnvironmentResponse;
import io.harness.spec.server.ng.model.ServiceOverrideRequest;
import io.harness.spec.server.ng.model.ServiceOverrideResponse;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolation;
import javax.validation.Valid;
import javax.validation.Validator;
import org.jetbrains.annotations.NotNull;

@Singleton
public class EnvironmentsResourceApiUtils {
  private final Validator validator;

  @Inject
  public EnvironmentsResourceApiUtils(Validator validator) {
    this.validator = validator;
  }

  public Environment toEnvironmentEntity(
      String accountId, EnvironmentRequest environmentRequest, String org, String project) {
    Environment environment;
    if (isNotEmpty(environmentRequest.getYaml())) {
      NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environmentRequest, org, project);

      validate(ngEnvironmentConfig);
      validateEnvGlobalOverrides(ngEnvironmentConfig);

      environment = Environment.builder()
                        .identifier(environmentRequest.getSlug())
                        .accountId(accountId)
                        .orgIdentifier(org)
                        .projectIdentifier(project)
                        .name(environmentRequest.getName())
                        .color(Optional.ofNullable(environmentRequest.getColor()).orElse(HARNESS_BLUE))
                        .description(environmentRequest.getDescription())
                        .type(EnvironmentType.valueOf(environmentRequest.getType().toString()))
                        .tags(convertToList(environmentRequest.getTags()))
                        .build();

      environment.setYaml(environmentRequest.getYaml());
      if (isEmpty(environment.getYaml())) {
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
      return environment;
    }
    environment = toNGEnvironmentEntity(accountId, environmentRequest, org, project);
    NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    environment.setYaml(toYaml(ngEnvironmentConfig));
    Set<ConstraintViolation<Environment>> violations = validator.validate(environment);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return environment;
  }
  public NGEnvironmentConfig toNGEnvironmentConfig(EnvironmentRequest dto, String org, String project) {
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
                                     .identifier(dto.getSlug())
                                     .orgIdentifier(org)
                                     .projectIdentifier(project)
                                     .description(dto.getDescription())
                                     .tags(dto.getTags())
                                     .type(EnvironmentType.valueOf(dto.getType().toString()))
                                     .build())
        .build();
  }

  public Environment toNGEnvironmentEntity(String accountId, EnvironmentRequest dto, String org, String project) {
    return Environment.builder()
        .identifier(dto.getSlug())
        .accountId(accountId)
        .orgIdentifier(org)
        .projectIdentifier(project)
        .name(dto.getName())
        .color(Optional.ofNullable(dto.getColor()).orElse(HARNESS_BLUE))
        .description(dto.getDescription())
        .type(EnvironmentType.valueOf(dto.getType().toString()))
        .tags(convertToList(dto.getTags()))
        .build();
  }

  private void validate(NGEnvironmentConfig ngEnvironmentConfig) {
    Set<ConstraintViolation<NGEnvironmentConfig>> violations = validator.validate(ngEnvironmentConfig);
    if (isEmpty(violations)) {
      return;
    }
    final List<String> messages = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toList());
    throw new InvalidRequestException(join(",", messages));
  }

  private void validateEnvGlobalOverrides(NGEnvironmentConfig ngEnvironmentConfig) {
    if (ngEnvironmentConfig.getNgEnvironmentInfoConfig() != null
        && ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride() != null) {
      final NGEnvironmentGlobalOverride environmentGlobalOverride =
          ngEnvironmentConfig.getNgEnvironmentInfoConfig().getNgEnvironmentGlobalOverride();
      checkDuplicateManifestIdentifiersWithIn(environmentGlobalOverride.getManifests());
      checkDuplicateConfigFilesIdentifiersWithIn(environmentGlobalOverride.getConfigFiles());
    }
  }

  public EnvironmentResponse toEnvironmentResponseWrapper(Environment environment) {
    EnvironmentResponse environmentResponse = new EnvironmentResponse();
    environmentResponse.setEnvironment(writeDTO(environment));
    environmentResponse.setCreated(environment.getCreatedAt());
    environmentResponse.setUpdated(environment.getLastModifiedAt());
    Set<ConstraintViolation<EnvironmentResponse>> violations = validator.validate(environmentResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return environmentResponse;
  }

  public io.harness.spec.server.ng.model.Environment writeDTO(Environment environment) {
    io.harness.spec.server.ng.model.Environment environmentResponse = new io.harness.spec.server.ng.model.Environment();
    environmentResponse.setAccount(environment.getAccountId());
    environmentResponse.setOrg(environment.getOrgIdentifier());
    environmentResponse.setProject(environment.getProjectIdentifier());
    environmentResponse.setSlug(environment.getIdentifier());
    environmentResponse.setName(environment.getName());
    environmentResponse.setColor(environment.getColor());
    environmentResponse.setDescription(environment.getDescription());
    environmentResponse.setType(
        io.harness.spec.server.ng.model.Environment.TypeEnum.fromValue(environment.getType().toString()));
    environmentResponse.setTags(convertToMap(environment.getTags()));
    environmentResponse.setYaml(environment.getYaml());
    return environmentResponse;
  }
  // ServiceOverrides
  public NGServiceOverridesEntity toServiceOverridesEntity(String accountId,
      ServiceOverrideRequest serviceOverrideRequest, String org, String project, String environment, String service) {
    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .accountId(accountId)
                                                          .orgIdentifier(org)
                                                          .projectIdentifier(project)
                                                          .environmentRef(environment)
                                                          .serviceRef(service)
                                                          .yaml(serviceOverrideRequest.getYaml())
                                                          .build();

    // validating the yaml
    NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig(serviceOverridesEntity);
    Set<ConstraintViolation<NGServiceOverridesEntity>> violations = validator.validate(serviceOverridesEntity);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return serviceOverridesEntity;
  }

  public ServiceOverrideResponse toServiceOverrideResponse(NGServiceOverridesEntity serviceOverridesEntity) {
    ServiceOverrideResponse serviceOverrideResponse = new ServiceOverrideResponse();
    serviceOverrideResponse.setService(serviceOverridesEntity.getServiceRef());
    serviceOverrideResponse.setEnvironemnt(serviceOverridesEntity.getEnvironmentRef());
    serviceOverrideResponse.setOrg(serviceOverridesEntity.getOrgIdentifier());
    serviceOverrideResponse.setProject(serviceOverridesEntity.getProjectIdentifier());
    serviceOverrideResponse.setAccount(serviceOverridesEntity.getAccountId());
    serviceOverrideResponse.setYaml(serviceOverridesEntity.getYaml());
    Set<ConstraintViolation<ServiceOverrideResponse>> violations = validator.validate(serviceOverrideResponse);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return serviceOverrideResponse;
  }

  public void checkDuplicateManifestIdentifiersWithIn(List<ManifestConfigWrapper> manifests) {
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

  public void checkDuplicateConfigFilesIdentifiersWithIn(List<ConfigFileWrapper> configFiles) {
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
  private Set<String> getDuplicateIdentifiers(Stream<String> identifierStream) {
    Set<String> uniqueIds = new HashSet<>();
    Set<String> duplicateIds = new HashSet<>();
    identifierStream.forEach(id -> {
      if (!uniqueIds.add(id)) {
        duplicateIds.add(id);
      }
    });
    return duplicateIds;
  }
  public String toYaml(@Valid NGEnvironmentConfig ngEnvironmentConfig) {
    try {
      return YamlPipelineUtils.getYamlString(ngEnvironmentConfig);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create environment entity due to " + e.getMessage());
    }
  }

  public String mapSort(String sort, String order) {
    String property;
    if (isEmpty(sort) && isEmpty(order)) {
      property = EnvironmentKeys.lastModifiedAt;
      order = "DESC";
      return property + ',' + order;
    } else if (isEmpty(sort)) {
      property = EnvironmentKeys.lastModifiedAt;
      return property + ',' + order;
    } else if (isEmpty(order)) {
      order = "DESC";
    }
    switch (sort) {
      case "slug":
        property = EnvironmentKeys.identifier;
        break;
      case "harness_account":
        property = EnvironmentKeys.accountId;
        break;
      case "org":
        property = EnvironmentKeys.orgIdentifier;
        break;
      case "project":
        property = EnvironmentKeys.projectIdentifier;
        break;
      case "created":
        property = EnvironmentKeys.createdAt;
        break;
      case "updated":
        property = EnvironmentKeys.lastModifiedAt;
        break;
      default:
        property = sort;
    }
    return property + ',' + order;
  }

  public PermissionCheckDTO environmentResponseToPermissionCheckDTO(EnvironmentResponse environmentResponse) {
    return PermissionCheckDTO.builder()
        .permission(CDNGRbacPermissions.ENVIRONMENT_RUNTIME_PERMISSION)
        .resourceIdentifier(environmentResponse.getEnvironment().getSlug())
        .resourceScope(ResourceScope.builder()
                           .accountIdentifier(environmentResponse.getEnvironment().getAccount())
                           .orgIdentifier(environmentResponse.getEnvironment().getOrg())
                           .projectIdentifier(environmentResponse.getEnvironment().getProject())
                           .build())
        .resourceType(NGResourceType.ENVIRONMENT)
        .build();
  }
}
