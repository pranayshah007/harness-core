/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(PL)
@UtilityClass
public class NotificationSettingsHelper {
  public List<String> getRecipientsWithValidDomain(List<String> recipients, List<String> targetAllowlist) {
    if (isEmpty(targetAllowlist)) {
      return recipients;
    }
    return recipients.stream()
        .filter(recipient -> validateTargetDomainFromGiveAllowlist(recipient, targetAllowlist))
        .collect(Collectors.toList());
  }

  private boolean validateTargetDomainFromGiveAllowlist(String recipient, List<String> targetDomainAllowlist) {
    return targetDomainAllowlist.stream().anyMatch(recipient::contains);
  }
}
