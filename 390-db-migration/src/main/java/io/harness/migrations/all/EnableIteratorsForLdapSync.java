/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.persistence.HPersistence.returnNewOptions;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;
import io.harness.mongo.MongoUtils;
import io.harness.persistence.HIterator;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettings.LdapSettingsKeys;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOSettings.SSOSettingsKeys;
import software.wings.beans.sso.SSOType;
import software.wings.dl.WingsPersistence;
import software.wings.scheduler.LdapSyncJobConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(PL)
public class EnableIteratorsForLdapSync implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  private static final String LOG_IDENTIFIER = "[ADD_LDAP_SETTINGS_TO_USE_ITERATOR_FRAMEWORK]: ";
  public static final String GROUP = "LDAP_GROUP_SYNC_CRON_JOB";
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Inject private LdapSyncJobConfig ldapSyncJobConfig;

  @Override
  public void migrate() {
    log.info(LOG_IDENTIFIER + "Running migration to add enableNextIterations to ldap setting config");
    int count = 0;
    try (
        HIterator<LdapSettings> settingsHIterator = new HIterator<>(
            wingsPersistence.createQuery(LdapSettings.class).field(SSOSettingsKeys.type).equal(SSOType.LDAP).fetch())) {
      while (settingsHIterator.hasNext()) {
        try {
          migrateLdapSettings(settingsHIterator.next());
        } catch (Exception e) {
          log.error(LOG_IDENTIFIER + "Failed to catch the migration", e);
        }
        count++;
      }
    } catch (Exception e) {
      log.error(LOG_IDENTIFIER + "Could not run migration to add enableNextIterations to ldap settings ", e);
    }
    log.info(LOG_IDENTIFIER + "Updated {} ldap setting records", count);
  }

  private void migrateLdapSettings(LdapSettings ldapSettings) {
    try {
      log.info(LOG_IDENTIFIER + "Updating ldapSettings with id {} for accountId {}", ldapSettings.getUuid(),
          ldapSettings.getAccountId());
      // Uncomment below in next release after prod verification has been done
      // jobScheduler.pauseJob(ldapSettings.getUuid(), GROUP);

      ldapSettings.setNextIterations(new ArrayList<>());
      ldapSettings.setCronExpression(ldapSyncJobConfig.getDefaultCronExpression());
      List<Long> nextFireTime = ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
      // Update the current ldapSettings to use the IteratorsFramework
      Query<LdapSettings> query = wingsPersistence.createQuery(LdapSettings.class)
                                      .filter(SSOSettings.UUID_KEY, ldapSettings.getUuid())
                                      .filter(SSOSettings.ACCOUNT_ID_KEY, ldapSettings.getAccountId());

      UpdateOperations<LdapSettings> operations = wingsPersistence.createUpdateOperations(LdapSettings.class);
      MongoUtils.setUnset(operations, SSOSettingsKeys.nextIterations, nextFireTime);
      MongoUtils.setUnset(operations, LdapSettingsKeys.cronExpression, ldapSyncJobConfig.getDefaultCronExpression());
      wingsPersistence.findAndModify(query, operations, returnNewOptions);
      log.info(LOG_IDENTIFIER + "Updated ldapSettings with id {} for accountId {}", ldapSettings.getUuid(),
          ldapSettings.getAccountId());
    } catch (Exception e) {
      log.error(LOG_IDENTIFIER + "Could not update ldapSettings with id " + ldapSettings.getUuid() + " for accountId "
              + ldapSettings.getAccountId(),
          e);

      // Uncomment below in next release after prod verification has been done
      // jobScheduler.resumeJob(ldapSettings.getUuid(), GROUP);
    }
  }
}
