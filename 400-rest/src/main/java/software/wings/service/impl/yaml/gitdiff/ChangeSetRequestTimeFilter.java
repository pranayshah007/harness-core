/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.gitdiff;

import static software.wings.beans.yaml.YamlSuccessfulChange.ChangeSource.HARNESS;

import static java.lang.String.format;

import software.wings.beans.yaml.GitFileChange;
import software.wings.beans.yaml.HarnessSuccessFulChangeDetail;
import software.wings.beans.yaml.SuccessfulChangeDetail;
import software.wings.beans.yaml.YamlSuccessfulChange;
import software.wings.service.impl.yaml.gitdiff.YamlFilterResult.YamlFilterResultBuilder;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlSuccessfulChangeService;
import software.wings.yaml.gitSync.YamlChangeSet;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ChangeSetRequestTimeFilter {
  @Inject private YamlSuccessfulChangeService yamlSuccessfulChangeService;
  @Inject private YamlChangeSetService yamlChangeSetService;

  public YamlFilterResult filterFiles(List<GitFileChange> gitFileChangeList, String accountId) {
    log.info("Started applying ChangeRequestTimeFilter for files");
    final YamlFilterResultBuilder filterResultBuilder = YamlFilterResult.builder();
    for (GitFileChange gitFileChange : gitFileChangeList) {
      if (commitTimeMissing(gitFileChange)) {
        filterResultBuilder.filteredFile(gitFileChange);
      } else {
        filterFileWithCommitTime(accountId, gitFileChange, filterResultBuilder);
      }
    }
    log.info("Successfully applied ChangeRequestTimeFilter for files");
    return filterResultBuilder.build();
  }

  private void filterFileWithCommitTime(
      String accountId, GitFileChange gitFileChange, YamlFilterResultBuilder filterResultBuilder) {
    final YamlSuccessfulChange yamlSuccessfulChange =
        yamlSuccessfulChangeService.get(accountId, gitFileChange.getFilePath());

    if (yamlSuccessfulChange == null) {
      filterResultBuilder.filteredFile(gitFileChange);
    } else {
      handleWhenSuccessfulChangeFound(gitFileChange, yamlSuccessfulChange, filterResultBuilder);
    }
  }

  private void handleWhenSuccessfulChangeFound(GitFileChange gitFileChange, YamlSuccessfulChange yamlSuccessfulChange,
      YamlFilterResultBuilder filterResultBuilder) {
    if (!isChangeRequestInOrder(gitFileChange, yamlSuccessfulChange)) {
      handleChangeOutOfOrder(gitFileChange, yamlSuccessfulChange, filterResultBuilder);
    } else if (lastSuccessFulChangeWasHarnessAndYetToProcess(yamlSuccessfulChange)) {
      handleHarnessChangesetNotProcessed(gitFileChange, yamlSuccessfulChange, filterResultBuilder);
    } else {
      filterResultBuilder.filteredFile(gitFileChange);
    }
  }

  private boolean lastSuccessFulChangeWasHarnessAndYetToProcess(YamlSuccessfulChange yamlSuccessfulChange) {
    return lastSuccessfulChangeWasHarness(yamlSuccessfulChange) && !hasHarnessChangesetProcessed(yamlSuccessfulChange);
  }

  private void handleHarnessChangesetNotProcessed(GitFileChange gitFileChange,
      YamlSuccessfulChange yamlSuccessfulChange, YamlFilterResultBuilder filterResultBuilder) {
    final String skipMessage = format("Conflict detected: Override due to change from Harness UI/API at [%s]",
        dateStringInGMT(yamlSuccessfulChange.getChangeRequestTS()));

    final HarnessSuccessFulChangeDetail changeDetail =
        (HarnessSuccessFulChangeDetail) yamlSuccessfulChange.getChangeDetail();
    log.info(" Skipping file [{}] with reason =[{}]. Harness -> git changeset [{}] still in queue.",
        gitFileChange.getFilePath(), skipMessage, changeDetail.getYamlChangeSetId());

    filterResultBuilder.excludedFilePathWithReason(gitFileChange.getFilePath(), skipMessage);
  }

  private boolean hasHarnessChangesetProcessed(YamlSuccessfulChange yamlSuccessfulChange) {
    final YamlChangeSet yamlChangeSet = getYamlChangeSet(yamlSuccessfulChange);
    if (yamlChangeSet != null) {
      log.info("Yamlchangeset id =[{}], status =[{}]", yamlChangeSet.getUuid(), yamlChangeSet.getStatus());
      return YamlChangeSet.terminalStatusList.contains(yamlChangeSet.getStatus());
    }
    return true;
  }

  private YamlChangeSet getYamlChangeSet(YamlSuccessfulChange yamlSuccessfulChange) {
    final SuccessfulChangeDetail changeDetail = yamlSuccessfulChange.getChangeDetail();
    if (changeDetail != null) {
      final HarnessSuccessFulChangeDetail harnessChangeDetail = (HarnessSuccessFulChangeDetail) changeDetail;
      final String yamlChangeSetId = harnessChangeDetail.getYamlChangeSetId();
      if (yamlChangeSetId != null) {
        return yamlChangeSetService.get(yamlSuccessfulChange.getAccountId(), yamlChangeSetId);
      }
    }
    return null;
  }

  private boolean lastSuccessfulChangeWasHarness(YamlSuccessfulChange yamlSuccessfulChange) {
    return HARNESS.name().equals(yamlSuccessfulChange.getChangeSource());
  }

  private void handleChangeOutOfOrder(GitFileChange gitFileChange, YamlSuccessfulChange yamlSuccessfulChange,
      YamlFilterResultBuilder filterResultBuilder) {
    final String skipMessage = format(
        "Conflict detected: File change request time [%s] is less than last successfully processed change request time [%s]",
        dateStringInGMT(gitFileChange.getCommitTimeMs()), dateStringInGMT(yamlSuccessfulChange.getChangeRequestTS()));
    log.info(" Skipping file [{}] with reason =[{}]. last Successful change detail = [{}] ",
        gitFileChange.getFilePath(), skipMessage, yamlSuccessfulChange.getChangeDetail());
    filterResultBuilder.excludedFilePathWithReason(gitFileChange.getFilePath(), skipMessage);
  }

  private boolean isChangeRequestInOrder(GitFileChange gitFileChange, YamlSuccessfulChange yamlSuccessfulChange) {
    return yamlSuccessfulChange.getChangeRequestTS() <= gitFileChange.getCommitTimeMs();
  }

  private boolean commitTimeMissing(GitFileChange gitFileChange) {
    return gitFileChange.getCommitTimeMs() == null;
  }

  private String dateStringInGMT(long timestamp) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
    sdf.setTimeZone(TimeZone.getTimeZone(TimeZone.getTimeZone("Etc/UTC").toZoneId()));
    final Date date = new Date();
    date.setTime(timestamp);
    return sdf.format(date);
  }
}
