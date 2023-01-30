/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.dl.WingsPersistence;
import software.wings.licensing.LicenseService;
import software.wings.service.impl.LicenseUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.instance.licensing.InstanceLimitProvider;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

/**
 * Migration script to update license info for all accounts.
 *
 * @author rktummala on 11/02/18
 */
@Slf4j
public class LicenseDataMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private LicenseService licenseService;

  @Override
  public void migrate() {
    log.info("LicenseMigration - Start - Updating license info for all accounts");
    Query<Account> accountsQuery = wingsPersistence.createQuery(Account.class, excludeAuthority);
    try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
      while (records.hasNext()) {
        Account account = null;
        try {
          account = records.next();

          Account accountWithDecryptedLicenseInfo = LicenseUtils.decryptLicenseInfo(account, false);
          LicenseInfo licenseInfo = accountWithDecryptedLicenseInfo.getLicenseInfo();
          if (licenseInfo == null) {
            continue;
          }

          String accountType = licenseInfo.getAccountType();

          if (!AccountType.isValid(accountType)) {
            log.error(
                "LicenseMigration - Invalid accountType {} for account {}", accountType, account.getAccountName());
            continue;
          }

          long expiryTime = licenseInfo.getExpiryTime();
          int licenseUnits = licenseInfo.getLicenseUnits();

          switch (accountType) {
            case AccountType.PAID:
              if (expiryTime <= 0) {
                licenseInfo.setExpiryTime(LicenseUtils.getDefaultPaidExpiryTime());
              }

              if (licenseUnits <= 0) {
                licenseInfo.setLicenseUnits(InstanceLimitProvider.defaults(AccountType.PAID));
              }
              break;

            case AccountType.TRIAL:
              if (expiryTime <= 0) {
                licenseInfo.setExpiryTime(LicenseUtils.getDefaultTrialExpiryTime());
              }

              if (licenseUnits <= 0) {
                licenseInfo.setLicenseUnits(InstanceLimitProvider.defaults(AccountType.TRIAL));
              }
              break;

            case AccountType.COMMUNITY:
              if (expiryTime <= 0) {
                licenseInfo.setExpiryTime(-1L);
              }

              if (licenseUnits <= 0) {
                licenseInfo.setLicenseUnits(InstanceLimitProvider.defaults(AccountType.COMMUNITY));
              }
              break;

            default:
              log.error("Unsupported account type {} for account {}", accountType, account.getAccountName());
              break;
          }

          licenseService.updateAccountLicense(account.getUuid(), licenseInfo);
          log.info("LicenseMigration - Updated license info for account {}", account.getAccountName());
        } catch (Exception ex) {
          log.error("LicenseMigration - Error while updating license info for account: {}",
              account != null ? account.getAccountName() : "", ex);
        }
      }

      log.info("LicenseMigration - Done - Updating license info for all accounts");
    } catch (Exception ex) {
      log.error("LicenseMigration - Failed - Updating license info for all accounts", ex);
    }
  }
}
