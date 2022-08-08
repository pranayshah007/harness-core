/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.mappers;

import io.harness.notification.entities.NotificationSetting;
import io.harness.notification.remote.dto.AccountNotificationSettingDTO;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationSettingMapper {
  public static Optional<AccountNotificationSettingDTO> toDTO(NotificationSetting notificationSetting) {
    if (!Optional.ofNullable(notificationSetting).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(AccountNotificationSettingDTO.builder()
                           .accountId(notificationSetting.getAccountId())
                           .sendNotificationViaDelegate(notificationSetting.isSendNotificationViaDelegate())
                           .smtpConfig(notificationSetting.getSmtpConfig())
                           .build());
  }
}
