/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.FilterType;
import io.harness.freeze.beans.FreezeEntityRule;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.FreezeStatus;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.response.FreezeBannerDetails;
import io.harness.freeze.beans.response.FreezeDetailedResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseDTO;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.text.ParseException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@UtilityClass
public class NGFreezeDtoMapper {
  private static final long MIN_FREEZE_WINDOW_TIME = 1800000L;
  private static final long MAX_FREEZE_WINDOW_TIME = 31536000000L;
  DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
  public FreezeConfigEntity toFreezeConfigEntity(
      String accountId, String orgId, String projectId, String freezeConfigYaml, FreezeType type) {
    FreezeConfig freezeConfig = toFreezeConfig(freezeConfigYaml);
    return toFreezeConfigEntityResponse(accountId, freezeConfig, freezeConfigYaml, type, orgId, projectId);
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

  public static FreezeConfigEntity updateOldFreezeConfig(
      FreezeConfigEntity newFreezeConfigEntity, FreezeConfigEntity oldFreezeConfigEntity) {
    oldFreezeConfigEntity.setOrgIdentifier(newFreezeConfigEntity.getOrgIdentifier());
    oldFreezeConfigEntity.setProjectIdentifier(newFreezeConfigEntity.getProjectIdentifier());
    oldFreezeConfigEntity.setIdentifier(newFreezeConfigEntity.getIdentifier());
    oldFreezeConfigEntity.setFreezeScope(newFreezeConfigEntity.getFreezeScope());
    oldFreezeConfigEntity.setDescription(getDescription(newFreezeConfigEntity.getDescription()));
    oldFreezeConfigEntity.setName(newFreezeConfigEntity.getName());
    oldFreezeConfigEntity.setStatus(newFreezeConfigEntity.getStatus());
    oldFreezeConfigEntity.setTags(newFreezeConfigEntity.getTags());
    oldFreezeConfigEntity.setYaml(newFreezeConfigEntity.getYaml());
    oldFreezeConfigEntity.setShouldSendNotification(true);
    return oldFreezeConfigEntity;
  }

  public FreezeResponseDTO prepareFreezeResponseDto(FreezeConfigEntity freezeConfigEntity) {
    return FreezeResponseDTO.builder()
        .accountId(freezeConfigEntity.getAccountId())
        .orgIdentifier(freezeConfigEntity.getOrgIdentifier())
        .projectIdentifier(freezeConfigEntity.getProjectIdentifier())
        .yaml(freezeConfigEntity.getYaml())
        .identifier(freezeConfigEntity.getIdentifier())
        .description(getDescription(freezeConfigEntity.getDescription()))
        .name(freezeConfigEntity.getName())
        .status(freezeConfigEntity.getStatus())
        .freezeScope(freezeConfigEntity.getFreezeScope())
        .tags(TagMapper.convertToMap(freezeConfigEntity.getTags()))
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .createdAt(freezeConfigEntity.getCreatedAt())
        .type(freezeConfigEntity.getType())
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .build();
  }

  public List<FreezeSummaryResponseDTO> prepareFreezeResponseSummaryDto(
      List<FreezeConfigEntity> freezeConfigEntityList) {
    return freezeConfigEntityList.stream()
        .map(NGFreezeDtoMapper::prepareFreezeResponseSummaryDto)
        .collect(Collectors.toList());
  }

  public FreezeSummaryResponseDTO prepareFreezeResponseSummaryDto(FreezeConfigEntity freezeConfigEntity) {
    FreezeConfig freezeConfig = toFreezeConfig(freezeConfigEntity.getYaml());
    return FreezeSummaryResponseDTO.builder()
        .accountId(freezeConfigEntity.getAccountId())
        .orgIdentifier(freezeConfigEntity.getOrgIdentifier())
        .projectIdentifier(freezeConfigEntity.getProjectIdentifier())
        .windows(freezeConfig.getFreezeInfoConfig().getWindows())
        .rules(freezeConfig.getFreezeInfoConfig().getRules())
        .identifier(freezeConfigEntity.getIdentifier())
        .description(getDescription(freezeConfigEntity.getDescription()))
        .name(freezeConfigEntity.getName())
        .status(freezeConfigEntity.getStatus())
        .freezeScope(freezeConfigEntity.getFreezeScope())
        .tags(TagMapper.convertToMap(freezeConfigEntity.getTags()))
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .createdAt(freezeConfigEntity.getCreatedAt())
        .type(freezeConfigEntity.getType())
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .currentOrUpcomingWindow(
            FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeConfig.getFreezeInfoConfig().getWindows()))
        .yaml(freezeConfigEntity.getYaml())
        .build();
  }

