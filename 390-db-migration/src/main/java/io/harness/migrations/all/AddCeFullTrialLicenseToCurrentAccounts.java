/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.ccm.license.CeLicenseInfo;
import io.harness.ccm.license.CeLicenseType;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.dl.WingsPersistence;
import software.wings.licensing.LicenseService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddCeFullTrialLicenseToCurrentAccounts implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private LicenseService licenseService;

  @Override
  public void migrate() {
    log.info("License Migration - Start adding CE Full Trial licenses for existing accounts");

    CeLicenseInfo fullTrialLicense = CeLicenseInfo.builder()
                                         .licenseType(CeLicenseType.FULL_TRIAL)
                                         .expiryTime(CeLicenseType.FULL_TRIAL.getDefaultExpiryTime())
                                         .build();

    Map<String, String> paidCeAccountExpiryMap = ImmutableMap.<String, String>builder()
                                                     .put("g9Nw8fnOSYGlr4H9QYWJww", "06/04/2021")
                                                     .put("R7OsqSbNQS69mq74kMNceQ", "05/15/2023")
                                                     .put("8VwWgE0WRK67_PWDpkooNA", "07/26/2021")
                                                     .put("BpYJcC5sR76ag3to4FbubQ", "09/30/2020")
                                                     .put("WhejVM7NTJe2fZ99Pdo2YA", "07/15/2021")
                                                     .put("hW63Ny6rQaaGsKkVjE0pJA", "08/20/2020")
                                                     .put("TlKfvX4wQNmRmxkZrPXEgQ", "10/30/2020")
                                                     .put("NVsV7gjbTZyA3CgSgXNOcg", "07/30/2023")
                                                     .build();

    Query<Account> accountsQuery = wingsPersistence.createQuery(Account.class, excludeAuthority)
                                       .field(AccountKeys.cloudCostEnabled)
                                       .equal(Boolean.TRUE);
    try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
      while (records.hasNext()) {
        Account account = records.next();
        try {
          if (account.isCloudCostEnabled() && account.getCeLicenseInfo() == null) {
            if (paidCeAccountExpiryMap.containsKey(account.getUuid())) {
              CeLicenseInfo paidCeLicenseInfo =
                  CeLicenseInfo.builder()
                      .licenseType(CeLicenseType.PAID)
                      .expiryTime(getExpiryTime(paidCeAccountExpiryMap.get(account.getUuid())))
                      .build();
              licenseService.updateCeLicense(account.getUuid(), paidCeLicenseInfo);

            } else {
              licenseService.updateCeLicense(account.getUuid(), fullTrialLicense);
            }
          }
        } catch (Exception ex) {
          log.error("Error while adding CE license for account {}", account.getUuid(), ex);
        }
      }
    }
    log.info("License Migration - Completed adding CE Full Trial licenses for existing accounts");
  }

  private long getExpiryTime(String expiryDate) {
    return LocalDate.parse(expiryDate, DateTimeFormatter.ofPattern("MM/dd/yyyy"))
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli();
  }
}
