/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import io.harness.migrations.Migration;

import software.wings.dl.WingsPersistence;
import software.wings.yaml.gitSync.GitFileActivity;
import software.wings.yaml.gitSync.GitFileActivity.GitFileActivityKeys;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeleteGitActivityWithoutProcCommitIdMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try {
      wingsPersistence.getCollection(DEFAULT_STORE, "gitFileActivity").dropIndex("uniqueIdx_1");
    } catch (RuntimeException ex) {
      log.error("Drop index error", ex);
    }

    try {
      Query<GitFileActivity> query = wingsPersistence.createQuery(GitFileActivity.class)
                                         .field(GitFileActivityKeys.processingCommitId)
                                         .doesNotExist();

      wingsPersistence.delete(query);
    } catch (Exception ex) {
      log.error("Error while deleting activities not having processingCommitId field", ex);
    }
  }
}
