/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.gitsync.gitsyncerror.beans.GitSyncErrorType.CONNECTIVITY_ISSUE;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.dtos.RepoProviders;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError.GitSyncErrorKeys;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class UpdateRepoProviderInConnectivityErrorMigration implements NGMigration {
  private final MongoTemplate mongoTemplate;

  @Override
  public void migrate() {
    try {
      log.info("Started the migration to add the repo providers in connectivity errors");
      updateTheTypeBitbucketInTheRecords();
      updateTheTypeGithubInTheRecords();
      log.info("UpdateTheRepoProvidersInGitSyncErrors Migration completed");
    } catch (Exception ex) {
      log.error("Error occurred while adding the repo provider type", ex);
    }
  }

  private void updateTheTypeBitbucketInTheRecords() {
    log.info("Updating the git sync error records with the type Bitbucket");
    Update updatingRecordWithBitbucketType = update(GitSyncErrorKeys.repoProvider, RepoProviders.BITBUCKET);
    Criteria criteriaForErrorsInBitbucket = Criteria.where(GitSyncErrorKeys.repoUrl)
                                                .regex("/.*bitbucket.*/")
                                                .and(GitSyncErrorKeys.errorType)
                                                .is(CONNECTIVITY_ISSUE)
                                                .and(GitSyncErrorKeys.repoProvider)
                                                .is(null);
    UpdateResult updateResult = mongoTemplate.updateMulti(
        query(criteriaForErrorsInBitbucket), updatingRecordWithBitbucketType, GitSyncError.class);
    log.info("Total {} records updated with value bitbucket", updateResult.getModifiedCount());
  }

  private void updateTheTypeGithubInTheRecords() {
    log.info("Updating the git sync error records with the type Github");
    Update updatingRecordWithGithubType = update(GitSyncErrorKeys.repoProvider, RepoProviders.GITHUB);
    Criteria criteriaForErrorsInGithub = Criteria.where(GitSyncErrorKeys.repoUrl)
                                             .not()
                                             .regex("/.*bitbucket.*/")
                                             .and(GitSyncErrorKeys.errorType)
                                             .is(CONNECTIVITY_ISSUE)
                                             .and(GitSyncErrorKeys.repoProvider)
                                             .is(null);
    UpdateResult updateResultForGithub =
        mongoTemplate.updateMulti(query(criteriaForErrorsInGithub), updatingRecordWithGithubType, GitSyncError.class);
    log.info("Total {} records updated with value github", updateResultForGithub.getModifiedCount());
  }
}
