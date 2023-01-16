/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.event.handler.impl.MarketoHelper;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.migrations.Migration;
import io.harness.network.Http;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.beans.LicenseInfo;
import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.licensing.LicenseService;
import software.wings.service.impl.LicenseUtils;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

/**
 * Migration script to register all the existing users of trial accounts as marketo leads
 *
 * @author rktummala on 11/02/18
 */
@Slf4j
public class MarketoLeadDataMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UserService userService;
  @Inject private LicenseService licenseService;
  @Inject private MarketoConfig marketoConfig;
  @Inject private MarketoHelper marketoHelper;

  @Override
  public void migrate() {
    if (!marketoConfig.isEnabled()) {
      log.info("MarketoMigration - Marketo config is disabled. skipping...");
      return;
    }

    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(marketoConfig.getUrl())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .client(Http.getUnsafeOkHttpClient(marketoConfig.getUrl()))
                            .build();

    log.info("MarketoMigration - Start - registering all users of trial accounts as leads");

    String accessToken;
    try {
      accessToken =
          marketoHelper.getAccessToken(marketoConfig.getClientId(), marketoConfig.getClientSecret(), retrofit);
    } catch (IOException e) {
      log.error("MarketoMigration - Error while getting the access token", e);
      return;
    }
    Query<Account> accountsQuery = wingsPersistence.createQuery(Account.class, excludeAuthority);
    try (HIterator<Account> records = new HIterator<>(accountsQuery.fetch())) {
      while (records.hasNext()) {
        Account account = null;
        try {
          account = records.next();

          if (account == null) {
            continue;
          }

          Account accountWithDecryptedLicenseInfo = LicenseUtils.decryptLicenseInfo(account, false);
          LicenseInfo licenseInfo = accountWithDecryptedLicenseInfo.getLicenseInfo();
          if (licenseInfo == null) {
            continue;
          }

          String accountType = licenseInfo.getAccountType();

          if (!AccountType.isValid(accountType)) {
            log.error(
                "MarketoMigration - Invalid accountType {} for account {}", accountType, account.getAccountName());
            continue;
          }

          if (!AccountType.TRIAL.equals(accountType)) {
            continue;
          }

          final String accountId = account.getUuid();
          final Account finalAccount = account;
          List<User> usersOfAccount = userService.getUsersOfAccount(accountId);
          usersOfAccount.stream().filter(user -> user.getMarketoLeadId() == 0L).forEach(user -> {
            try {
              marketoHelper.createOrUpdateLead(
                  finalAccount, user.getName(), user.getEmail(), accessToken, null, retrofit, null);
            } catch (IOException e) {
              log.error("MarketoMigration - Error while registering lead for user {} in account: {}", user.getUuid(),
                  accountId, e);
            } catch (URISyntaxException e) {
              log.error("MarketoMigration - Error while registering lead for user {} in account: {}", user.getUuid(),
                  accountId, e);
            }
          });

          log.info("MarketoMigration - Created leads for account {}", account.getAccountName());
        } catch (Exception ex) {
          log.error("MarketoMigration - Error while updating license info for account: {}",
              account != null ? account.getAccountName() : "", ex);
        }
      }

      log.info("MarketoMigration - Done - registering all users of trial accounts as leads");
    } catch (Exception ex) {
      log.error("MarketoMigration - Failed - registering all users of trial accounts as leads", ex);
    }
  }
}
