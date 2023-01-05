/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.mongo.MongoUtils.setUnset;

import io.harness.migrations.Migration;

import software.wings.beans.GitCommit;
import software.wings.beans.GitCommit.GitCommitKeys;
import software.wings.beans.GitCommit.Status;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GitCommitStatusMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Running migration GitCommitStatusMigration");

    try {
      UpdateOperations<GitCommit> ops = wingsPersistence.createUpdateOperations(GitCommit.class);
      setUnset(ops, GitCommitKeys.status, Status.COMPLETED_WITH_ERRORS);

      Query<GitCommit> gitCommitQuery =
          wingsPersistence.createQuery(GitCommit.class).filter(GitCommitKeys.status, Status.FAILED);

      final UpdateResults updateResults = wingsPersistence.update(gitCommitQuery, ops);
      log.info("update results = {}", updateResults);
    } catch (Exception e) {
      log.error("Error running migration GitCommitStatusMigration", e);
    }
    log.info("Completed migration:  GitSyncErrorGitDetailsMigration");
  }
}
