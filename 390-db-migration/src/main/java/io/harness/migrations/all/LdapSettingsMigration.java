/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.security.UserGroup;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.SSOType;
import software.wings.dl.WingsPersistence;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

/**
 * Prior this migration for each LDAPSetting we were supporting
 * on User Query filter.
 *
 * Now we will need to support multiple User and Group Query filters.
 *
 * THis Migration will convert single LDAP users & group query to
 * list of user & group queries.
 *
 */
@Slf4j
public class LdapSettingsMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Starting LdapSettingsMigration for all eligible accountIds");

    UpdateOperations<UserGroup> operations = wingsPersistence.createUpdateOperations(UserGroup.class);
    try (HIterator<LdapSettings> ldapSettingsHIterator =
             new HIterator<>(wingsPersistence.createQuery(LdapSettings.class).filter("type", SSOType.LDAP).fetch())) {
      for (LdapSettings ldapSettings : ldapSettingsHIterator) {
        if (ldapSettings.getUserSettings() != null && CollectionUtils.isEmpty(ldapSettings.getUserSettingsList())) {
          log.info("Migrating Ldap UserSettings for accountId: {}", ldapSettings.getAccountId());
          ldapSettings.setUserSettingsList(Lists.newArrayList(ldapSettings.getUserSettings()));
        }
        if (ldapSettings.getGroupSettings() != null && CollectionUtils.isEmpty(ldapSettings.getGroupSettingsList())) {
          log.info("Migrating Ldap GroupSettings for accountId: {}", ldapSettings.getAccountId());
          ldapSettings.setGroupSettingsList(Lists.newArrayList(ldapSettings.getGroupSettings()));
        }
        wingsPersistence.save(ldapSettings);
      }
    }
  }
}
