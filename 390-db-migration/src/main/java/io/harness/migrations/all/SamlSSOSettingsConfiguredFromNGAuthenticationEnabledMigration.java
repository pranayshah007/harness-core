/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlSettings;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SamlSSOSettingsConfiguredFromNGAuthenticationEnabledMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;

  @Override
  public void migrate() {
    log.info("SSO_SAML_SETTING_MIGRATION: Starting migration of SSO saml settings configured across accounts");
    int countToUpdate = 0;
    int countCorrectlyUpdated = 0;

    try (HIterator<SamlSettings> samlSettingListHIterator =
             new HIterator<>(wingsPersistence.createQuery(SamlSettings.class).filter("type", SSOType.SAML).fetch())) {
      for (SamlSettings samlSetting : samlSettingListHIterator) {
        countToUpdate++;

        if (samlSetting != null) {
          try {
            samlSetting.setConfiguredFromNG(false);
            samlSetting.setFriendlySamlName(
                samlSetting.getDisplayName()); // populate the friendlySamlName to displayName by default, this can be
                                               // updated by customer later
            Account fetchedAccount = accountService.get(samlSetting.getAccountId());
            if (fetchedAccount != null) {
              AuthenticationMechanism authMechanism = fetchedAccount.getAuthenticationMechanism();
              if (AuthenticationMechanism.SAML == authMechanism) {
                samlSetting.setAuthenticationEnabled(true);
              }
            }
            wingsPersistence.save(samlSetting);
            countCorrectlyUpdated++;
            log.info("Successfully updated saml setting {} in account {}", samlSetting.getUuid(),
                samlSetting.getAccountId());
          } catch (Exception exc) {
            log.error(
                "Error while updating saml setting {} for configuredFromNG and authenticationEnabled in account: {}",
                samlSetting.getUuid(), samlSetting.getAccountId(), exc);
          }
        }
      }
    } catch (Exception e) {
      log.error("SSO_SAML_SETTING_MIGRATION: Error in Saml setting migration HIterator step", e);
    } finally {
      log.info("Saml settings configured with failed migration count: {} and finished successfully for count: {}",
          countToUpdate - countCorrectlyUpdated, countCorrectlyUpdated);
    }
    log.info(
        "SSO_SAML_SETTING_MIGRATION: Migration to update saml settings for configuredFromNG and authenticationEnabled finished");
  }
}
