/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.service.impl.compliance.GovernanceConfigServiceImpl.GOVERNANCE_CONFIG;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.governance.GovernanceConfig;
import software.wings.beans.governance.GovernanceConfig.GovernanceConfigKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.compliance.GovernanceConfigService;

import com.google.inject.Inject;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddDeploymentFreezeReferenceToUserGroups implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Inject private UserGroupService userGroupService;

  @Inject private GovernanceConfigService governanceConfigService;

  @Override
  public void migrate() {
    log.info("Starting to add pipelines reference in userGroups.");
    try (HIterator<Account> accounts = new HIterator<>(wingsPersistence.createQuery(Account.class).fetch())) {
      while (accounts.hasNext()) {
        migrateDeploymentFreezeWindowsForAccount(accounts.next());
      }
    } catch (Exception ex) {
      log.error("Exception while fetching Accounts");
    }
  }

  private void migrateDeploymentFreezeWindowsForAccount(Account account) {
    log.info("Migrating for account {}", account.getUuid());

    try {
      GovernanceConfig governanceConfig = wingsPersistence.createQuery(GovernanceConfig.class)
                                              .filter(GovernanceConfigKeys.accountId, account.getUuid())
                                              .get();
      if (governanceConfig == null || isEmpty(governanceConfig.getTimeRangeBasedFreezeConfigs())) {
        log.info("Nothing to migrate for the account");
        return;
      }
      Set<String> userGroupIds =
          governanceConfigService.getReferencedUserGroupIds(governanceConfig.getTimeRangeBasedFreezeConfigs());
      for (String id : userGroupIds) {
        userGroupService.addParentsReference(
            id, account.getUuid(), governanceConfig.getAppId(), governanceConfig.getUuid(), GOVERNANCE_CONFIG);
      }
    } catch (Exception ex) {
      log.error("Exception while fetching GovernanceConfigs for Account {} ", account.getUuid());
    }
    log.info("Completed migration for account {}", account.getUuid());
  }
}
