/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceBasicInfo;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.yaml.BasicService;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.yaml.YamlUtils;
import lombok.experimental.UtilityClass;

import java.io.IOException;

@OwnedBy(PIPELINE)
@UtilityClass
public class ServiceElementMapper {
  public ServiceEntity toServiceEntity(String accountId, ServiceRequestDTO serviceRequestDTO) {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .identifier(serviceRequestDTO.getIdentifier())
                                      .accountId(accountId)
                                      .orgIdentifier(serviceRequestDTO.getOrgIdentifier())
                                      .projectIdentifier(serviceRequestDTO.getProjectIdentifier())
                                      .name(serviceRequestDTO.getName())
                                      .description(serviceRequestDTO.getDescription())
                                      .tags(convertToList(serviceRequestDTO.getTags()))
                                      .yaml(serviceRequestDTO.getYaml())
                                      .build();
    // This also validates the service yaml
    final NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(serviceEntity);
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

  public ServiceEntity toServiceEntity(String accountId, String orgId, String projectId, String serviceYaml) {
    try {
      BasicService basicService = YamlUtils.read(serviceYaml, BasicService.class);
      if (NGExpressionUtils.matchesInputSetPattern(basicService.getIdentifier())) {
        throw new InvalidRequestException("Service identifier cannot be runtime input");
      }
      return ServiceEntity.builder()
          .yaml(serviceYaml)
          .accountId(accountId)
          .orgIdentifier(orgId)
          .projectIdentifier(projectId)
          .name(basicService.getName())
          .identifier(basicService.getIdentifier())
          .description(basicService.getDescription())
          .tags(TagMapper.convertToList(basicService.getTags()))
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create service entity due to " + e.getMessage());
    }
  }

  public ServiceResponseDTO writeDTO(ServiceEntity serviceEntity) {
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
        .yaml(serviceEntity.getYaml())
        .build();
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
        .build();
  }

  public ServiceResponse toResponseWrapper(ServiceEntity serviceEntity) {
    return ServiceResponse.builder()
        .service(writeDTO(serviceEntity))
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
