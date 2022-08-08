/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.notification.entities.NotificationSetting;
import io.harness.notification.remote.dto.AccountNotificationSettingDTO;
import io.harness.notification.remote.mappers.NotificationSettingMapper;
import io.harness.notification.service.api.NotificationSettingsService;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.AllArgsConstructor;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class NotificationSettingsResourceImpl implements NotificationSettingsResource {
  private final NotificationSettingsService notificationSettingsService;

  public ResponseDTO<Optional<AccountNotificationSettingDTO>> getNotificationSetting(String accountId) {
    return ResponseDTO.newResponse(
        NotificationSettingMapper.toDTO(notificationSettingsService.getNotificationSetting(accountId).orElse(null)));
  }

  public ResponseDTO<Optional<AccountNotificationSettingDTO>> putSendNotificationViaDelegate(
      String accountId, boolean sendNotificationViaDelegate) {
    NotificationSetting notificationSetting =
        notificationSettingsService.setSendNotificationViaDelegate(accountId, sendNotificationViaDelegate);
    return ResponseDTO.newResponse(NotificationSettingMapper.toDTO(notificationSetting));
  }
}
