/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.GIT_TO_HARNESS;
import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.HARNESS_TO_GIT;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.migrations.OnPrimaryManagerMigration;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitToHarnessErrorDetails;
import software.wings.yaml.errorhandling.HarnessToGitErrorDetails;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RefactorTheFieldsInGitSyncError implements OnPrimaryManagerMigration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private GitSyncErrorService gitSyncErrorService;

  @Override
  public void migrate() {
    log.info("Running migration RefactorTheFieldsInGitSyncError");
    Query<GitSyncError> query = wingsPersistence.createQuery(GitSyncError.class);

    try (HIterator<GitSyncError> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        GitSyncError syncError = records.next();
        try {
          // Is the record already being processed ? If yes then skip
          if (isBlank(syncError.getGitSyncDirection())) {
            GitSyncError updatedGitSyncError = populateNewGitSyncFields(syncError);
            wingsPersistence.save(updatedGitSyncError);
          }
        } catch (Exception e) {
          log.error("Error while processing git sync error id =" + syncError.getUuid(), e);
        }
      }
    }

    try {
      wingsPersistence.getCollection(DEFAULT_STORE, "gitSyncError").dropIndex("uniqueIdx");
      log.info("Successfully  dropped the index");
    } catch (RuntimeException ex) {
      log.error("Drop index error", ex);
    }

    log.info("Completed migration RefactorTheFieldsInGitSyncError");
  }

  private GitSyncError populateNewGitSyncFields(GitSyncError gitSyncError) {
    GitSyncError updatedGitSyncError = GitSyncError.builder()
                                           .yamlFilePath(gitSyncError.getYamlFilePath())
                                           .accountId(gitSyncError.getAccountId())
                                           .changeType(gitSyncError.getChangeType())
                                           .branchName(gitSyncError.getBranchName())
                                           .repositoryName(gitSyncError.getRepositoryName())
                                           .gitConnectorId(gitSyncError.getGitConnectorId())
                                           .yamlGitConfigId(gitSyncError.getYamlGitConfigId())
                                           .failureReason(gitSyncError.getFailureReason())
                                           .status(gitSyncError.getStatus())
                                           .build();
    updatedGitSyncError.setUuid(gitSyncError.getUuid());
    updatedGitSyncError.setAppId(gitSyncError.getAppId());
    updatedGitSyncError.setCreatedAt(gitSyncError.getCreatedAt());
    if (isBlank(gitSyncError.getGitCommitId())) {
      // Its a harnessToGit Error
      // New fields to be added gitSyncDirection,additionalErrorDetails
      updatedGitSyncError.setGitSyncDirection(HARNESS_TO_GIT.toString());
      HarnessToGitErrorDetails harnessToGitErrorDetails =
          HarnessToGitErrorDetails.builder().fullSyncPath(gitSyncError.isFullSyncPath()).build();
      updatedGitSyncError.setAdditionalErrorDetails(harnessToGitErrorDetails);
    } else {
      // Its a gitToHarness Error
      // New fields to be added gitToHarnessErrorDetails
      updatedGitSyncError.setGitSyncDirection(GIT_TO_HARNESS.toString());
      Long commitTime = gitSyncError.getCommitTime();
      if (commitTime == null || commitTime.equals(0L)) {
        log.info(
            "The commitTime field was not existing correctly for the gitSyncError with id {}", gitSyncError.getUuid());
      }

      String yamlContent = gitSyncError.getYamlContent();
      if (isBlank(yamlContent)) {
        log.info(
            "The yamlcontent field was not existing correctly for the gitSyncError with id {}", gitSyncError.getUuid());
      }
      GitToHarnessErrorDetails gitToHarnessErrorDetails = GitToHarnessErrorDetails.builder()
                                                              .gitCommitId(gitSyncError.getGitCommitId())
                                                              .yamlContent(yamlContent)
                                                              .commitTime(commitTime)
                                                              .build();
      updatedGitSyncError.setAdditionalErrorDetails(gitToHarnessErrorDetails);
    }
    return updatedGitSyncError;
  }
}
