/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.background;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.export.ExportExecutionsRequestLogContext;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequest.ExportExecutionsRequestKeys;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class ExportExecutionsRequestCleanupHandler implements Handler<ExportExecutionsRequest> {
  private static final int ASSIGNMENT_INTERVAL_HOURS = 1;
  private static final int REASSIGNMENT_INTERVAL_MINUTES = 45;
  private static final int ACCEPTABLE_DELAY_MINUTES = 45;

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private ExportExecutionsService exportExecutionsService;
  @Inject private MorphiaPersistenceProvider<ExportExecutionsRequest> persistenceProvider;

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PumpExecutorOptions.builder()
            .name("ExportExecutionsRequestCleanupHandler")
            .poolSize(2)
            .interval(ofHours(ASSIGNMENT_INTERVAL_HOURS))
            .build(),
        ExportExecutionsRequestCleanupHandler.class,
        MongoPersistenceIterator.<ExportExecutionsRequest, MorphiaFilterExpander<ExportExecutionsRequest>>builder()
            .clazz(ExportExecutionsRequest.class)
            .fieldName(ExportExecutionsRequestKeys.nextCleanupIteration)
            .targetInterval(ofMinutes(REASSIGNMENT_INTERVAL_MINUTES))
            .acceptableNoAlertDelay(ofMinutes(ACCEPTABLE_DELAY_MINUTES))
            .handler(this)
            .filterExpander(query
                -> query.field(ExportExecutionsRequestKeys.status)
                       .equal(Status.READY)
                       .field(ExportExecutionsRequestKeys.expiresAt)
                       .greaterThan(0)
                       .field(ExportExecutionsRequestKeys.expiresAt)
                       .lessThan(System.currentTimeMillis()))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(ExportExecutionsRequest request) {
    if (request == null) {
      log.warn("ExportExecutionsRequest is null");
      return;
    }

    try (AutoLogContext ignore1 = new AccountLogContext(request.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ExportExecutionsRequestLogContext(request.getUuid(), OVERRIDE_ERROR)) {
      try {
        exportExecutionsService.expireRequest(request);
      } catch (Exception ex) {
        // NOTE: This operation will be tried again. Just log an error and don't rethrow exception.
        log.error("Unable to cleanup export executions request", ex);
      }
    }
  }
}
