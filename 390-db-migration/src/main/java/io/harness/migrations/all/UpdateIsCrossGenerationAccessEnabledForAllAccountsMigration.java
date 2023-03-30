/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static software.wings.beans.Account.AccountKeys;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.licensing.beans.modules.AccountLicenseDTO;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.logging.MdcContextSetter;
import io.harness.logging.ResponseTimeRecorder;
import io.harness.migrations.Migration;
import io.harness.ng.core.account.DefaultExperience;
import io.harness.persistence.HIterator;
import io.harness.remote.client.NGRestUtils;

import software.wings.beans.Account;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateOpsImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.GTM)
public class UpdateIsCrossGenerationAccessEnabledForAllAccountsMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private NgLicenseHttpClient ngLicenseHttpClient;

  private static final String CROSS_GEN_ACCESS_MIGRATION_LOGS_KEY = "crossGenAccessMigrationLogs";

  @Override
  public void migrate() {
    log.info("Starting the migration to add new isCrossGenerationAccessEnabled field into all the accounts");

    Query<Account> accountsQuery = wingsPersistence.createQuery(Account.class);
    log.info("Migration will run for total {} accounts", accountsQuery.count());
    Set<String> accountsLeftForMigration = new HashSet<>();

    try (
        ResponseTimeRecorder ignore = new ResponseTimeRecorder(
            "Update isCrossGenerationAccessEnabled field for all the accounts BG Migration Job");
        MdcContextSetter ignore1 = new MdcContextSetter(Map.of(CROSS_GEN_ACCESS_MIGRATION_LOGS_KEY, generateUuid()));) {
      try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
        while (records.hasNext()) {
          Account account = null;
          try {
            account = records.next();
            UpdateOperations<Account> updateOperations = wingsPersistence.createUpdateOperations(Account.class);

            if (isAccountNonCd(account.getUuid())) {
              updateOperations.set(AccountKeys.isCrossGenerationAccessEnabled, Boolean.FALSE);
            } else {
              if (DefaultExperience.isNGExperience(account.getDefaultExperience())) {
                updateOperations.set(AccountKeys.isCrossGenerationAccessEnabled, Boolean.TRUE);
              } else {
                Boolean crossGenAccessValue =
                    accountService.isFeatureFlagEnabled(FeatureName.PL_HIDE_LAUNCH_NEXTGEN.name(), account.getUuid())
                    ? Boolean.FALSE
                    : Boolean.TRUE;
                updateOperations.set(AccountKeys.isCrossGenerationAccessEnabled, crossGenAccessValue);
              }
            }

            wingsPersistence.update(account, updateOperations);
            log.info("isCrossGenerationAccessEnabled field is successfully updated for account: {} with values: {}",
                account.getUuid(), ((UpdateOpsImpl<?>) updateOperations).getOps().get("$set"));
          } catch (Exception e) {
            accountsLeftForMigration.add(account.getUuid());
            log.error("Error while updating isCrossGenerationAccessEnabled field for account: {}",
                account != null ? account.getUuid() : "", e);
          }
        }
      } finally {
        log.info("Total accounts with failed migration= {} and the list= {}", accountsLeftForMigration.size(),
            accountsLeftForMigration);
      }
    }

    log.info("Migration to add new isCrossGenerationAccessEnabled field into all the accounts finished");
  }

  private boolean isAccountNonCd(String accountIdentifier) {
    AccountLicenseDTO accountLicenseDTO =
        NGRestUtils.getResponse(ngLicenseHttpClient.getAccountLicensesDTO(accountIdentifier));
    List<ModuleLicenseDTO> cdModuleLicenses = accountLicenseDTO.getAllModuleLicenses().get(ModuleType.CD);
    log.info("Account: {} is a non-CD account: {}", accountIdentifier, cdModuleLicenses.isEmpty());

    return cdModuleLicenses.isEmpty();
  }
}
