/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.serializer.KryoRegistrar;

import software.wings.beans.NotificationChannelType;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.notification.SlackNotificationSetting;

import com.esotericsoftware.kryo.Kryo;

public class NotificationBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(NotificationSettings.class, 5626);
    kryo.register(NotificationChannelType.class, 7115);
    kryo.register(SlackNotificationSetting.class, 7119);
  }
}
