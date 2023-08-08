/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.api.AuditSettingsService;
import io.harness.audit.entities.AuditSettings;
import io.harness.audit.repositories.AuditRetentionRepository;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@OwnedBy(PL)
public class AuditSettingsServiceImpl implements AuditSettingsService {
  @Inject private AuditRetentionRepository auditRetentionRepository;

  @Override
  public AuditSettings getAuditRetentionPolicy(String accountIdentifier) {
    Optional<AuditSettings> optionalAuditRetention =
        auditRetentionRepository.findByAccountIdentifier(accountIdentifier);
    return optionalAuditRetention.orElse(null);
  }

  @Override
  public AuditSettings update(String accountIdentifier, int months) {
    AuditSettings auditSettings = getAuditRetentionPolicy(accountIdentifier);
    if (auditSettings != null) {
      auditSettings.setRetentionPeriodInMonths(months);
      return auditRetentionRepository.save(auditSettings);
    }
    return create(accountIdentifier, months);
  }

  @Override
  public AuditSettings create(String accountIdentifier, int months) {
    Long now = System.currentTimeMillis();
    AuditSettings auditSettings = AuditSettings.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .retentionPeriodInMonths(months)
                                      .nextIteration(now + TimeUnit.HOURS.toMillis(12))
                                      .build();
    return auditRetentionRepository.save(auditSettings);
  }

  @Override
  public List<AuditSettings> fetchAll() {
    List<AuditSettings> auditSettingsList = new ArrayList<>();
    auditRetentionRepository.findAll().iterator().forEachRemaining(auditSettingsList::add);
    return auditSettingsList;
  }

  @Override
  public void deleteByAccountIdentifier(String accountIdentifier) {
    auditRetentionRepository.deleteByAccountIdentifier(accountIdentifier);
  }
}
