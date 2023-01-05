/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.GitCommit;
import software.wings.beans.GitCommit.GitCommitKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitSyncError.GitSyncErrorKeys;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AddCommitTimeToGitSyncError implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  private static final List<String> NULL_AND_EMPTY = Arrays.asList(null, "");
  @Inject private GitSyncErrorService gitSyncErrorService;

  private Table<String, String, GitCommit> gitCommitTable = HashBasedTable.create();

  @Override
  public void migrate() {
    log.info("Running migration AddCommitTimeToGitSyncError");

    Query<GitSyncError> query = wingsPersistence.createAuthorizedQuery(GitSyncError.class)
                                    .field(GitSyncErrorKeys.gitCommitId)
                                    .notIn(NULL_AND_EMPTY);

    try (HIterator<GitSyncError> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        GitSyncError syncError = records.next();
        try {
          if (isNotEmpty(syncError.getGitCommitId()) && syncError.getCommitTime() == null) {
            Long commitCreatedAtTime = getCreationTimeOfCommit(syncError.getAccountId(), syncError.getGitCommitId());
            if (commitCreatedAtTime != null) {
              syncError.setCommitTime(commitCreatedAtTime);
            } else {
              log.info("The createdAt value is null for the commit {} in account", syncError.getUuid(),
                  syncError.getAccountId());
              syncError.setCommitTime(syncError.getLastUpdatedAt());
            }
          }
          wingsPersistence.save(syncError);
        } catch (Exception e) {
          log.error("Error while processing gitsyncerror id =" + syncError.getUuid(), e);
        }
      }
    }
    log.info("Completed migration:  AddCommitTimeToGitSyncError");
  }

  private Long getCreationTimeOfCommit(String accountId, String gitCommitId) {
    GitCommit gitCommit = gitCommitTable.get(accountId, gitCommitId);
    if (gitCommit == null) {
      gitCommit = wingsPersistence.createQuery(GitCommit.class)
                      .filter(GitCommitKeys.accountId, accountId)
                      .filter(GitCommitKeys.commitId, gitCommitId)
                      .project(GitCommitKeys.createdAt, true)
                      .get();
      if (gitCommit == null) {
        log.info("The gitCommitId {} was not found in the db for the account {}", gitCommitId, accountId);
        return null;
      }
      gitCommitTable.put(accountId, gitCommitId, gitCommit);
    }
    return gitCommit.getCreatedAt();
  }
}
