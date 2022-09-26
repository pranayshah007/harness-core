/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.mappers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.freeze.beans.FreezeResponseDTO;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NGFreezeDtoMapper {
  public FreezeConfigEntity toFreezeConfigEntity(
      String accountId, String orgId, String projectId, String freezeConfigYaml, FreezeType type) {
    try {
      FreezeConfig templateConfig = YamlPipelineUtils.read(freezeConfigYaml, FreezeConfig.class);
      return toFreezeConfigEntityResponse(accountId, templateConfig, freezeConfigYaml, type);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public FreezeConfigEntity toFreezeConfigEntityGlobal(
      String accountId, String orgId, String projectId, String freezeConfigYaml) {
    return toFreezeConfigEntity(accountId, orgId, projectId, freezeConfigYaml, FreezeType.GLOBAL);
  }

  public FreezeConfigEntity toFreezeConfigEntityManual(
      String accountId, String orgId, String projectId, String freezeConfigYaml) {
    return toFreezeConfigEntity(accountId, orgId, projectId, freezeConfigYaml, FreezeType.MANUAL);
  }

  public FreezeConfig toFreezeConfig(String freezeConfigYaml) {
    try {
      return YamlPipelineUtils.read(freezeConfigYaml, FreezeConfig.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create template entity due to " + e.getMessage());
    }
  }

  public FreezeResponseDTO prepareFreezeResponseDto(FreezeConfigEntity freezeConfigEntity) {
    return FreezeResponseDTO.builder()
        .accountId(freezeConfigEntity.getAccountId())
        .orgIdentifier(freezeConfigEntity.getOrgIdentifier())
        .projectIdentifier(freezeConfigEntity.getProjectIdentifier())
        .yaml(freezeConfigEntity.getYaml())
        .identifier(freezeConfigEntity.getIdentifier())
        .description(freezeConfigEntity.getDescription())
        .name(freezeConfigEntity.getName())
        .status(freezeConfigEntity.getStatus())
        .freezeScope(freezeConfigEntity.getFreezeScope())
        .tags(TagMapper.convertToMap(freezeConfigEntity.getTags()))
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .createdAt(freezeConfigEntity.getCreatedAt())
        .type(freezeConfigEntity.getType())
        .createdBy(freezeConfigEntity.getCreatedBy())
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .lastUpdatedBy(freezeConfigEntity.getLastUpdatedBy())
        .build();
  }

  public String toYaml(FreezeConfig freezeConfig) {
    return YamlPipelineUtils.writeYamlString(freezeConfig);
  }

  private FreezeConfigEntity toFreezeConfigEntityResponse(
      String accountId, FreezeConfig freezeConfig, String freezeConfigYaml, FreezeType type) {
    //    validateFreezeYaml(freezeConfig, orgId, projectId);
    String description = (String) freezeConfig.getFreezeInfoConfig().getDescription().fetchFinalValue();
    description = description == null ? "" : description;
    return FreezeConfigEntity.builder()
        .yaml(freezeConfigYaml)
        .identifier(freezeConfig.getFreezeInfoConfig().getIdentifier())
        .accountId(accountId)
        .orgIdentifier(freezeConfig.getFreezeInfoConfig().getOrgIdentifier())
        .projectIdentifier(freezeConfig.getFreezeInfoConfig().getProjectIdentifier())
        .name(freezeConfig.getFreezeInfoConfig().getName())
        .status(freezeConfig.getFreezeInfoConfig().getActive())
        .description(description)
        .tags(TagMapper.convertToList(freezeConfig.getFreezeInfoConfig().getTags()))
        .type(type)
        .freezeScope(getScopeFromTemplateDto(freezeConfig.getFreezeInfoConfig()))
        .build();
  }

  private Scope getScopeFromTemplateDto(FreezeInfoConfig freezeInfoConfig) {
    if (EmptyPredicate.isNotEmpty(freezeInfoConfig.getProjectIdentifier())) {
      return Scope.PROJECT;
    }
    if (EmptyPredicate.isNotEmpty(freezeInfoConfig.getOrgIdentifier())) {
      return Scope.ORG;
    }
    return Scope.ACCOUNT;
  }
}
