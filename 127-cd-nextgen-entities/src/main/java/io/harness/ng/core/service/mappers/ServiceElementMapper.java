/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceBasicInfo;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.ng.core.template.CacheResponseMetadataDTO;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.util.Map;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(PIPELINE)
@UtilityClass
public class ServiceElementMapper {
  public ServiceEntity toServiceEntity(String accountId, ServiceRequestDTO serviceRequestDTO) {
    NGServiceConfig ngServiceConfig = getNgServiceConfig(serviceRequestDTO);

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .identifier(ngServiceConfig.getNgServiceV2InfoConfig().getIdentifier())
                                      .accountId(accountId)
                                      .orgIdentifier(serviceRequestDTO.getOrgIdentifier())
                                      .projectIdentifier(serviceRequestDTO.getProjectIdentifier())
                                      .name(ngServiceConfig.getNgServiceV2InfoConfig().getName())
                                      .description(ngServiceConfig.getNgServiceV2InfoConfig().getDescription())
                                      .tags(convertToList(ngServiceConfig.getNgServiceV2InfoConfig().getTags()))
                                      .yaml(serviceRequestDTO.getYaml())
                                      .build();

    final NGServiceV2InfoConfig ngServiceV2InfoConfig = ngServiceConfig.getNgServiceV2InfoConfig();
    if (isEmpty(serviceEntity.getYaml())) {
      serviceEntity.setYaml(NGServiceEntityMapper.toYaml(ngServiceConfig));
    }
    serviceEntity.setGitOpsEnabled(ngServiceV2InfoConfig.getGitOpsEnabled());
    if (ngServiceV2InfoConfig.getServiceDefinition() != null) {
      serviceEntity.setType(ngServiceV2InfoConfig.getServiceDefinition().getType());
    }

    return serviceEntity;
  }

  private NGServiceConfig getNgServiceConfig(ServiceRequestDTO serviceRequestDTO) {
    NGServiceConfig ngServiceConfig;
    if (isNotEmpty(serviceRequestDTO.getYaml())) {
      try {
        ngServiceConfig = YamlPipelineUtils.read(serviceRequestDTO.getYaml(), NGServiceConfig.class);
        validateFieldsOrThrow(ngServiceConfig.getNgServiceV2InfoConfig(), serviceRequestDTO);

      } catch (IOException e) {
        throw new InvalidRequestException(
            String.format("Cannot create service ng with Identifier : %s service config due to " + e.getMessage(),
                serviceRequestDTO.getIdentifier()));
      }
    } else {
      ngServiceConfig = NGServiceConfig.builder()
                            .ngServiceV2InfoConfig(NGServiceV2InfoConfig.builder()
                                                       .name(serviceRequestDTO.getName())
                                                       .identifier(serviceRequestDTO.getIdentifier())
                                                       .description(serviceRequestDTO.getDescription())
                                                       .tags(serviceRequestDTO.getTags())
                                                       .build())
                            .build();
    }
    String name = isEmpty(serviceRequestDTO.getName()) ? ngServiceConfig.getNgServiceV2InfoConfig().getName()
                                                       : serviceRequestDTO.getName();

    String description = isEmpty(serviceRequestDTO.getDescription())
        ? ngServiceConfig.getNgServiceV2InfoConfig().getDescription()
        : serviceRequestDTO.getDescription();

    Map<String, String> tags = isEmpty(serviceRequestDTO.getTags())
        ? ngServiceConfig.getNgServiceV2InfoConfig().getTags()
        : serviceRequestDTO.getTags();

    ngServiceConfig.getNgServiceV2InfoConfig().setName(name);
    ngServiceConfig.getNgServiceV2InfoConfig().setDescription(description);
    ngServiceConfig.getNgServiceV2InfoConfig().setTags(tags);

    return ngServiceConfig;
  }

  private void validateFieldsOrThrow(NGServiceV2InfoConfig fromYaml, ServiceRequestDTO serviceRequestDTO) {
    if (StringUtils.compare(serviceRequestDTO.getIdentifier(), fromYaml.getIdentifier()) != 0) {
      throw new InvalidRequestException(
          String.format("Service Identifier : %s in service request doesn't match with identifier : %s given in yaml",
              serviceRequestDTO.getIdentifier(), fromYaml.getIdentifier()));
    }

    // not using StringUtils.compare() here as we replace service name with identifier when name field is empty
    if (isNotEmpty(serviceRequestDTO.getName()) && isNotEmpty(fromYaml.getName())
        && !serviceRequestDTO.getName().equals(fromYaml.getName())) {
      throw new InvalidRequestException(
          String.format("Service Name : %s in service request doesn't match with name : %s given in yaml",
              serviceRequestDTO.getName(), fromYaml.getName()));
    }

    ServiceDefinition serviceDefinition = fromYaml.getServiceDefinition();
    if (serviceDefinition != null) {
      NGServiceEntityMapper.validateManifests(serviceDefinition);
    }
  }

  public ServiceResponseDTO writeDTO(ServiceEntity serviceEntity) {
    return writeDTO(serviceEntity, false);
  }

