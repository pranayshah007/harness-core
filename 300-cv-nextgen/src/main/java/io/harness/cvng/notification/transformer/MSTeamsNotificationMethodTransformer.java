/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import io.harness.cvng.notification.channelDetails.CVNGMSTeamsChannelSpec;
import io.harness.cvng.notification.entities.NotificationRule.CVNGMSTeamsChannel;

public class MSTeamsNotificationMethodTransformer
    extends NotificationMethodTransformer<CVNGMSTeamsChannel, CVNGMSTeamsChannelSpec> {
  @Override
  public CVNGMSTeamsChannel getEntityNotificationMethod(CVNGMSTeamsChannelSpec notificationChannelSpec) {
    return CVNGMSTeamsChannel.builder()
        .msTeamKeys(notificationChannelSpec.getMsTeamKeys())
        .userGroups(notificationChannelSpec.getUserGroups())
        .build();
  }

  @Override
  protected CVNGMSTeamsChannelSpec getSpec(CVNGMSTeamsChannel notificationChannel) {
    return CVNGMSTeamsChannelSpec.builder()
        .msTeamKeys(notificationChannel.getMsTeamKeys())
        .userGroups(notificationChannel.getUserGroups())
        .build();
  }
}
