/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOSettings.SSOSettingsKeys;
import software.wings.beans.sso.SSOType;
import software.wings.dl.WingsPersistence;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Vaibhav Tulsyan
 * 17/May/2019
 * Changes that will be made by this migration:
 *  - Add allowedProviders list. If a user has "GITHUB" in displayName right now, we will add "GITHUB"
 *    to the list allowedProviders
 */
@Slf4j
@Singleton
public class OAuthAllowedProvidersListMigration implements Migration {
  private WingsPersistence wingsPersistence;

  @Inject
  public OAuthAllowedProvidersListMigration(WingsPersistence wingsPersistence) {
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public void migrate() {
    log.info("Started OAuthAllowedProvidersListMigration ...");
    try (HIterator<SSOSettings> ssoSettingsHIterator =
             new HIterator<>(wingsPersistence.createQuery(SSOSettings.class)
                                 .filter(SSOSettingsKeys.type, SSOType.OAUTH.name())
                                 .fetch())) {
      while (ssoSettingsHIterator.hasNext()) {
        SSOSettings ssoSettings = ssoSettingsHIterator.next();
        log.info("Updating SSOSetting with UUID {}", ssoSettings.getUuid());
        String displayNameInUpperCase;
        UpdateOperations<SSOSettings> operations;
        try {
          if (isEmpty(ssoSettings.getDisplayName())) {
            log.warn("Expected displayName to be non-empty");
            continue;
          }
          displayNameInUpperCase = ssoSettings.getDisplayName().toUpperCase();
          operations = wingsPersistence.createUpdateOperations(SSOSettings.class);
        } catch (Exception e) {
          log.error("Failed to create Update Operations for SSOSetting with UUID {}", ssoSettings.getUuid());
          continue;
        }
        try {
          log.info("Setting allowedProviders for SSOSetting with UUID {} to {}", ssoSettings.getUuid(),
              Sets.newHashSet(displayNameInUpperCase));
          operations.set("allowedProviders", Sets.newHashSet(displayNameInUpperCase));
          wingsPersistence.update(ssoSettings, operations);
          log.info("Completed updating SSOSetting with UUID {}", ssoSettings.getUuid());
        } catch (Exception e) {
          log.error("Failed to update SSOSetting with UUID {}", ssoSettings.getUuid());
        }
      }
      log.info("Finished OAuthAllowedProvidersListMigration ...");
    } catch (Exception ex) {
      log.error("OAuthAllowedProvidersListMigration failed.", ex);
    }
  }
}
