/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.execution.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.ExecutionInfoUtility;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.executions.StageExecutionInfoRepository;
import io.harness.utils.StageStatus;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class StageExecutionInfoServiceImpl implements StageExecutionInfoService {
  private static final int STAGE_STATUS_KEY_LOCKS_EXPIRE_TIME_HOURS = 1;
  private static final int STAGE_STATUS_KEY_LOCKS_MAXIMUM_SIZE = 5000;
  private static final int LATEST_ITEM_LIMIT = 1;

  // LoadingCache map implementations are thread safe
  private static final LoadingCache<String, Boolean> stageStatusKeyLocks =
      CacheBuilder.newBuilder()
          .expireAfterAccess(STAGE_STATUS_KEY_LOCKS_EXPIRE_TIME_HOURS, TimeUnit.HOURS)
          .maximumSize(STAGE_STATUS_KEY_LOCKS_MAXIMUM_SIZE)
          .build(CacheLoader.from(Boolean::new));

  private StageExecutionInfoRepository stageExecutionInfoRepository;

  @Override
  public StageExecutionInfo save(@Valid @NotNull StageExecutionInfo stageExecutionInfo) {
    return stageExecutionInfoRepository.save(stageExecutionInfo);
  }

  @Override
  public void updateStatus(@NotNull Scope scope, @NotNull final String stageExecutionId, StageStatus stageStatus) {
    if (isEmpty(stageExecutionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }

    UpdateResult updateResult = stageExecutionInfoRepository.updateStatus(scope, stageExecutionId, stageStatus);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Unable to update stage execution status, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, executionId: %s, stageStatus: %s",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stageExecutionId,
          stageStatus));
    }
  }

  @Override
  public void update(Scope scope, String stageExecutionId, Map<String, Object> updates) {
    UpdateResult updateResult = stageExecutionInfoRepository.update(scope, stageExecutionId, updates);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException("Unable to update StageExecutionInfo");
    }
  }

  public void updateOnce(Scope scope, String stageExecutionId, Map<String, Object> updates) {
    // if some other thread already updated DB collection based on stage status key, skip update collection again
    String stageExecutionKey = ExecutionInfoUtility.buildStageStatusKey(scope, stageExecutionId);
    Boolean previousValue = stageStatusKeyLocks.asMap().put(stageExecutionKey, true);
    if (previousValue != null) {
      return;
    }

    UpdateResult updateResult = stageExecutionInfoRepository.update(scope, stageExecutionId, updates);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException("Unable to update StageExecutionInfo");
    }
  }

  public void deleteStageStatusKeyLock(Scope scope, String stageExecutionId) {
    String stageExecutionKey = ExecutionInfoUtility.buildStageStatusKey(scope, stageExecutionId);
    log.info("Deleting stage execution key, stageExecutionKey: {}, stageStatusKeyLocksSize:{}", stageExecutionKey,
        stageStatusKeyLocks.size());

    stageStatusKeyLocks.asMap().remove(stageExecutionKey);
  }

  public Optional<StageExecutionInfo> getLatestSuccessfulStageExecutionInfo(
      @NotNull @Valid ExecutionInfoKey executionInfoKey, @NotNull final String executionId) {
    if (isEmpty(executionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }

    List<StageExecutionInfo> latestSucceededStageExecutionInfo =
        stageExecutionInfoRepository.listSucceededStageExecutionNotIncludeCurrent(
            executionInfoKey, executionId, LATEST_ITEM_LIMIT);
    return isEmpty(latestSucceededStageExecutionInfo) ? Optional.empty()
                                                      : Optional.ofNullable(latestSucceededStageExecutionInfo.get(0));
  }

  public List<StageExecutionInfo> listLatestSuccessfulStageExecutionInfo(
      @NotNull @Valid ExecutionInfoKey executionInfoKey, @NotNull final String stageExecutionId, int limit) {
    if (isEmpty(stageExecutionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }
    return stageExecutionInfoRepository.listSucceededStageExecutionNotIncludeCurrent(
        executionInfoKey, stageExecutionId, limit);
  }
}
