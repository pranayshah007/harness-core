/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.gitsyncerror.beans.GitSyncError;
import io.harness.migration.NGMigration;
import io.harness.repositories.gitSyncError.GitSyncErrorRepository;
import io.harness.utils.FilePathUtils;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class GitSyncErrorCompleteFilePathMigration implements NGMigration {
  private final MongoTemplate mongoTemplate;
  private final GitSyncErrorRepository gitSyncErrorRepository;

  @Override
  public void migrate() {
    int pageIdx = 0;
    int pageSize = 20;
    int maxGitErrors = 35000;
    int maxPages = maxGitErrors / pageSize;
    int totalErrors = 0;

    while (pageIdx < maxPages) {
      Pageable pageable = PageRequest.of(pageIdx, pageSize);
      Query query = new Query().with(pageable);
      List<GitSyncError> gitSyncErrorList = mongoTemplate.find(query, GitSyncError.class);
      if (isEmpty(gitSyncErrorList)) {
        break;
      }

      try {
        updateCompleteFilePath(gitSyncErrorList);
        gitSyncErrorRepository.saveAll(gitSyncErrorList);
      } catch (DuplicateKeyException ex) {
        // this would happen when migration is run for the second time
        log.error("GitSyncError Migration failed", ex);
      } catch (Exception ex) {
        log.error("Couldn't update completeFilePath", ex);
      }
      totalErrors += gitSyncErrorList.size();
      pageIdx++;
      if (pageIdx % (maxPages / 5) == 0) {
        log.info("GitSyncError migration in process...");
      }
    }

    log.info("GitSyncError Migration Completed");
    log.info("Total {} gitSyncErrors updated", totalErrors);
  }

  private void updateCompleteFilePath(List<GitSyncError> gitSyncErrorList) {
    for (GitSyncError gitSyncError : gitSyncErrorList) {
      if (isNotEmpty(gitSyncError.getCompleteFilePath()) && gitSyncError.getCompleteFilePath().charAt(0) != '/') {
        String newFilePath = FilePathUtils.updatePathWithForwardSlash(gitSyncError.getCompleteFilePath());
        gitSyncError.setCompleteFilePath(newFilePath);
      }
    }
  }
}
