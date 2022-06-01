/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import io.harness.cvng.notification.channelDetails.CVNGSlackChannelSpec;
import io.harness.cvng.notification.entities.NotificationRule.CVNGSlackChannel;

public class SlackNotificationMethodTransformer
    extends NotificationMethodTransformer<CVNGSlackChannel, CVNGSlackChannelSpec> {
  @Override
  public CVNGSlackChannel getEntityNotificationMethod(CVNGSlackChannelSpec notificationChannelSpec) {
    return CVNGSlackChannel.builder()
        .webhookUrl(notificationChannelSpec.getWebhookUrl())
        .userGroups(notificationChannelSpec.getUserGroups())
        .build();
  }

  @Override
  protected CVNGSlackChannelSpec getSpec(CVNGSlackChannel notificationChannel) {
    return CVNGSlackChannelSpec.builder()
        .webhookUrl(notificationChannel.getWebhookUrl())
        .userGroups(notificationChannel.getUserGroups())
        .build();
  }
}
