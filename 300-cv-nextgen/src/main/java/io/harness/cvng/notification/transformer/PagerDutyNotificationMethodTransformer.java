/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import io.harness.cvng.notification.channelDetails.CVNGPagerDutyChannelSpec;
import io.harness.cvng.notification.entities.NotificationRule.CVNGPagerDutyChannel;

public class PagerDutyNotificationMethodTransformer
    extends NotificationMethodTransformer<CVNGPagerDutyChannel, CVNGPagerDutyChannelSpec> {
  @Override
  public CVNGPagerDutyChannel getEntityNotificationMethod(CVNGPagerDutyChannelSpec notificationChannelSpec) {
    return CVNGPagerDutyChannel.builder()
        .integrationKey(notificationChannelSpec.getIntegrationKey())
        .userGroups(notificationChannelSpec.getUserGroups())
        .build();
  }

  @Override
  protected CVNGPagerDutyChannelSpec getSpec(CVNGPagerDutyChannel notificationChannel) {
    return CVNGPagerDutyChannelSpec.builder()
        .integrationKey(notificationChannel.getIntegrationKey())
        .userGroups(notificationChannel.getUserGroups())
        .build();
  }
}