  public ServiceResponseDTO writeDTO(ServiceEntity serviceEntity, boolean includeVersionInfo) {
    ServiceResponseDTO serviceResponseDTO = ServiceResponseDTO.builder()
                                                .accountId(serviceEntity.getAccountId())
                                                .orgIdentifier(serviceEntity.getOrgIdentifier())
                                                .projectIdentifier(serviceEntity.getProjectIdentifier())
                                                .identifier(serviceEntity.getIdentifier())
                                                .name(serviceEntity.getName())
                                                .description(serviceEntity.getDescription())
                                                .deleted(serviceEntity.getDeleted())
                                                .tags(convertToMap(serviceEntity.getTags()))
                                                .version(serviceEntity.getVersion())
                                                .yaml(serviceEntity.getYaml())
                                                .entityGitDetails(getEntityGitDetails(serviceEntity))
                                                .storeType(serviceEntity.getStoreType())
                                                .fallbackBranch(serviceEntity.getFallBackBranch())
                                                .connectorRef(serviceEntity.getConnectorRef())
                                                .cacheResponseMetadataDTO(getCacheResponse(serviceEntity))
                                                .build();

    if (includeVersionInfo && serviceEntity.getType() != null) {
      serviceResponseDTO.setV2Service(true);
    }
    return serviceResponseDTO;
  }

  public EntityGitDetails getEntityGitDetails(ServiceEntity serviceEntity) {
    if (serviceEntity.getStoreType() == StoreType.REMOTE) {
      EntityGitDetails entityGitDetails = GitAwareContextHelper.getEntityGitDetails(serviceEntity);

      // add additional details from scm metadata
      return GitAwareContextHelper.updateEntityGitDetailsFromScmGitMetadata(entityGitDetails);
    }
    return null; // Default if storeType is not remote
  }

  private CacheResponseMetadataDTO getCacheResponse(ServiceEntity serviceEntity) {
    if (serviceEntity.getStoreType() == StoreType.REMOTE) {
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

  public String getServiceNotFoundError(String orgIdentifier, String projectIdentifier, @NonNull String serviceId) {
    if (isNotEmpty(projectIdentifier)) {
      return format("Service with identifier [%s] in project [%s], org [%s] not found", serviceId, projectIdentifier,
          orgIdentifier);
    } else if (isNotEmpty(orgIdentifier)) {
      return format("Service with identifier [%s] in org [%s] not found", serviceId, orgIdentifier);
    }
    return format("Service with identifier [%s] in account not found", serviceId);
  }

  public ServiceResponseDTO writeAccessListDTO(ServiceEntity serviceEntity) {
    return ServiceResponseDTO.builder()
        .accountId(serviceEntity.getAccountId())
        .orgIdentifier(serviceEntity.getOrgIdentifier())
        .projectIdentifier(serviceEntity.getProjectIdentifier())
        .identifier(serviceEntity.getIdentifier())
        .name(serviceEntity.getName())
        .description(serviceEntity.getDescription())
        .deleted(serviceEntity.getDeleted())
        .tags(convertToMap(serviceEntity.getTags()))
        .version(serviceEntity.getVersion())
        .storeType(serviceEntity.getStoreType())
        .fallbackBranch(serviceEntity.getFallBackBranch())
        .connectorRef(serviceEntity.getConnectorRef())
        .entityGitDetails(getEntityGitDetails(serviceEntity))
        .build();
  }

  public ServiceResponse toResponseWrapper(ServiceEntity serviceEntity) {
    return ServiceResponse.builder()
        .service(writeDTO(serviceEntity))
        .createdAt(serviceEntity.getCreatedAt())
        .lastModifiedAt(serviceEntity.getLastModifiedAt())
        .build();
  }

  public ServiceResponse toResponseWrapper(ServiceEntity serviceEntity, boolean includeVersionInfo) {
    return ServiceResponse.builder()
        .service(writeDTO(serviceEntity, includeVersionInfo))
        .createdAt(serviceEntity.getCreatedAt())
        .lastModifiedAt(serviceEntity.getLastModifiedAt())
        .build();
  }

  public ServiceResponse toAccessListResponseWrapper(ServiceEntity serviceEntity) {
    return ServiceResponse.builder()
        .service(writeAccessListDTO(serviceEntity))
        .createdAt(serviceEntity.getCreatedAt())
        .lastModifiedAt(serviceEntity.getLastModifiedAt())
        .build();
  }

  public ServiceBasicInfo toBasicInfo(ServiceEntity serviceEntity) {
    return ServiceBasicInfo.builder()
        .identifier(serviceEntity.getIdentifier())
        .name(serviceEntity.getName())
        .description(serviceEntity.getDescription())
        .accountIdentifier(serviceEntity.getAccountId())
        .orgIdentifier(serviceEntity.getOrgIdentifier())
        .projectIdentifier(serviceEntity.getProjectIdentifier())
        .tags(convertToMap(serviceEntity.getTags()))
        .build();
  }
}
