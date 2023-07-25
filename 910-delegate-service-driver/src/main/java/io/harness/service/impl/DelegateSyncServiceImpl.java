/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.lang.System.currentTimeMillis;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateSyncTaskResponse.DelegateSyncTaskResponseKeys;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.serializer.KryoSerializer;
import io.harness.service.intfc.DelegateSyncService;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ResponseData;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.tools.StringUtils;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class DelegateSyncServiceImpl implements DelegateSyncService {
  @Inject private HPersistence persistence;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject @Named("disableDeserialization") private boolean disableDeserialization;

  @VisibleForTesting public final ConcurrentMap<String, AtomicLong> syncTaskWaitMap = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings({"PMD", "SynchronizationOnLocalVariableOrMethodParameter"})
  public void run() {
    try {
      if (isNotEmpty(syncTaskWaitMap)) {
        List<String> completedSyncTasks = persistence.createQuery(DelegateSyncTaskResponse.class, excludeAuthority)
                                              .field(DelegateSyncTaskResponseKeys.uuid)
                                              .in(syncTaskWaitMap.keySet())
                                              .asKeyList()
                                              .stream()
                                              .map(key -> key.getId().toString())
                                              .collect(toList());
        for (String taskId : completedSyncTasks) {
          log.debug("Found response for sync task {}", taskId);
          AtomicLong endAt = syncTaskWaitMap.get(taskId);
          if (endAt != null) {
            synchronized (endAt) {
              log.debug("Notifying threads for task {}", taskId);
              endAt.set(0L);
              endAt.notifyAll();
            }
          }
        }
      }
    } catch (Exception exception) {
      log.warn("Exception is of type Exception. Ignoring.", exception);
    }
  }

  @Override
  public <T extends ResponseData> T waitForTask(
      String taskId, String description, Duration timeout, List<ExecutionCapability> executionCapabilities) {
    DelegateSyncTaskResponse taskResponse = null;
    try {
      log.info("Start wait sync task {}", taskId);
      AtomicLong endAt =
          syncTaskWaitMap.computeIfAbsent(taskId, k -> new AtomicLong(currentTimeMillis() + timeout.toMillis()));
      synchronized (endAt) {
        do {
          endAt.wait(Math.min(Duration.ofMillis(200).toMillis(), timeout.toMillis()));
          taskResponse = persistence.get(DelegateSyncTaskResponse.class, taskId);
        } while (taskResponse == null && endAt.get() != 0 && currentTimeMillis() < endAt.get());
      }
    } catch (Exception e) {
      throw new InvalidArgumentsException(Pair.of("args", "Error while waiting for completion"), e);
    } finally {
      syncTaskWaitMap.remove(taskId);
      persistence.delete(DelegateSyncTaskResponse.class, taskId);
    }

    if (taskResponse == null) {
      List<String> capabilityErrorMsgsList = new ArrayList<>();
      if (isNotEmpty(executionCapabilities)) {
        for (ExecutionCapability executionCapability : executionCapabilities) {
          if (isNotEmpty(executionCapability.getCapabilityToString())) {
            capabilityErrorMsgsList.add(executionCapability.getCapabilityToString());
          }
        }
      }
      String errorMsg = "Task has expired.";
      if (CollectionUtils.isNotEmpty(capabilityErrorMsgsList)) {
        errorMsg = errorMsg
            + String.format(" None of the delegate had following capabilities [%s]",
                StringUtils.join(capabilityErrorMsgsList, ","));
      } else {
        errorMsg = errorMsg
            + " It wasn't picked up by any delegate or delegate did not have enough time to finish the execution";
      }
      throw new InvalidArgumentsException(errorMsg);
    }

    if (disableDeserialization) {
      return (T) BinaryResponseData.builder()
          .data(taskResponse.getResponseData())
          .usingKryoWithoutReference(taskResponse.isUsingKryoWithoutReference())
          .build();
    }
    // throw exception here
    Object response = referenceFalseKryoSerializer.asInflatedObject(taskResponse.getResponseData());
    if (response instanceof ErrorNotifyResponseData) {
      WingsException exception = ((ErrorNotifyResponseData) response).getException();
      // if task registered to error handling framework on delegate, then exception won't be null
      if (exception != null) {
        throw exception;
      }
    }

    return (T) referenceFalseKryoSerializer.asInflatedObject(taskResponse.getResponseData());
  }
}
