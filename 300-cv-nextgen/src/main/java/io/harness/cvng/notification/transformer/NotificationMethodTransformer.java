/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.notification.transformer;

import io.harness.cvng.notification.channelDetails.CVNGNotificationChannel;
import io.harness.cvng.notification.channelDetails.CVNGNotificationChannelSpec;
import io.harness.cvng.notification.entities.NotificationRule;

public abstract class NotificationMethodTransformer<E extends NotificationRule.CVNGNotificationChannel, S
                                                        extends CVNGNotificationChannelSpec> {
  public abstract E getEntityNotificationMethod(S notificationChannelSpec);

  public final CVNGNotificationChannel getDTONotificationMethod(E notificationChannel) {
    return CVNGNotificationChannel.builder()
        .type(notificationChannel.getType())
        .spec(getSpec(notificationChannel))
        .build();
  }

  protected abstract S getSpec(E notificationChannel);
}
