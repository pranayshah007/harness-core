/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.TaskSelectorMap.TaskSelectorMapKeys;
import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;

import static dev.morphia.mapping.Mapper.ID_KEY;

import io.harness.delegate.beans.TaskGroup;
import io.harness.delegate.beans.TaskSelectorMap;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTaskSelectorMapService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import java.util.List;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@Singleton
@ValidateOnExecution
@Slf4j
public class DelegateTaskSelectorMapServiceImpl implements DelegateTaskSelectorMapService {
  @Inject private HPersistence hPersistence;

  @Override
  public List<TaskSelectorMap> list(String accountId) {
    return hPersistence.createQuery(TaskSelectorMap.class).filter(TaskSelectorMapKeys.accountId, accountId).asList();
  }

  @Override
  public TaskSelectorMap add(TaskSelectorMap taskSelectorMap) {
    log.info("Adding task selector map:" + taskSelectorMap);
    if (isEmpty(taskSelectorMap.getSelectors())) {
      log.warn("Task selector list cannot be empty.");
      throw new IllegalArgumentException("Task selector list cannot be empty.");
    }

    try {
      hPersistence.save(taskSelectorMap);
    } catch (DuplicateKeyException e) {
      ignoredOnPurpose(e);
      throw new InvalidRequestException("Task selector map with given task group already exists for this account");
    }

    log.info("Added task selector map: {}", taskSelectorMap.getUuid());
    return taskSelectorMap;
  }

  @Override
  public TaskSelectorMap update(TaskSelectorMap taskSelectorMap) {
    log.info("Updating task selector map:" + taskSelectorMap);
    if (isEmpty(taskSelectorMap.getSelectors())) {
      hPersistence.delete(TaskSelectorMap.class, taskSelectorMap.getUuid());
      return null;
    } else {
      hPersistence.update(taskSelectorMap,
          hPersistence.createUpdateOperations(TaskSelectorMap.class)
              .set(TaskSelectorMapKeys.selectors, taskSelectorMap.getSelectors()));
      return taskSelectorMap;
    }
  }

  @Override
  public TaskSelectorMap addTaskSelector(String accountId, String taskSelectorMapUuid, String taskSelector) {
    TaskSelectorMap existingMap = hPersistence.createQuery(TaskSelectorMap.class)
                                      .filter(TaskSelectorMapKeys.accountId, accountId)
                                      .filter(ID_KEY, taskSelectorMapUuid)
                                      .get();
    if (existingMap == null) {
      String errorMessage = String.format("Task selector map with id: %s not found", taskSelectorMapUuid);
      log.warn(errorMessage);
      throw NoResultFoundException.newBuilder().code(ErrorCode.RESOURCE_NOT_FOUND).message(errorMessage).build();
    }
    if (!existingMap.getSelectors().contains(taskSelector)) {
      existingMap.getSelectors().add(taskSelector);
      hPersistence.save(existingMap);
      log.info("Updated task selector map {} for task category {} with new task selector", taskSelectorMapUuid,
          existingMap.getTaskGroup(), taskSelector);
    }
    return existingMap;
  }

  @Override
  public TaskSelectorMap removeTaskSelector(String accountId, String taskSelectorMapUuid, String taskSelector) {
    TaskSelectorMap existingMap = hPersistence.createQuery(TaskSelectorMap.class)
                                      .filter(TaskSelectorMapKeys.accountId, accountId)
                                      .filter(ID_KEY, taskSelectorMapUuid)
                                      .get();
    if (existingMap == null) {
      String errorMessage = String.format("Task selector map with id: %s not found", taskSelectorMapUuid);
      log.warn(errorMessage);
      throw NoResultFoundException.newBuilder().code(ErrorCode.RESOURCE_NOT_FOUND).message(errorMessage).build();
    }
    if (isNotEmpty(existingMap.getSelectors()) && existingMap.getSelectors().contains(taskSelector)) {
      existingMap.getSelectors().remove(taskSelector);
      if (existingMap.getSelectors().isEmpty()) {
        hPersistence.delete(TaskSelectorMap.class, taskSelectorMapUuid);
        existingMap = null;
        log.info("Delegate task selector map {} deleted", taskSelectorMapUuid);
      } else {
        hPersistence.save(existingMap);
        log.info("Delegate task selector map {} updated", taskSelectorMapUuid);
      }
    }
    return existingMap;
  }

  @Override
  public TaskSelectorMap get(String accountId, TaskGroup taskGroup) {
    return hPersistence.createQuery(TaskSelectorMap.class)
        .filter(TaskSelectorMapKeys.accountId, accountId)
        .filter(TaskSelectorMapKeys.taskGroup, taskGroup)
        .get();
  }
}
