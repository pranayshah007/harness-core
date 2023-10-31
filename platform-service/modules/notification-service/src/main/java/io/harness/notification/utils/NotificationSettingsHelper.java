/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.notification.exception.NotificationException;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
@Singleton
public class NotificationSettingsHelper {
  @Inject private NGSettingsClient ngSettingsClient;

  public List<String> getTargetAllowlistFromSettings(String settingIdentifier, String accountIdentifier) {
    SettingValueResponseDTO settingValueResponseDTO;
    try {
      settingValueResponseDTO =
          NGRestUtils.getResponse(ngSettingsClient.getSetting(settingIdentifier, accountIdentifier, null, null));
    } catch (Exception exception) {
      return Collections.emptyList();
    }
    if (Objects.isNull(settingValueResponseDTO) || isEmpty(settingValueResponseDTO.getValue())) {
      return Collections.emptyList();
    }

    String targetAllowlist = settingValueResponseDTO.getValue();
    List<String> listOfAllowedTargets = List.of(targetAllowlist.split(","));
    return HarnessStringUtils.removeLeadingAndTrailingSpacesInListOfStrings(listOfAllowedTargets);
  }

  private boolean validateTargetDomainFromGiveAllowlist(String recipient, List<String> targetDomainAllowlist) {
    return targetDomainAllowlist.stream().anyMatch(recipient::contains);
  }

  public void validateRecipient(String recipient, String accountId, String settingIdentifier) {
    List<String> targetAllowlist = getTargetAllowlistFromSettings(settingIdentifier, accountId);
    if (isNotEmpty(targetAllowlist) && !validateTargetDomainFromGiveAllowlist(recipient, targetAllowlist)) {
      throw new NotificationException(
          String.format("Invalid recipient [%s] as it doesn't go to target domain.", recipient), DEFAULT_ERROR_CODE,
          USER);
    }
  }
}
