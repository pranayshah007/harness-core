/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.notification.entities.Channel;
import io.harness.notification.entities.Notification;
import io.harness.notification.entities.NotificationChannel;
import io.harness.notification.entities.NotificationRule;
import io.harness.notification.entities.NotificationSetting;
import io.harness.notification.entities.NotificationTemplate;

import java.util.Set;

@OwnedBy(PL)
public class NotificationRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(Notification.class);
    set.add(NotificationTemplate.class);
    set.add(NotificationSetting.class);
    set.add(NotificationChannel.class);
    set.add(NotificationRule.class);
    set.add(Channel.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {}
}
