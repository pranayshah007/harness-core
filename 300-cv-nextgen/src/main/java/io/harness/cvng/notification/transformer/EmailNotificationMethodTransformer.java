/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import io.harness.cvng.notification.channelDetails.CVNGEmailChannelSpec;
import io.harness.cvng.notification.entities.NotificationRule.CVNGEmailChannel;

public class EmailNotificationMethodTransformer
    extends NotificationMethodTransformer<CVNGEmailChannel, CVNGEmailChannelSpec> {
  @Override
  public CVNGEmailChannel getEntityNotificationMethod(CVNGEmailChannelSpec notificationChannelSpec) {
    return CVNGEmailChannel.builder()
        .recipients(notificationChannelSpec.getRecipients())
        .userGroups(notificationChannelSpec.getUserGroups())
        .build();
  }

  @Override
  public CVNGEmailChannelSpec getSpec(CVNGEmailChannel notificationChannel) {
    return CVNGEmailChannelSpec.builder()
        .recipients(notificationChannel.getRecipients())
        .userGroups(notificationChannel.getUserGroups())
        .build();
  }
}
