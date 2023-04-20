/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.deploymentmetadata;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.ng.core.k8s.ServiceSpecType.GOOGLE_CLOUD_FUNCTIONS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStrategyType;
import io.harness.cdng.service.beans.GoogleCloudFunctionsServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class DeploymentMetadataServiceHelper {
  private static final String GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE = "GenTwo";
  private static final String GOOGLE_FUNCTION_GEN_ONE_ENV_TYPE = "GenOne";
  private static final String GOOGLE_FUNCTION_GEN_STRATEGY_SUFFIX = "genone";
  public Optional<DeploymentMetadataDto> parseDeploymentMetadataYaml(
      ServiceDefinitionType type, String deploymentMetadataYaml) {
    if (EmptyPredicate.isEmpty(deploymentMetadataYaml)) {
      return Optional.empty();
    }
    switch (type.getYamlName()) {
      case GOOGLE_CLOUD_FUNCTIONS:
        GoogleCloudFunctionDeploymentMetadataDto googleCloudFunctionDeploymentMetaDataDto;
        try {
          googleCloudFunctionDeploymentMetaDataDto =
              YamlPipelineUtils.read(deploymentMetadataYaml, GoogleCloudFunctionDeploymentMetadataDto.class);
        } catch (IOException e) {
          throw new InvalidRequestException(
              "Cannot create googleCloudFunctionDeploymentMetaDataDto due to " + e.getMessage());
        }
        return Optional.of(googleCloudFunctionDeploymentMetaDataDto);
      default:
        return Optional.empty();
    }
  }

  public List<ServiceEntity> filterOnDeploymentMetadata(
      List<ServiceEntity> serviceEntities, ServiceDefinitionType type, String deploymentMetadataYaml) {
    if (serviceEntities.isEmpty()) {
      return serviceEntities;
    }
    Optional<DeploymentMetadataDto> deploymentMetadataDtoOptional =
        parseDeploymentMetadataYaml(type, deploymentMetadataYaml);
    switch (type.getYamlName()) {
      case GOOGLE_CLOUD_FUNCTIONS:
        GoogleCloudFunctionDeploymentMetadataDto googleCloudFunctionDeploymentMetaDataDto;
        if (deploymentMetadataDtoOptional.isEmpty()) {
          // if metadata is empty, we should treat it as google function gen two metadata
          googleCloudFunctionDeploymentMetaDataDto = GoogleCloudFunctionDeploymentMetadataDto.builder()
                                                         .environmentType(GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE)
                                                         .build();
        } else {
          googleCloudFunctionDeploymentMetaDataDto =
              (GoogleCloudFunctionDeploymentMetadataDto) deploymentMetadataDtoOptional.get();
        }
        return serviceEntities.stream()
            .filter(serviceEntity
                -> isGoogleFunctionServiceSyncedWithDeploymentMetadata(
                    googleCloudFunctionDeploymentMetaDataDto, serviceEntity))
            .collect(Collectors.toList());
      default:
        return serviceEntities;
    }
  }

  public String filterStrategyTypeOnDeploymentMetadata(
      ServiceDefinitionType type, String deploymentMetadataYaml, ExecutionStrategyType executionStrategyType) {
    Optional<DeploymentMetadataDto> deploymentMetadataDtoOptional =
        parseDeploymentMetadataYaml(type, deploymentMetadataYaml);
    switch (type.getYamlName()) {
      case GOOGLE_CLOUD_FUNCTIONS:
        if (deploymentMetadataDtoOptional.isEmpty()) {
          return executionStrategyType.getDisplayName().toLowerCase();
        }
        GoogleCloudFunctionDeploymentMetadataDto googleCloudFunctionDeploymentMetaDataDto =
            (GoogleCloudFunctionDeploymentMetadataDto) deploymentMetadataDtoOptional.get();
        if (GOOGLE_FUNCTION_GEN_ONE_ENV_TYPE.equals(googleCloudFunctionDeploymentMetaDataDto.getEnvironmentType())) {
          return executionStrategyType.getDisplayName().toLowerCase() + GOOGLE_FUNCTION_GEN_STRATEGY_SUFFIX;
        }
        return executionStrategyType.getDisplayName().toLowerCase();
      default:
        return executionStrategyType.getDisplayName().toLowerCase();
    }
  }

  private boolean isGoogleFunctionServiceSyncedWithDeploymentMetadata(
      GoogleCloudFunctionDeploymentMetadataDto googleCloudFunctionDeploymentMetaDataDto, ServiceEntity serviceEntity) {
    NGServiceConfig config;
    if (EmptyPredicate.isEmpty(serviceEntity.getYaml())) {
      return false;
    }
    try {
      config = YamlPipelineUtils.read(serviceEntity.getYaml(), NGServiceConfig.class);
    } catch (IOException e) {
      log.debug("Cannot create service config due to " + e.getMessage());
      return false;
    }
    GoogleCloudFunctionsServiceSpec googleCloudFunctionsServiceSpec =
        (GoogleCloudFunctionsServiceSpec) config.getNgServiceV2InfoConfig().getServiceDefinition().getServiceSpec();
    String serviceEnvironmentType = googleCloudFunctionsServiceSpec.getEnvironmentType().getValue();
    // if environment type in service is empty, we should treat it as gen two environment type
    if (EmptyPredicate.isEmpty(serviceEnvironmentType)) {
      serviceEnvironmentType = GOOGLE_FUNCTION_GEN_TWO_ENV_TYPE;
    }
    return serviceEnvironmentType.equals(googleCloudFunctionDeploymentMetaDataDto.getEnvironmentType());
  }
}
