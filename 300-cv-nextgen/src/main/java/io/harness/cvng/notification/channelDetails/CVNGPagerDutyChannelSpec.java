/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.channelDetails;

import io.harness.Team;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.channeldetails.PagerDutyChannel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CVNGPagerDutyChannelSpec extends CVNGNotificationChannelSpec {
  List<String> userGroups;
  String integrationKey;

  @Override
  public CVNGNotificationChannelType getType() {
    return CVNGNotificationChannelType.PAGERDUTY;
  }

  @Override
  public NotificationChannel toNotificationChannel(String accountId, String orgIdentifier, String projectIdentifier,
      String templateId, Map<String, String> templateData) {
    return PagerDutyChannel.builder()
        .accountId(accountId)
        .userGroups(
            userGroups.stream()
                .map(e -> CVNGNotificationChannelUtils.getUserGroups(e, accountId, orgIdentifier, projectIdentifier))
                .collect(Collectors.toList()))
        .team(Team.CV)
        .templateId(templateId)
        .integrationKeys(Lists.newArrayList(integrationKey))
        .templateData(templateData)
        .build();
  }
}
