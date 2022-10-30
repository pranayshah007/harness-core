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

import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.ResourceScope;
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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.dropwizard.jersey.validation.JerseyViolationException;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;

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

      environment = toNGEnvironmentEntity(accountId, environmentRequest, org, project);
      environment.setYaml(environmentRequest.getYaml());
      if (isEmpty(environment.getYaml())) {
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
      return environment;
    }
    environment = toNGEnvironmentEntity(accountId, environmentRequest, org, project);
    NGEnvironmentConfig ngEnvironmentConfig = EnvironmentMapper.toNGEnvironmentConfig(environment);
    environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
    Set<ConstraintViolation<Environment>> violations = validator.validate(environment);
    if (!violations.isEmpty()) {
      throw new JerseyViolationException(violations, null);
    }
    return environment;
  }
  public static NGEnvironmentConfig toNGEnvironmentConfig(EnvironmentRequest dto, String org, String project) {
    if (isNotEmpty(dto.getYaml())) {
      try {
        return YamlUtils.read(dto.getYaml(), NGEnvironmentConfig.class);
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create environment config due to " + e.getMessage());
      }
    }
    return toNGEnvironmentConfigWrapper(dto, org, project);
  }

  public static NGEnvironmentConfig toNGEnvironmentConfigWrapper(EnvironmentRequest dto, String org, String project) {
    NGEnvironmentConfig ngEnvironmentConfig =
        NGEnvironmentConfig.builder()
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
    return ngEnvironmentConfig;
  }

  public static Environment toNGEnvironmentEntity(
      String accountId, EnvironmentRequest dto, String org, String project) {
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
      EnvironmentMapper.checkDuplicateManifestIdentifiersWithIn(environmentGlobalOverride.getManifests());
      EnvironmentMapper.checkDuplicateConfigFilesIdentifiersWithIn(environmentGlobalOverride.getConfigFiles());
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
