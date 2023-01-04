/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.beans.yaml.YamlSuccessfulChange.ChangeSource.GIT;
import static software.wings.beans.yaml.YamlSuccessfulChange.ChangeSource.HARNESS;

import io.harness.logging.AccountLogContext;

import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.GitSuccessFulChangeDetail;
import software.wings.beans.yaml.HarnessSuccessFulChangeDetail;
import software.wings.beans.yaml.YamlSuccessfulChange;
import software.wings.beans.yaml.YamlSuccessfulChange.YamlSuccessfulChangeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.YamlSuccessfulChangeService;
import software.wings.yaml.gitSync.YamlChangeSet;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Query;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

@Singleton
@Slf4j
public class YamlSuccessfulChangeServiceImpl implements YamlSuccessfulChangeService {
  private final WingsPersistence wingsPersistence;

  @Inject
  public YamlSuccessfulChangeServiceImpl(WingsPersistence wingsPersistence) {
    this.wingsPersistence = wingsPersistence;
  }

  @Override
  public String upsert(YamlSuccessfulChange yamlSuccessfulChange) {
    final Query<YamlSuccessfulChange> findQuery =
        newFindQuery(yamlSuccessfulChange.getAccountId(), yamlSuccessfulChange.getYamlFilePath());
    // as the race condition would be minimum, so using delete and save for upsert
    wingsPersistence.delete(findQuery);
    return wingsPersistence.save(yamlSuccessfulChange);
  }

  private Query<YamlSuccessfulChange> newFindQuery(String accountId, String filePath) {
    return wingsPersistence.createQuery(YamlSuccessfulChange.class)
        .filter(YamlSuccessfulChangeKeys.accountId, accountId)
        .filter(YamlSuccessfulChangeKeys.yamlFilePath, filePath);
  }

  @Override
  public void updateOnHarnessChangeSet(YamlChangeSet savedYamlChangeset) {
    if (harnessToGitChange(savedYamlChangeset)) {
      try (AccountLogContext ignore = new AccountLogContext(savedYamlChangeset.getAccountId(), OVERRIDE_ERROR)) {
        log.info("updating SuccessfulChange for  harness -> git changeset [{}]", savedYamlChangeset.getUuid());
        for (GitFileChange gitFileChange : ListUtils.emptyIfNull(savedYamlChangeset.getGitFileChanges())) {
          final YamlSuccessfulChange yamlSuccessfulChange =
              YamlSuccessfulChange.builder()
                  .accountId(savedYamlChangeset.getAccountId())
                  .yamlFilePath(gitFileChange.getFilePath())
                  .changeRequestTS(System.currentTimeMillis())
                  .changeProcessedTS(System.currentTimeMillis())
                  .changeSource(HARNESS.name())
                  .changeDetail(
                      HarnessSuccessFulChangeDetail.builder().yamlChangeSetId(savedYamlChangeset.getUuid()).build())
                  .build();
          final String updatedId = upsert(yamlSuccessfulChange);
          log.info(
              "updated SuccessfulChange for file = [{}], changeRequestTS =[{}], changeProcessedTS=[{}], uuid= [{}] ",
              yamlSuccessfulChange.getYamlFilePath(), yamlSuccessfulChange.getChangeRequestTS(),
              yamlSuccessfulChange.getChangeProcessedTS(), updatedId);
        }
        log.info("updated SuccessfulChange for  harness -> git changeset [{}]", savedYamlChangeset.getUuid());
      }
    }
  }

  @Override
  public void updateOnSuccessfulGitChangeProcessing(GitFileChange gitFileChange, String accountId) {
    try (AccountLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("updating SuccessfulChange from git for  file [{}]", gitFileChange.getFilePath());
      final YamlSuccessfulChange yamlSuccessfulChange =
          YamlSuccessfulChange.builder()
              .accountId(accountId)
              .yamlFilePath(gitFileChange.getFilePath())
              .changeRequestTS(gitFileChange.getCommitTimeMs())
              .changeProcessedTS(System.currentTimeMillis())
              .changeSource(GIT.name())
              .changeDetail(GitSuccessFulChangeDetail.builder()
                                .commitId(gitFileChange.getCommitId())
                                .processingCommitId(gitFileChange.getProcessingCommitId())
                                .build())
              .build();
      final String updatedId = upsert(yamlSuccessfulChange);
      log.info("updated SuccessfulChange for file = [{}], changeRequestTS =[{}], changeProcessedTS=[{}], uuid= [{}] ",
          yamlSuccessfulChange.getYamlFilePath(), yamlSuccessfulChange.getChangeRequestTS(),
          yamlSuccessfulChange.getChangeProcessedTS(), updatedId);

      log.info("updated SuccessfulChange from git for  file [{}]", gitFileChange.getFilePath());
    }
  }

  @Override
  public YamlSuccessfulChange get(String accountId, String filePath) {
    return newFindQuery(accountId, filePath).get();
  }

  private boolean harnessToGitChange(YamlChangeSet savedYamlChangeset) {
    return !savedYamlChangeset.isGitToHarness();
  }
}
