/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.channelDetails;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.SlackChannel;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Lists;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(NotificationChannelType.SLACK)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PmsSlackChannel extends PmsNotificationChannel {
  List<String> userGroups;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> webhookUrl;

  @Override
  public NotificationChannel toNotificationChannel(String accountId, String orgIdentifier, String projectIdentifier,
      String templateId, Map<String, String> templateData) {
    return SlackChannel.builder()
        .accountId(accountId)
        .team(Team.PIPELINE)
        .templateData(templateData)
        .templateId(templateId)
        .userGroups(
            userGroups.stream()
                .map(e -> NotificationChannelUtils.getUserGroups(e, accountId, orgIdentifier, projectIdentifier))
                .collect(Collectors.toList()))
        .webhookUrls(
            Lists.newArrayList(webhookUrl.getValue() != null ? webhookUrl.getValue() : webhookUrl.getExpressionValue()))
        .build();
  }
}
