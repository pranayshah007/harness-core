/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AddUniqueIdParentIdToEntitiesJob implements Managed {
  private Future<?> uniqueIdParentIdForEntityJobFuture;
  private final ScheduledExecutorService executorService;
  private final AddUniqueIdParentIdToEntitiesTask scopeEntitiesTask;
  private static final String DEBUG_MESSAGE = "AddMissingUniqueIdParentIdForEntitiesJob ";

  @Inject
  public AddUniqueIdParentIdToEntitiesJob(AddUniqueIdParentIdToEntitiesTask scopeEntitiesTask) {
    this.scopeEntitiesTask = scopeEntitiesTask;
    this.executorService = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder().setNameFormat("add-missing-uniqueId-parentId-for-entities").build());
  }

  @Override
  public void start() throws Exception {
    log.info(DEBUG_MESSAGE + "started...");
    uniqueIdParentIdForEntityJobFuture =
        executorService.scheduleWithFixedDelay(scopeEntitiesTask, 30, 1440, TimeUnit.MINUTES);
  }

  @Override
  public void stop() throws Exception {
    uniqueIdParentIdForEntityJobFuture.cancel(false);
    log.info(DEBUG_MESSAGE + "stopping...");
    executorService.shutdownNow();
  }
}
