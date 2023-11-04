/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.channeldetails;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.notification.NotificationRequest;
import io.harness.notification.Team;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@EqualsAndHashCode(callSuper = true)
public class SlackChannel extends NotificationChannel {
  List<String> webhookUrls;
  String orgIdentifier;
  String projectIdentifier;
  long expressionFunctorToken;
  String message;

  @Builder
  public SlackChannel(String accountId, List<NotificationRequest.UserGroup> userGroups, String templateId,
      Map<String, String> templateData, Team team, List<String> webhookUrls, String orgIdentifier,
      String projectIdentifier, long expressionFunctorToken, String message) {
    super(accountId, userGroups, templateId, templateData, team);
    this.webhookUrls = webhookUrls;
    this.orgIdentifier = orgIdentifier;
    this.projectIdentifier = projectIdentifier;
    this.expressionFunctorToken = expressionFunctorToken;
    this.message = message;
  }

  @Override
  public NotificationRequest buildNotificationRequest() {
    NotificationRequest.Builder builder = NotificationRequest.newBuilder();
    String notificationId = generateUuid();
    return builder.setId(notificationId).setAccountId(accountId).setTeam(team).setSlack(buildSlack(builder)).build();
  }

  private NotificationRequest.Slack buildSlack(NotificationRequest.Builder builder) {
    NotificationRequest.Slack.Builder slackBuilder = builder.getSlackBuilder()
                                                         .addAllSlackWebHookUrls(webhookUrls)
                                                         .setTemplateId(templateId)
                                                         .putAllTemplateData(templateData)
                                                         .setMessage(message)
                                                         .addAllUserGroup(CollectionUtils.emptyIfNull(userGroups));

    if (orgIdentifier != null) {
      slackBuilder.setOrgIdentifier(orgIdentifier);
    }
    if (projectIdentifier != null) {
      slackBuilder.setProjectIdentifier(projectIdentifier);
    }
    slackBuilder.setExpressionFunctorToken(expressionFunctorToken);
    return slackBuilder.build();
  }
}
