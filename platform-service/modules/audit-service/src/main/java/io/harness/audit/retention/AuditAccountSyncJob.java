/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.retention;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditService;
import io.harness.audit.api.AuditSettingsService;
import io.harness.audit.entities.AuditSettings;

import com.google.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class AuditAccountSyncJob implements Runnable {
  @Inject private AuditService auditService;
  @Inject private AuditSettingsService auditSettingsService;

  @Override
  public void run() {
    try {
      Set<String> accountIdentifiers = auditService.getUniqueAuditedAccounts();
      Set<String> accountsInDBWithRetentionPeriod =
          auditSettingsService.fetchAll().stream().map(AuditSettings::getAccountIdentifier).collect(Collectors.toSet());

      accountIdentifiers.removeAll(accountsInDBWithRetentionPeriod);
      accountIdentifiers.forEach(accountIdentifier -> auditSettingsService.create(accountIdentifier, 24));
    } catch (Exception e) {
      log.error("Error in Account and audit retention sync job", e);
    }
  }
}
