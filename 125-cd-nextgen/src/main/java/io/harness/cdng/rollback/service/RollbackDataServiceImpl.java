/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.rollback.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.cdng.rollback.RollbackData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.rollback.RollbackDataRepository;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class RollbackDataServiceImpl implements RollbackDataService {
  private RollbackDataRepository rollbackDataRepository;

  @Override
  public RollbackData save(@Valid @NotNull RollbackData rollbackData) {
    return rollbackDataRepository.save(rollbackData);
  }

  public Optional<RollbackData> getLatestRollbackData(
      @NotNull final String rollbackDeploymentInfoKey, StageStatus stageStatus) {
    if (isEmpty(rollbackDeploymentInfoKey)) {
      throw new InvalidArgumentsException("Rollback key cannot be null or empty");
    }

    int limit = 1;
    List<RollbackData> rollbackData = listLatestRollbackData(rollbackDeploymentInfoKey, stageStatus, limit);
    return rollbackData.size() == limit ? Optional.ofNullable(rollbackData.get(0)) : Optional.empty();
  }

  @Override
  public List<RollbackData> listLatestRollbackData(@NotNull final String key, StageStatus stageStatus, int limit) {
    if (isEmpty(key)) {
      throw new InvalidArgumentsException("Rollback key cannot be null or empty");
    }

    return rollbackDataRepository.listRollbackDataOrderedByCreatedAt(key, stageStatus, limit);
  }

  @Override
  public void updateStatus(@NotNull final String executionId, StageStatus stageStatus) {
    if (isEmpty(executionId)) {
      throw new InvalidArgumentsException("Execution Id cannot be null or empty");
    }

    UpdateResult updateResult = rollbackDataRepository.updateStatus(executionId, stageStatus);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(
          format("Unable to update stage status, executionId: %s, stageStatus: %s", executionId, stageStatus));
    }
  }
}
