/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.entities;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.notification.NotificationRequest.Email;

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
@JsonTypeName("Email")
public class EmailChannel implements Channel {
  List<String> emailIds;
  List<UserGroup> userGroups;
  Map<String, String> templateData;
  String templateId;

  @Override
  public Object toObjectofProtoSchema() {
    return Email.newBuilder()
        .addAllEmailIds(emailIds)
        .putAllTemplateData(templateData)
        .setTemplateId(templateId)
        .addAllUserGroup(NotificationUserGroupMapper.toProto(userGroups))
        .build();
  }

  @Override
  @JsonIgnore
  public NotificationChannelType getChannelType() {
    return NotificationChannelType.EMAIL;
  }

  public static EmailChannel toEmailEntity(Email emailDetails) {
    return EmailChannel.builder()
        .emailIds(emailDetails.getEmailIdsList())
        .templateData(emailDetails.getTemplateDataMap())
        .templateId(emailDetails.getTemplateId())
        .userGroups(NotificationUserGroupMapper.toEntity(emailDetails.getUserGroupList()))
        .build();
  }
}
