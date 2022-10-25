/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.governance.pipeline.service;

import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.pipeline.service.model.PipelineGovernanceConfig;
import io.harness.governance.pipeline.service.model.PipelineGovernanceConfig.PipelineGovernanceConfigKeys;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.persistence.HQuery;

import software.wings.beans.Event;
import software.wings.dl.WingsPersistence;
import software.wings.features.PipelineGovernanceFeature;
import software.wings.features.api.AccountId;
import software.wings.features.api.RestrictedApi;
import software.wings.service.impl.AuditServiceHelper;

import com.google.inject.Inject;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ParametersAreNonnullByDefault
@OwnedBy(HarnessTeam.CDC)
public class PipelineGovernanceServiceImpl implements PipelineGovernanceService {
  @Inject private WingsPersistence persistence;
  @Inject private AuditServiceHelper auditServiceHelper;

  @Override
  public PipelineGovernanceConfig get(final String uuid) {
    return persistence.createQuery(PipelineGovernanceConfig.class)
        .field(PipelineGovernanceConfigKeys.uuid)
        .equal(uuid)
        .get();
  }

  @Override
  public boolean delete(final String accountId, final String uuid) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting pipeline governance standard  with uuid: {}", uuid);
      PipelineGovernanceConfig config = get(uuid);
      boolean deleted = persistence.delete(PipelineGovernanceConfig.class, uuid);
      if (deleted) {
        auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, config);
      }
      return deleted;
    }
  }

  @Override
  public List<PipelineGovernanceConfig> list(final String accountId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Fetching all pipeline governance standards");
      return persistence.createQuery(PipelineGovernanceConfig.class, HQuery.excludeCount)
          .field(PipelineGovernanceConfigKeys.accountId)
          .equal(accountId)
          .asList();
    }
  }

  @Override
  @RestrictedApi(PipelineGovernanceFeature.class)
  public PipelineGovernanceConfig add(@AccountId final String accountId, final PipelineGovernanceConfig config) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Adding pipeline governance standard");
      PipelineGovernanceConfig existingConfig = get(config.getUuid());
      persistence.save(config);
      if (existingConfig == null) {
        auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, config, Event.Type.CREATE);
      } else {
        auditServiceHelper.reportForAuditingUsingAccountId(accountId, existingConfig, config, Event.Type.UPDATE);
      }
      return config;
    }
  }
}
