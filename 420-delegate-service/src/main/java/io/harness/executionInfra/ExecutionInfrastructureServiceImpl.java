/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.executionInfra;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.executionInfra.ExecutionInfraLocation.ExecutionInfraLocationKeys;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExecutionInfrastructureServiceImpl implements ExecutionInfrastructureService {
  private final HPersistence persistence;

  @Override
  public String createExecutionInfra(
      final String taskId, final Map<String, String> stepTaskIds, final String runnerType) {
    try (AutoLogContext ignore = new ExecutionLogContext(taskId, OVERRIDE_ERROR)) {
      final ExecutionInfraLocation entity =
          ExecutionInfraLocation.builder().runnerType(runnerType).uuid(taskId).stepTaskIds(stepTaskIds).build();
      return persistence.save(entity, false);
    }
  }

  @Override
  public boolean updateDelegateInfo(final String infraRefId, final String delegateId, final String delegateName) {
    final var updateOperation = persistence.createUpdateOperations(ExecutionInfraLocation.class)
                                    .set(ExecutionInfraLocationKeys.createdByDelegateId, delegateId)
                                    .set(ExecutionInfraLocationKeys.delegateGroupName, delegateName)
                                    .set(ExecutionInfraLocationKeys.delegateGroupName, delegateName);
    return persistence.update(findInfra(infraRefId), updateOperation).getUpdatedExisting();
  }

  @Override
  public ExecutionInfraLocation getExecutionInfra(final String infraRefId) {
    final var infra = findInfra(infraRefId).first();
    if (infra == null) {
      throw new IllegalArgumentException("ExecutionInfraLocation not found for infraRefId " + infraRefId);
    }
    return infra;
  }

  @Override
  public boolean deleteExecutionInfrastructure(final String infraRefId) {
    return persistence.delete(findInfra(infraRefId));
  }

  private Query<ExecutionInfraLocation> findInfra(final String infraRefId) {
    return persistence.createQuery(ExecutionInfraLocation.class).filter(ExecutionInfraLocation.UUID_KEY, infraRefId);
  }
}
