package io.harness.service.impl;

import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.Delegate;
import io.harness.persistence.HPersistence;
import io.harness.selection.log.DelegateSelectionLog;
import io.harness.service.intfc.DelegateCache;

import software.wings.service.intfc.DMSDelegateSelectionLogService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DMSDelegateSelectionLogServiceImpl implements DMSDelegateSelectionLogService {
  @Inject private HPersistence persistence;
  @Inject private DelegateCache delegateCache;
  private static final String TASK_ASSIGNED = "Delegate assigned for task execution";
  private static final String ASSIGNED = "Assigned";

  //  @Inject private DataStoreService dataStoreService;
  private Cache<String, List<DelegateSelectionLog>> cache =
      Caffeine.newBuilder()
          .executor(Executors.newSingleThreadExecutor(
              new ThreadFactoryBuilder().setNameFormat("delegate-selection-log-write").build()))
          .expireAfterWrite(1000, TimeUnit.MILLISECONDS)
          .removalListener(this::dispatchSelectionLogs)
          .build();
  @Override
  public void logTaskAssigned(String delegateId, DelegateTask delegateTask) {
    if (!delegateTask.isSelectionLogsTrackingEnabled()) {
      return;
    }
    Delegate delegate = delegateCache.get(delegateTask.getAccountId(), delegateId, false);
    if (delegate != null) {
      delegateId = delegate.getHostName();
    }
    String message = String.format("%s : [%s]", TASK_ASSIGNED, delegateId);
    save(DelegateSelectionLog.builder()
             .accountId(getAccountId(delegateTask))
             .taskId(delegateTask.getUuid())
             .conclusion(ASSIGNED)
             .message(message)
             .eventTimestamp(System.currentTimeMillis())
             .build());
  }

  @Override
  public synchronized void save(DelegateSelectionLog selectionLog) {
    Optional.ofNullable(cache.get(selectionLog.getAccountId(), log -> new ArrayList<>()))
        .ifPresent(logs -> logs.add(selectionLog));
  }

  private String getAccountId(DelegateTask delegateTask) {
    return delegateTask.isExecuteOnHarnessHostedDelegates() ? delegateTask.getSecondaryAccountId()
                                                            : delegateTask.getAccountId();
  }

  private void dispatchSelectionLogs(String accountId, List<DelegateSelectionLog> logs, RemovalCause removalCause) {
    //    try {
    //      dataStoreService.save(DelegateSelectionLog.class, logs, true);
    //      // TODO: remove this once reading from datastore is operational
    ////      if (dataStoreService instanceof GoogleDataStoreServiceImpl) {
    ////        persistence.save(logs);
    ////      }
    //    } catch (Exception exception) {
    //      log.error("Error while saving into Database ", exception);
    //    }
    return;
  }
}
