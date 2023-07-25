/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.envGroup.mappers;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.envGroup.beans.EnvironmentGroupConfig;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupWrapperConfig;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupDeleteResponse;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupRequestDTO;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponse;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponseDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.scope.ScopeHelper;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class EnvironmentGroupMapper {
  public EnvironmentGroupResponseDTO writeDTO(
      EnvironmentGroupEntity envGroup, List<EnvironmentResponse> envResponseList) {
    return EnvironmentGroupResponseDTO.builder()
        .accountId(envGroup.getAccountId())
        .orgIdentifier(envGroup.getOrgIdentifier())
        .projectIdentifier(envGroup.getProjectIdentifier())
        .identifier(envGroup.getIdentifier())
        .name(envGroup.getName())
        .color(Optional.ofNullable(envGroup.getColor()).orElse(HARNESS_BLUE))
        .description(envGroup.getDescription())
        .deleted(envGroup.getDeleted())
        .tags(convertToMap(envGroup.getTags()))
        .version(envGroup.getVersion())
        .envIdentifiers(envGroup.getEnvIdentifiers())
        .gitDetails(EntityGitDetailsMapper.mapEntityGitDetails(envGroup))
        .envResponse(CollectionUtils.emptyIfNull(envResponseList))
        .yaml(envGroup.getYaml())
        .build();
  }

  public EnvironmentGroupResponse toResponseWrapper(
      EnvironmentGroupEntity envGroup, List<EnvironmentResponse> envResponseList) {
    return EnvironmentGroupResponse.builder()
        .environment(writeDTO(envGroup, envResponseList))
        .createdAt(envGroup.getCreatedAt())
        .lastModifiedAt(envGroup.getLastModifiedAt())
        .build();
  }

  public EnvironmentGroupDeleteResponse toDeleteResponseWrapper(EnvironmentGroupEntity envGroup) {
    return EnvironmentGroupDeleteResponse.builder()
        .accountId(envGroup.getAccountId())
        .orgIdentifier(envGroup.getOrgIdentifier())
        .projectIdentifier(envGroup.getProjectIdentifier())
        .identifier(envGroup.getIdentifier())
        .deleted(envGroup.getDeleted())
        .build();
  }

  public EnvironmentGroupEntity toEnvironmentGroupEntity(
      String accId, EnvironmentGroupRequestDTO environmentGroupRequestDTO) {
    if (isNotEmpty(environmentGroupRequestDTO.getYaml())) {
      try {
        EnvironmentGroupConfig environmentGroupConfig =
            YamlUtils.read(environmentGroupRequestDTO.getYaml(), EnvironmentGroupWrapperConfig.class)
                .getEnvironmentGroupConfig();
        validateYamlOfEnvGroup(environmentGroupConfig, environmentGroupRequestDTO);
        return EnvironmentGroupEntity.builder()
            .accountId(accId)
            .projectIdentifier(environmentGroupRequestDTO.getProjectIdentifier())
            .orgIdentifier(environmentGroupRequestDTO.getOrgIdentifier())
            .identifier(environmentGroupRequestDTO.getIdentifier().trim())
            .name(environmentGroupConfig.getName().trim())
            .color(environmentGroupRequestDTO.getColor())
            .description(environmentGroupConfig.getDescription())
            .tags(convertToList(environmentGroupConfig.getTags()))
            .yaml(environmentGroupRequestDTO.getYaml())
            .envIdentifiers(CollectionUtils.emptyIfNull(environmentGroupConfig.getEnvIdentifiers()))
            .build();
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create environment config due to " + e.getMessage());
      }
    }

    return null;
  }

  public void validateYamlOfEnvGroup(EnvironmentGroupConfig config, EnvironmentGroupRequestDTO dto) {
    if (StringUtils.compare(config.getOrgIdentifier(), dto.getOrgIdentifier()) != 0) {
      throw new InvalidRequestException(
          String.format("Org Identifier %s passed in yaml is not same as passed in query params %s",
              config.getOrgIdentifier(), dto.getOrgIdentifier()));
    }
    if (StringUtils.compare(config.getProjectIdentifier(), dto.getProjectIdentifier()) != 0) {
      throw new InvalidRequestException(
          String.format("Project Identifier %s passed in yaml is not same as passed in query params %s",
              config.getProjectIdentifier(), dto.getProjectIdentifier()));
    }
    if (StringUtils.compare(config.getIdentifier(), dto.getIdentifier()) != 0) {
      throw new InvalidRequestException(
          String.format("Environment Group Identifier %s passed in yaml is not same as passed in query params %s",
              config.getIdentifier(), dto.getIdentifier()));
    }
  }

  public EntityDetail getEntityDetail(EnvironmentGroupEntity entity) {
    return EntityDetail.builder()
        .name(entity.getName())
        .type(EntityType.ENVIRONMENT_GROUP)
        .entityRef(IdentifierRef.builder()
                       .accountIdentifier(entity.getAccountIdentifier())
                       .orgIdentifier(entity.getOrgIdentifier())
                       .projectIdentifier(entity.getProjectIdentifier())
                       .scope(ScopeHelper.getScope(
                           entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier()))
                       .identifier(entity.getIdentifier())
                       .build())
        .build();
  }
}
