/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.infra.yaml.InfrastructureConfig;
import io.harness.cdng.infra.yaml.InfrastructureDefinitionConfig;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponse;
import io.harness.ng.core.infrastructure.dto.InfrastructureResponseDTO;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.template.CacheResponseMetadataDTO;

import lombok.experimental.UtilityClass;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDC)
@UtilityClass
public class InfrastructureMapper {
  public InfrastructureEntity toInfrastructureEntity(
      String accountId, InfrastructureRequestDTO infrastructureRequestDTO) {
    final InfrastructureConfig infrastructureConfig =
        InfrastructureEntityConfigMapper.toInfrastructureConfig(infrastructureRequestDTO.getYaml());
    setMissingFieldsFromRequestDTO(infrastructureConfig, infrastructureRequestDTO);
    InfrastructureEntityConfigMapper.checkForFieldMismatch(infrastructureConfig, infrastructureRequestDTO);
    return getInfrastructureEntity(
        accountId, infrastructureRequestDTO.getYaml(), infrastructureConfig.getInfrastructureDefinitionConfig());
  }

  private static InfrastructureEntity getInfrastructureEntity(
      String accountId, String yaml, InfrastructureDefinitionConfig infrastructureDefinitionConfig) {
    return InfrastructureEntity.builder()
        .identifier(infrastructureDefinitionConfig.getIdentifier())
        .accountId(accountId)
        .orgIdentifier(infrastructureDefinitionConfig.getOrgIdentifier())
        .projectIdentifier(infrastructureDefinitionConfig.getProjectIdentifier())
        .envIdentifier(infrastructureDefinitionConfig.getEnvironmentRef())
        .name(infrastructureDefinitionConfig.getName())
        .description(infrastructureDefinitionConfig.getDescription())
        .tags(convertToList(infrastructureDefinitionConfig.getTags()))
        .yaml(yaml)
        .type(infrastructureDefinitionConfig.getType())
        .deploymentType(infrastructureDefinitionConfig.getDeploymentType())
        .build();
  }

  private static void setMissingFieldsFromRequestDTO(
      InfrastructureConfig fromYaml, InfrastructureRequestDTO requestDTO) {
    InfrastructureDefinitionConfig configFromYaml = fromYaml.getInfrastructureDefinitionConfig();
    if (isEmpty(configFromYaml.getOrgIdentifier()) && isNotEmpty(requestDTO.getOrgIdentifier())) {
      configFromYaml.setOrgIdentifier(requestDTO.getOrgIdentifier());
    }

    if (isEmpty(configFromYaml.getProjectIdentifier()) && isNotEmpty(requestDTO.getProjectIdentifier())) {
      configFromYaml.setProjectIdentifier(requestDTO.getProjectIdentifier());
    }
    if (isEmpty(configFromYaml.getName()) && isNotEmpty(requestDTO.getName())) {
      configFromYaml.setName(requestDTO.getName());
    }
    if (isEmpty(configFromYaml.getIdentifier()) && isNotEmpty(requestDTO.getIdentifier())) {
      configFromYaml.setIdentifier(requestDTO.getIdentifier());
    }
    if (isEmpty(configFromYaml.getEnvironmentRef()) && isNotEmpty(requestDTO.getEnvironmentRef())) {
      configFromYaml.setEnvironmentRef(requestDTO.getEnvironmentRef());
    }

    if (isEmpty(configFromYaml.getDescription()) && isNotEmpty(requestDTO.getDescription())) {
      configFromYaml.setDescription(requestDTO.getDescription());
    }

    if (isEmpty(configFromYaml.getTags()) && isNotEmpty(requestDTO.getTags())) {
      configFromYaml.setTags(requestDTO.getTags());
    }
  }

  public InfrastructureResponse toResponseWrapper(InfrastructureEntity infrastructureEntity) {
    return InfrastructureResponse.builder()
        .infrastructure(writeDTO(infrastructureEntity))
        .createdAt(infrastructureEntity.getCreatedAt())
        .lastModifiedAt(infrastructureEntity.getLastModifiedAt())
        .build();
  }

  public InfrastructureResponseDTO writeDTO(InfrastructureEntity infrastructureEntity) {
    return InfrastructureResponseDTO.builder()
        .accountId(infrastructureEntity.getAccountId())
        .orgIdentifier(infrastructureEntity.getOrgIdentifier())
        .projectIdentifier(infrastructureEntity.getProjectIdentifier())
        .environmentRef(infrastructureEntity.getEnvIdentifier())
        .identifier(infrastructureEntity.getIdentifier())
        .name(infrastructureEntity.getName())
        .description(infrastructureEntity.getDescription())
        .tags(convertToMap(infrastructureEntity.getTags()))
        .yaml(infrastructureEntity.getYaml())
        .type(infrastructureEntity.getType())
        .deploymentType(infrastructureEntity.getDeploymentType())
        .entityGitDetails(getEntityGitDetails(infrastructureEntity))
        .storeType(infrastructureEntity.getStoreType())
        .connectorRef(infrastructureEntity.getConnectorRef())
        .cacheResponseMetadataDTO(getCacheResponse(infrastructureEntity))
        .build();
  }

  private EntityGitDetails getEntityGitDetails(InfrastructureEntity infrastructureEntity) {
    if (infrastructureEntity.getStoreType() == StoreType.REMOTE) {
      EntityGitDetails entityGitDetails = GitAwareContextHelper.getEntityGitDetails(infrastructureEntity);

      // add additional details from scm metadata
      return GitAwareContextHelper.updateEntityGitDetailsFromScmGitMetadata(entityGitDetails);
    }
    return null; // Default if storeType is not remote
  }

  private CacheResponseMetadataDTO getCacheResponse(InfrastructureEntity infrastructureEntity) {
    if (infrastructureEntity.getStoreType() == StoreType.REMOTE) {
      CacheResponse cacheResponse = GitAwareContextHelper.getCacheResponseFromScmGitMetadata();

      if (cacheResponse != null) {
        return createCacheResponseMetadataDTO(cacheResponse);
      }
    }

    return null;
  }

  private CacheResponseMetadataDTO createCacheResponseMetadataDTO(CacheResponse cacheResponse) {
    return CacheResponseMetadataDTO.builder()
        .cacheState(cacheResponse.getCacheState())
        .ttlLeft(cacheResponse.getTtlLeft())
        .lastUpdatedAt(cacheResponse.getLastUpdatedAt())
        .build();
  }
}