  public FreezeSummaryResponseDTO prepareFreezeResponseSummaryDto(FreezeResponseDTO freezeResponseDTO) {
    FreezeConfig freezeConfig = toFreezeConfig(freezeResponseDTO.getYaml());
    return FreezeSummaryResponseDTO.builder()
        .accountId(freezeResponseDTO.getAccountId())
        .orgIdentifier(freezeResponseDTO.getOrgIdentifier())
        .projectIdentifier(freezeResponseDTO.getProjectIdentifier())
        .windows(freezeConfig.getFreezeInfoConfig().getWindows())
        .rules(freezeConfig.getFreezeInfoConfig().getRules())
        .identifier(freezeResponseDTO.getIdentifier())
        .description(getDescription(freezeResponseDTO.getDescription()))
        .name(freezeResponseDTO.getName())
        .status(freezeResponseDTO.getStatus())
        .freezeScope(freezeResponseDTO.getFreezeScope())
        .tags(freezeResponseDTO.getTags())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .createdAt(freezeResponseDTO.getCreatedAt())
        .type(freezeResponseDTO.getType())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .currentOrUpcomingWindow(
            FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeConfig.getFreezeInfoConfig().getWindows()))
        .yaml(freezeResponseDTO.getYaml())
        .build();
  }

  public FreezeBannerDetails prepareBanner(FreezeResponseDTO freezeResponseDTO) {
    FreezeConfig freezeConfig = toFreezeConfig(freezeResponseDTO.getYaml());
    return FreezeBannerDetails.builder()
        .accountId(freezeResponseDTO.getAccountId())
        .orgIdentifier(freezeResponseDTO.getOrgIdentifier())
        .projectIdentifier(freezeResponseDTO.getProjectIdentifier())
        .windows(freezeConfig.getFreezeInfoConfig().getWindows())
        .identifier(freezeResponseDTO.getIdentifier())
        .name(freezeResponseDTO.getName())
        .freezeScope(freezeResponseDTO.getFreezeScope())
        .window(FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeConfig.getFreezeInfoConfig().getWindows()))
        .build();
  }

  public FreezeDetailedResponseDTO prepareDetailedFreezeResponseDto(FreezeResponseDTO freezeResponseDTO) {
    FreezeConfig freezeConfig = toFreezeConfig(freezeResponseDTO.getYaml());
    return FreezeDetailedResponseDTO.builder()
        .accountId(freezeResponseDTO.getAccountId())
        .orgIdentifier(freezeResponseDTO.getOrgIdentifier())
        .projectIdentifier(freezeResponseDTO.getProjectIdentifier())
        .windows(freezeConfig.getFreezeInfoConfig().getWindows())
        .rules(freezeConfig.getFreezeInfoConfig().getRules())
        .identifier(freezeResponseDTO.getIdentifier())
        .description(getDescription(freezeResponseDTO.getDescription()))
        .name(freezeResponseDTO.getName())
        .status(freezeResponseDTO.getStatus())
        .freezeScope(freezeResponseDTO.getFreezeScope())
        .tags(freezeResponseDTO.getTags())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .createdAt(freezeResponseDTO.getCreatedAt())
        .type(freezeResponseDTO.getType())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .currentOrUpcomingWindow(
            FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeConfig.getFreezeInfoConfig().getWindows()))
        .yaml(freezeResponseDTO.getYaml())
        .build();
  }

  public String toYaml(FreezeConfig freezeConfig) {
    return NGYamlUtils.getYamlString(freezeConfig);
  }

  private FreezeConfigEntity toFreezeConfigEntityResponse(String accountId, FreezeConfig freezeConfig,
      String freezeConfigYaml, FreezeType type, String orgId, String projectId) {
    Scope freezeScope = getScopeFromFreezeDto(orgId, projectId);
    validateFreezeYaml(freezeConfig, orgId, projectId, type, freezeScope);
    String description = null;
    if (freezeConfig.getFreezeInfoConfig().getDescription() != null) {
      description = (String) freezeConfig.getFreezeInfoConfig().getDescription().fetchFinalValue();
      description = getDescription(description);
    }
    return FreezeConfigEntity.builder()
        .yaml(freezeConfigYaml)
        .identifier(freezeConfig.getFreezeInfoConfig().getIdentifier())
        .accountId(accountId)
        .orgIdentifier(orgId)
        .projectIdentifier(projectId)
        .name(freezeConfig.getFreezeInfoConfig().getName())
        .status(freezeConfig.getFreezeInfoConfig().getStatus())
        .description(description)
        .tags(TagMapper.convertToList(freezeConfig.getFreezeInfoConfig().getTags()))
        .type(type)
        .freezeScope(freezeScope)
        .build();
  }

  public Scope getScopeFromFreezeDto(String orgId, String projId) {
    if (EmptyPredicate.isNotEmpty(projId)) {
      return Scope.PROJECT;
    }
    if (EmptyPredicate.isNotEmpty(orgId)) {
      return Scope.ORG;
    }
    return Scope.ACCOUNT;
  }

  public String getFreezeRef(Scope freezeScope, String identifier) {
    switch (freezeScope) {
      case ACCOUNT:
        return "account." + identifier;
      case ORG:
        return "org." + identifier;
      case PROJECT:
      default:
        return identifier;
    }
  }

  public boolean setGlobalFreezeStatus(FreezeConfig freezeConfig) {
    FreezeInfoConfig freezeInfoConfig = freezeConfig.getFreezeInfoConfig();
    List<FreezeWindow> windows = freezeInfoConfig.getWindows();
    boolean[] update = {false};
    if (windows != null) {
      windows.stream().forEach(freezeWindow -> {
        try {
          CurrentOrUpcomingWindow currentOrUpcomingWindow =
              FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeInfoConfig.getWindows());
          if (currentOrUpcomingWindow == null && freezeInfoConfig.getStatus() == FreezeStatus.ENABLED) {
            update[0] = true;
            return;
          }
        } catch (Exception e) {
          // Ignore the exception if caught
        }
      });
    }
    return update[0];
  }

  public static void validateFreezeYaml(
      FreezeConfig freezeConfig, String orgId, String projectId, FreezeType type, Scope freezeScope) {
    if (freezeConfig.getFreezeInfoConfig() == null) {
      throw new InvalidRequestException("FreezeInfoConfig cannot be empty");
    }
    FreezeInfoConfig freezeInfoConfig = freezeConfig.getFreezeInfoConfig();
    List<FreezeEntityRule> rules = freezeInfoConfig.getRules();
    List<FreezeWindow> windows = freezeInfoConfig.getWindows();
    if (FreezeType.MANUAL.equals(type)) {
      if (EmptyPredicate.isEmpty(rules)) {
        throw new InvalidRequestException("Rules are required.");
      } else if (EmptyPredicate.isEmpty(windows)) {
        throw new InvalidRequestException("Freeze Windows are required.");
      }
    }

    // Currently we support only 1 window, Remove this validation after multiple windows are supported.
    if (windows != null && windows.size() > 1) {
      throw new InvalidRequestException("Multiple windows are not supported as of now.");
    }

    if (rules != null) {
      rules.stream().forEach(freezeEntityRule -> {
        freezeEntityRule.getEntityConfigList().stream().forEach(entityConfig -> {
          if (!FilterType.ALL.equals(entityConfig.getFilterType())) {
            if (EmptyPredicate.isEmpty(entityConfig.getEntityReference())) {
              throw new InvalidRequestException("Entity references are empty");
            }
            if (FreezeEntityType.ENV_TYPE.equals(entityConfig.getFreezeEntityType())) {
              Set<String> supportedEnvs = Sets.newHashSet("Production", "PreProduction");
              if (!entityConfig.getEntityReference().stream().allMatch(
                      entityReference -> supportedEnvs.contains(entityReference))) {
                throw new InvalidRequestException("Supported EnvTypes are Production and PreProduction");
              }
            }
          }
        });
      });
    }

    if (windows != null) {
      windows.stream().forEach(freezeWindow -> {
        try {
          FreezeStatus freezeStatus = freezeConfig.getFreezeInfoConfig().getStatus();
          FreezeTimeUtils.validateTimeRange(freezeWindow, freezeStatus);
        } catch (ParseException e) {
          throw new InvalidRequestException("Invalid time format provided.", e);
        } catch (DateTimeParseException e) {
          throw new InvalidRequestException(
              "Invalid time format provided. Provide the time in the following format YYYY-MM-DD hh:mm AM/PM", e);
        }
      });
    }
  }

  private String getDescription(String descriptionValue) {
    return descriptionValue == null ? "" : descriptionValue;
  }
}
