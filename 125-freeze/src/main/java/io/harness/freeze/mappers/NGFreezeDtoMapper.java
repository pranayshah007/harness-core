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
import io.harness.freeze.beans.FreezeDuration;
import io.harness.freeze.beans.FreezeType;
import io.harness.freeze.beans.FreezeWindow;
import io.harness.freeze.beans.Recurrence;
import io.harness.freeze.beans.response.FreezeDetailedResponseDTO;
import io.harness.freeze.beans.response.FreezeResponseDTO;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.beans.yaml.FreezeConfig;
import io.harness.freeze.beans.yaml.FreezeInfoConfig;
import io.harness.freeze.entity.FreezeConfigEntity;
import io.harness.freeze.helpers.FreezeTimeUtils;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.utils.YamlPipelineUtils;

import java.io.IOException;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;

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
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .build();
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
        .description(freezeConfigEntity.getDescription())
        .name(freezeConfigEntity.getName())
        .status(freezeConfigEntity.getStatus())
        .freezeScope(freezeConfigEntity.getFreezeScope())
        .tags(TagMapper.convertToMap(freezeConfigEntity.getTags()))
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .createdAt(freezeConfigEntity.getCreatedAt())
        .type(freezeConfigEntity.getType())
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .currentOrUpcomingActiveWindow(
            FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeConfig.getFreezeInfoConfig().getWindows()))
        .build();
  }

  public FreezeDetailedResponseDTO prepareDetailedFreezeResponseDto(FreezeConfigEntity freezeConfigEntity) {
    FreezeConfig freezeConfig = toFreezeConfig(freezeConfigEntity.getYaml());
    return FreezeDetailedResponseDTO.builder()
        .accountId(freezeConfigEntity.getAccountId())
        .orgIdentifier(freezeConfigEntity.getOrgIdentifier())
        .projectIdentifier(freezeConfigEntity.getProjectIdentifier())
        .windows(freezeConfig.getFreezeInfoConfig().getWindows())
        .rules(freezeConfig.getFreezeInfoConfig().getRules())
        .identifier(freezeConfigEntity.getIdentifier())
        .description(freezeConfigEntity.getDescription())
        .name(freezeConfigEntity.getName())
        .status(freezeConfigEntity.getStatus())
        .freezeScope(freezeConfigEntity.getFreezeScope())
        .tags(TagMapper.convertToMap(freezeConfigEntity.getTags()))
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .createdAt(freezeConfigEntity.getCreatedAt())
        .type(freezeConfigEntity.getType())
        .lastUpdatedAt(freezeConfigEntity.getLastUpdatedAt())
        .currentOrUpcomingActiveWindow(
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
        .description(freezeResponseDTO.getDescription())
        .name(freezeResponseDTO.getName())
        .status(freezeResponseDTO.getStatus())
        .freezeScope(freezeResponseDTO.getFreezeScope())
        .tags(freezeResponseDTO.getTags())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .createdAt(freezeResponseDTO.getCreatedAt())
        .type(freezeResponseDTO.getType())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .currentOrUpcomingActiveWindow(
            FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeConfig.getFreezeInfoConfig().getWindows()))
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
        .description(freezeResponseDTO.getDescription())
        .name(freezeResponseDTO.getName())
        .status(freezeResponseDTO.getStatus())
        .freezeScope(freezeResponseDTO.getFreezeScope())
        .tags(freezeResponseDTO.getTags())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .createdAt(freezeResponseDTO.getCreatedAt())
        .type(freezeResponseDTO.getType())
        .lastUpdatedAt(freezeResponseDTO.getLastUpdatedAt())
        .currentOrUpcomingActiveWindow(
            FreezeTimeUtils.fetchCurrentOrUpcomingTimeWindow(freezeConfig.getFreezeInfoConfig().getWindows()))
        .yaml(freezeResponseDTO.getYaml())
        .build();
  }

  public String toYaml(FreezeConfig freezeConfig) {
    return YamlPipelineUtils.writeYamlString(freezeConfig);
  }

  private FreezeConfigEntity toFreezeConfigEntityResponse(String accountId, FreezeConfig freezeConfig,
      String freezeConfigYaml, FreezeType type, String orgId, String projectId) {
    validateFreezeYaml(freezeConfig, orgId, projectId);
    String description = null;
    if (freezeConfig.getFreezeInfoConfig().getDescription() != null) {
      description = (String) freezeConfig.getFreezeInfoConfig().getDescription().fetchFinalValue();
      description = description == null ? "" : description;
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
        .freezeScope(getScopeFromFreezeDto(orgId, projectId))
        .build();
  }

  public static void validateFreezeYaml(FreezeConfig freezeConfig, String orgId, String projectId) {
    if (freezeConfig.getFreezeInfoConfig() == null) {
      throw new InvalidRequestException("FreezeInfoConfig cannot be empty");
    }
    FreezeInfoConfig freezeInfoConfig = freezeConfig.getFreezeInfoConfig();

    // Currently we support only 1 window, Remove this validation after multiple windows are supported.
    if (freezeInfoConfig.getWindows().size() > 1) {
      throw new InvalidRequestException("Multiple windows are not supported as of now.");
    }

    if (freezeInfoConfig.getWindows() != null) {
      freezeInfoConfig.getWindows().stream().forEach(freezeWindow -> {
        try {
          validateTimeRange(freezeWindow, freezeInfoConfig.getName());
        } catch (ParseException e) {
          throw new InvalidRequestException("Invalid time format provided.", e);
        }
      });
    }
  }

  private void validateTimeRange(FreezeWindow freezeWindow, String name) throws ParseException {
    if (EmptyPredicate.isEmpty(freezeWindow.getTimeZone())) {
      throw new InvalidRequestException("Time zone cannot be empty");
    }
    TimeZone timeZone = TimeZone.getTimeZone(freezeWindow.getTimeZone());
    LocalDateTime firstWindowStartTime = LocalDateTime.parse(freezeWindow.getStartTime(), dtf);
    LocalDateTime firstWindowEndTime;
    if (freezeWindow.getEndTime() == null) {
      FreezeDuration freezeDuration = FreezeDuration.fromString(freezeWindow.getDuration());
      Long endTime = FreezeTimeUtils.getEpochValueFromDateString(firstWindowStartTime, timeZone)
          + freezeDuration.getTimeoutInMillis();
      firstWindowEndTime = Instant.ofEpochMilli(endTime).atZone(timeZone.toZoneId()).toLocalDateTime();
    } else {
      firstWindowEndTime = LocalDateTime.parse(freezeWindow.getEndTime(), dtf);
    }

    // Time difference in milliseconds.
    long timeDifferenceInMilliseconds = FreezeTimeUtils.getEpochValueFromDateString(firstWindowEndTime, timeZone)
        - FreezeTimeUtils.getEpochValueFromDateString(firstWindowStartTime, timeZone);
    if (timeDifferenceInMilliseconds < 0) {
      throw new InvalidRequestException("Window Start time is less than Window end Time");
    }
    if (timeDifferenceInMilliseconds < MIN_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be at least 30 minutes");
    }
    if (timeDifferenceInMilliseconds > MAX_FREEZE_WINDOW_TIME) {
      throw new InvalidRequestException("Freeze window time should be less than 365 days");
    }
    if (freezeWindow.getRecurrence() != null) {
      Recurrence recurrence = freezeWindow.getRecurrence();
      if (recurrence.getRecurrenceType() == null) {
        throw new InvalidRequestException("Recurrence Type cannot be empty");
      }
    }
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
}
