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
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.executions.StageExecutionInfoRepository;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class StageExecutionInfoServiceImpl implements StageExecutionInfoService {
  private static final int LATEST_ITEM_LIMIT = 1;

  private StageExecutionInfoRepository stageExecutionInfoRepository;

  @Override
  public StageExecutionInfo save(@Valid @NotNull StageExecutionInfo stageExecutionInfo) {
    return stageExecutionInfoRepository.save(stageExecutionInfo);
  }

  @Override
  public void updateStatus(@NotNull Scope scope, @NotNull final String executionId, StageStatus stageStatus) {
    if (isEmpty(executionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }

    UpdateResult updateResult = stageExecutionInfoRepository.updateStatus(scope, executionId, stageStatus);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(format(
          "Unable to update stage execution status, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, executionId: %s, stageStatus: %s",
          scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), executionId,
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
      @NotNull @Valid ExecutionInfoKey executionInfoKey, @NotNull final String executionId, int limit) {
    if (isEmpty(executionId)) {
      throw new InvalidArgumentsException("Execution id cannot be null or empty");
    }
    return stageExecutionInfoRepository.listSucceededStageExecutionNotIncludeCurrent(
        executionInfoKey, executionId, limit);
  }
}
