/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ArtifactSourceCollectionEnabledMigration implements Migration {
  private static final String DEBUG_LOG = "[ARTIFACTSOURCECOLLECTIONENABLEDMIGRATION]: ";
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info(DEBUG_LOG + "Migration of artifact source collection enabled started");
    try (HIterator<Account> accounts =
             new HIterator<>(wingsPersistence.createAnalyticsQuery(Account.class, excludeAuthority)
                                 .project(Account.ID_KEY2, true)
                                 .fetch())) {
      while (accounts.hasNext()) {
        Account account = accounts.next();
        log.info(DEBUG_LOG + "Doing artifact source migration for accountId: " + account.getUuid());
        changeNullCollectionEnabledForAccountToTrue(account.getUuid());
      }
    }
  }

  private void changeNullCollectionEnabledForAccountToTrue(String accountId) {
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class)
                                      .filter(ArtifactStreamKeys.accountId, accountId)
                                      .filter(ArtifactStreamKeys.collectionEnabled, null);
    UpdateOperations<ArtifactStream> updateOperations =
        wingsPersistence.createUpdateOperations(ArtifactStream.class).set(ArtifactStreamKeys.collectionEnabled, true);

    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);
    log.info(String.format(
        "%s Updated %s artifact sources collection enabled null to true", DEBUG_LOG, updateResults.getUpdatedCount()));
  }
}
