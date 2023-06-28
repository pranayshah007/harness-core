/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupRequestV2DTO;
import io.harness.ng.core.dto.UserGroupResponse;
import io.harness.ng.core.dto.UserGroupResponseV2DTO;
import io.harness.ng.core.dto.UserInfo;
import io.harness.ng.core.entities.EmailConfig;
import io.harness.ng.core.entities.MicrosoftTeamsConfig;
import io.harness.ng.core.entities.NotificationSettingConfig;
import io.harness.ng.core.entities.PagerDutyConfig;
import io.harness.ng.core.entities.SlackConfig;
import io.harness.ng.core.entities.WebhookConfig;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.MicrosoftTeamsConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.notification.PagerDutyConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.ng.core.notification.WebhookConfigDTO;
import io.harness.ng.core.user.entities.UserGroup;

import software.wings.beans.sso.SSOType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class UserGroupMapper {
  public static UserGroupDTO toDTO(UserGroup userGroup) {
    if (null == userGroup) {
      return null;
    }

    UserGroupDTO groupDTO = UserGroupDTO.builder()
                                .accountIdentifier(userGroup.getAccountIdentifier())
                                .orgIdentifier(userGroup.getOrgIdentifier())
                                .projectIdentifier(userGroup.getProjectIdentifier())
                                .identifier(userGroup.getIdentifier())
                                .description(userGroup.getDescription())
                                .tags(convertToMap(userGroup.getTags()))
                                .name(userGroup.getName())
                                .ssoGroupId(userGroup.getSsoGroupId())
                                .ssoGroupName(userGroup.getSsoGroupName())
                                .externallyManaged(userGroup.isExternallyManaged())
                                .linkedSsoDisplayName(userGroup.getLinkedSsoDisplayName())
                                .linkedSsoId(userGroup.getLinkedSsoId())
                                .isSsoLinked(TRUE.equals(userGroup.getIsSsoLinked()))
                                .harnessManaged(TRUE.equals(userGroup.isHarnessManaged()))
                                .notificationConfigs(emptyIfNull(userGroup.getNotificationConfigs())
                                                         .stream()
                                                         .map(UserGroupMapper::toDTO)
                                                         .collect(Collectors.toList()))
                                .users(userGroup.getUsers() == null ? emptyList() : userGroup.getUsers())
                                .build();
    if (null != userGroup.getLinkedSsoType()) {
      groupDTO.setLinkedSsoType(userGroup.getLinkedSsoType().name());
    }

    return groupDTO;
  }

  public static UserGroupRequestV2DTO fromV1(UserGroupDTO userGroup, List<String> emails) {
    return UserGroupRequestV2DTO.builder()
        .accountIdentifier(userGroup.getAccountIdentifier())
        .orgIdentifier(userGroup.getOrgIdentifier())
        .projectIdentifier(userGroup.getProjectIdentifier())
        .identifier(userGroup.getIdentifier())
        .description(userGroup.getDescription())
        .tags(userGroup.getTags())
        .name(userGroup.getName())
        .ssoGroupId(userGroup.getSsoGroupId())
        .ssoGroupName(userGroup.getSsoGroupName())
        .externallyManaged(userGroup.isExternallyManaged())
        .linkedSsoDisplayName(userGroup.getLinkedSsoDisplayName())
        .linkedSsoId(userGroup.getLinkedSsoId())
        .isSsoLinked(userGroup.isSsoLinked())
        .linkedSsoType(userGroup.getLinkedSsoType())
        .harnessManaged(userGroup.isHarnessManaged())
        .notificationConfigs(userGroup.getNotificationConfigs())
        .users(emails)
        .build();
  }

  public static UserGroupResponseV2DTO toV2Response(UserGroupDTO userGroup, List<UserInfo> users) {
    return UserGroupResponseV2DTO.builder()
        .accountIdentifier(userGroup.getAccountIdentifier())
        .orgIdentifier(userGroup.getOrgIdentifier())
        .projectIdentifier(userGroup.getProjectIdentifier())
        .identifier(userGroup.getIdentifier())
        .description(userGroup.getDescription())
        .tags(userGroup.getTags())
        .name(userGroup.getName())
        .ssoGroupId(userGroup.getSsoGroupId())
        .ssoGroupName(userGroup.getSsoGroupName())
        .externallyManaged(userGroup.isExternallyManaged())
        .linkedSsoDisplayName(userGroup.getLinkedSsoDisplayName())
        .linkedSsoId(userGroup.getLinkedSsoId())
        .isSsoLinked(userGroup.isSsoLinked())
        .linkedSsoType(userGroup.getLinkedSsoType())
        .harnessManaged(userGroup.isHarnessManaged())
        .notificationConfigs(userGroup.getNotificationConfigs())
        .users(users)
        .build();
  }

  public static UserGroupDTO toV1(UserGroupRequestV2DTO userGroup, List<String> uuids) {
    return UserGroupDTO.builder()
        .accountIdentifier(userGroup.getAccountIdentifier())
        .orgIdentifier(userGroup.getOrgIdentifier())
        .projectIdentifier(userGroup.getProjectIdentifier())
        .identifier(userGroup.getIdentifier())
        .description(userGroup.getDescription())
        .tags(userGroup.getTags())
        .name(userGroup.getName())
        .ssoGroupId(userGroup.getSsoGroupId())
        .ssoGroupName(userGroup.getSsoGroupName())
        .externallyManaged(userGroup.isExternallyManaged())
        .linkedSsoDisplayName(userGroup.getLinkedSsoDisplayName())
        .linkedSsoId(userGroup.getLinkedSsoId())
        .isSsoLinked(userGroup.isSsoLinked())
        .linkedSsoType(userGroup.getLinkedSsoType())
        .harnessManaged(userGroup.isHarnessManaged())
        .notificationConfigs(userGroup.getNotificationConfigs())
        .users(uuids)
        .build();
  }

  public static UserGroup toEntity(UserGroupDTO userGroupDTO) {
    if (null == userGroupDTO) {
      return null;
    }

    UserGroup group =
        UserGroup.builder()
            .accountIdentifier(userGroupDTO.getAccountIdentifier())
            .orgIdentifier(userGroupDTO.getOrgIdentifier())
            .projectIdentifier(userGroupDTO.getProjectIdentifier())
            .identifier(userGroupDTO.getIdentifier())
            .name(userGroupDTO.getName())
            .externallyManaged(userGroupDTO.isExternallyManaged())
            .notificationConfigs((Optional.ofNullable(userGroupDTO.getNotificationConfigs()).orElse(emptyList()))
                                     .stream()
                                     .map(UserGroupMapper::toEntity)
                                     .collect(Collectors.toList()))
            .description(Optional.ofNullable(userGroupDTO.getDescription()).orElse(""))
            .tags(convertToList(Optional.ofNullable(userGroupDTO.getTags()).orElse(emptyMap())))
            .users(Optional.ofNullable(userGroupDTO.getUsers()).orElse(emptyList()))
            .isSsoLinked(userGroupDTO.isSsoLinked())
            .ssoGroupId(userGroupDTO.getSsoGroupId())
            .ssoGroupName(userGroupDTO.getSsoGroupName())
            .linkedSsoDisplayName(userGroupDTO.getLinkedSsoDisplayName())
            .linkedSsoId(userGroupDTO.getLinkedSsoId())
            .harnessManaged(TRUE.equals(userGroupDTO.isHarnessManaged()))
            .build();

    if (isNotEmpty(userGroupDTO.getLinkedSsoType())) {
      SSOType ssoType;
      try {
        ssoType = SSOType.valueOf(userGroupDTO.getLinkedSsoType());
      } catch (IllegalArgumentException ex) {
        throw new InvalidRequestException(String.format("Invalid LinkedSsoType passed: [%s]. Valid SSO Types: %s",
            userGroupDTO.getLinkedSsoType(), Arrays.toString(SSOType.values())));
      }
      group.setLinkedSsoType(ssoType);
    }

    return group;
  }

  public static NotificationSettingConfig toEntity(NotificationSettingConfigDTO dto) {
    if (dto == null) {
      return null;
    }
    switch (dto.getType()) {
      case SLACK:
        return SlackConfig.builder().slackWebhookUrl(((SlackConfigDTO) dto).getSlackWebhookUrl()).build();
      case PAGERDUTY:
        return PagerDutyConfig.builder().pagerDutyKey(((PagerDutyConfigDTO) dto).getPagerDutyKey()).build();
      case MSTEAMS:
        return MicrosoftTeamsConfig.builder()
            .microsoftTeamsWebhookUrl(((MicrosoftTeamsConfigDTO) dto).getMicrosoftTeamsWebhookUrl())
            .build();
      case EMAIL:
        return EmailConfig.builder()
            .groupEmail(((EmailConfigDTO) dto).getGroupEmail())
            .sendEmailToAllUsers(((EmailConfigDTO) dto).getSendEmailToAllUsers())
            .build();
      case WEBHOOK:
        return WebhookConfig.builder().webhookUrl(((WebhookConfigDTO) dto).getWebhookUrl()).build();
      default:
        throw new IllegalArgumentException("This is not a valid Notification Setting Type: " + dto.getType());
    }
  }

  public static NotificationSettingConfigDTO toDTO(NotificationSettingConfig entity) {
    if (entity == null) {
      return null;
    }
    switch (entity.getType()) {
      case SLACK:
        return SlackConfigDTO.builder().slackWebhookUrl(((SlackConfig) entity).getSlackWebhookUrl()).build();
      case PAGERDUTY:
        return PagerDutyConfigDTO.builder().pagerDutyKey(((PagerDutyConfig) entity).getPagerDutyKey()).build();
      case MSTEAMS:
        return MicrosoftTeamsConfigDTO.builder()
            .microsoftTeamsWebhookUrl(((MicrosoftTeamsConfig) entity).getMicrosoftTeamsWebhookUrl())
            .build();
      case EMAIL:
        return EmailConfigDTO.builder()
            .groupEmail(((EmailConfig) entity).getGroupEmail())
            .sendEmailToAllUsers(((EmailConfig) entity).getSendEmailToAllUsers())
            .build();
      case WEBHOOK:
        return WebhookConfigDTO.builder().webhookUrl(((WebhookConfig) entity).getWebhookUrl()).build();
      default:
        throw new IllegalArgumentException("This is not a valid Notification Setting Type: " + entity.getType());
    }
  }

  public static UserGroupResponse toResponseWrapper(UserGroup userGroup) {
    return UserGroupResponse.builder()
        .createdAt(userGroup.getCreatedAt())
        .lastModifiedAt(userGroup.getLastModifiedAt())
        .userGroup(toDTO(userGroup))
        .build();
  }
}
