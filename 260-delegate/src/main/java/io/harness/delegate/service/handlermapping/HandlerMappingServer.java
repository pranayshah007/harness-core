/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping;

import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.delegate.core.beans.TaskPayload;
import io.harness.delegate.service.common.AcquireTaskHelper;
import io.harness.delegate.service.handlermapping.context.Context;
import io.harness.delegate.service.handlermapping.handlers.Handler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class HandlerMappingServer {
  private final ThreadPoolExecutor taskExecutor;
  private final AcquireTaskHelper acquireTaskHelper;
  private final Context context;
  private final Map<String, Handler> handlersMap;

  @Inject
  public HandlerMappingServer(@Named("taskExecutor") ThreadPoolExecutor taskExecutor,
      AcquireTaskHelper acquireTaskHelper, Context context, Map<String, Handler> handlersMap) {
    this.taskExecutor = taskExecutor;
    this.acquireTaskHelper = acquireTaskHelper;
    this.context = context;
    this.handlersMap = handlersMap;
  }

  public void serve(AcquireTasksResponse acquired) {
    final TaskPayload taskPayload = acquired.getTask(0);

    var handler = handlersMap.get(taskPayload.getEventType());
    if (Objects.isNull(handler)) {
      log.error("Handler not found for event {}", taskPayload.getEventType());
      return;
    }

    var handlerContext = context.deepCopy();
    handlerContext.set(Context.TASK_ID, taskPayload.getId());
    // TODO: add decrypted secrets here
    handler.handle(taskPayload.getRunnerType(), taskPayload, context);
    log.info("Finished executing handler {}", taskPayload.getEventType());
  }
}
