/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.service;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.YamlChangeSet;
import io.harness.gitsync.common.beans.YamlChangeSetStatus;
import io.harness.gitsync.core.dtos.YamlChangeSetDTO;
import io.harness.gitsync.core.dtos.YamlChangeSetSaveDTO;
import io.harness.gitsync.core.runnable.ChangeSetGroupingKey;
import io.harness.validation.Create;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.data.mongodb.core.query.Criteria;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(DX)
public interface YamlChangeSetService {
  @ValidationGroups(Create.class) YamlChangeSetDTO save(@Valid YamlChangeSetSaveDTO yamlChangeSet);

  Optional<YamlChangeSetDTO> get(@NotNull String accountId, @NotNull String changeSetId);

  Optional<YamlChangeSetDTO> peekQueueHead(
      @NotNull String accountId, @NotNull String queueKey, @NotNull YamlChangeSetStatus yamlChangeSetStatus);

  boolean changeSetExistsFoQueueKey(@NotNull String accountId, @NotNull String queueKey,
      @Size(min = 1) List<YamlChangeSetStatus> yamlChangeSetStatuses);

  int countByAccountIdAndStatus(@NotNull String accountId, List<YamlChangeSetStatus> yamlChangeSetStatuses);

  boolean updateStatus(@NotNull String accountId, @NotNull String changeSetId, @NotNull YamlChangeSetStatus newStatus);

  boolean markSkippedWithMessageCode(@NotNull String accountId, @NotNull String changeSetId, String messageCode);

  boolean updateStatusForGivenYamlChangeSets(@NotNull String accountId, @NotNull YamlChangeSetStatus newStatus,
      List<YamlChangeSetStatus> currentStatuses, List<String> yamlChangeSetIds);

  boolean updateStatusAndCutoffTime(String accountId, String changeSetId, YamlChangeSetStatus newStatus);

  void markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(@NotNull String accountId, int maxRetryCount);

  boolean updateStatusWithRetryCountIncrement(@NotNull String accountId, @NotNull YamlChangeSetStatus currentStatus,
      @NotNull YamlChangeSetStatus newStatus, @NotNull String yamlChangeSetId);

  boolean updateStatusAndIncrementRetryCountForYamlChangeSets(@NotNull String accountId,
      @NotNull YamlChangeSetStatus newStatus, List<YamlChangeSetStatus> currentStatus, List<String> yamlChangeSetIds);

  List<YamlChangeSetDTO> findByAccountIdsStatusCutoffLessThan(
      List<String> runningAccountIdList, @Size(min = 1) List<YamlChangeSetStatus> yamlChangeSetStatuses, long timeout);

  List<String> findDistinctAccountIdsByStatus(List<YamlChangeSetStatus> status);

  UpdateResult updateYamlChangeSetsToNewStatusWithMessageCodeAndCreatedAtLessThan(
      @NotNull YamlChangeSetStatus oldStatus, @NotNull YamlChangeSetStatus newStatus, long timeout, String messageCode);

  Set<ChangeSetGroupingKey> getChangesetGroupingKeys(Criteria criteria);

  List<YamlChangeSet> list(@NotNull String queueKey, @NotNull String accountId, @NotNull YamlChangeSetStatus status);

  void markQueuedYamlChangeSetsWithMaxRetriesAsSkipped(int maxRetryCount);

  void deleteByAccount(String accountId);
}
