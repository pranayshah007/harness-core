/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.notification.NotificationRequest.Webhook;

import io.harness.annotations.dev.OwnedBy;
import io.harness.notification.NotificationChannelType;
import io.harness.notification.dtos.UserGroup;
import io.harness.notification.mapper.NotificationUserGroupMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(PL)
@Data
@Builder
@EqualsAndHashCode()
@JsonTypeName("Webhook")
public class WebhookChannel implements Channel {
  List<String> webHookUrls;
  List<UserGroup> userGroups;
  Map<String, String> templateData;
  @Override
  public Object toObjectofProtoSchema() {
    return Webhook.newBuilder()
        .addAllUrls(webHookUrls)
        .putAllTemplateData(templateData)
        .addAllUserGroup(NotificationUserGroupMapper.toProto(userGroups))
        .build();
  }

  @Override
  @JsonIgnore
  public NotificationChannelType getChannelType() {
    return NotificationChannelType.WEBHOOK;
  }

  public static WebhookChannel toWebhookEntity(Webhook webhookDetails) {
    return WebhookChannel.builder()
        .webHookUrls(webhookDetails.getUrlsList())
        .templateData(webhookDetails.getTemplateDataMap())
        .userGroups(NotificationUserGroupMapper.toEntity(webhookDetails.getUserGroupList()))
        .build();
  }
}
