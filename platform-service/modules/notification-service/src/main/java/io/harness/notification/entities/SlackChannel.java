/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.notification.NotificationRequest.Slack;

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
@JsonTypeName("Slack")
public class SlackChannel implements Channel {
  List<String> slackWebHookUrls;
  List<UserGroup> userGroups;
  Map<String, String> templateData;
  String message;
  @Override
  public Object toObjectofProtoSchema() {
    Slack.Builder builder = Slack.newBuilder()
                                .addAllSlackWebHookUrls(slackWebHookUrls)
                                .putAllTemplateData(templateData)
                                .addAllUserGroup(NotificationUserGroupMapper.toProto(userGroups));
    if (isNotEmpty(message)) {
      builder.setMessage(message);
    }
    return builder.build();
  }

  @Override
  @JsonIgnore
  public NotificationChannelType getChannelType() {
    return NotificationChannelType.SLACK;
  }

  public static SlackChannel toSlackEntity(Slack slackDetails) {
    return SlackChannel.builder()
        .slackWebHookUrls(slackDetails.getSlackWebHookUrlsList())
        .templateData(slackDetails.getTemplateDataMap())
        .userGroups(NotificationUserGroupMapper.toEntity(slackDetails.getUserGroupList()))
        .message(slackDetails.getMessage())
        .build();
  }
}
