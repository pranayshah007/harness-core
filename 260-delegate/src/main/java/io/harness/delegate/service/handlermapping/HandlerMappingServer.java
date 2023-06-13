/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.handlermapping;

import io.harness.delegate.beans.SchedulingTaskEvent;
import io.harness.delegate.core.beans.AcquireTasksResponse;
import io.harness.delegate.service.common.AcquireTaskHelper;
import io.harness.delegate.service.handlermapping.context.Context;
import io.harness.delegate.service.handlermapping.handlers.ExecutionHandler;
import io.harness.delegate.service.handlermapping.handlers.ExecutionInfrastructureDeleteHandler;
import io.harness.delegate.service.handlermapping.handlers.ExecutionInfrastructureHandler;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class HandlerMappingServer {
  private ThreadPoolExecutor taskExecutor;
  AcquireTaskHelper acquireTaskHelper;
  HandlerMappingRouter router;
  Injector injector;
  Context context;

  @Inject
  public HandlerMappingServer(@Named("taskExecutor") ThreadPoolExecutor taskExecutor,
      AcquireTaskHelper acquireTaskHelper, Context context, Injector injector) {
    this.taskExecutor = taskExecutor;
    this.acquireTaskHelper = acquireTaskHelper;
    this.injector = injector;
    this.router = new HandlerMappingRouter()
                      .POST("/executionInfrastructure/:runnerType", ExecutionInfrastructureHandler.class)
                      .POST("/execution/:runnerType", ExecutionHandler.class)
                      .DELETE("/executionInfrastructure/:runnerType", ExecutionInfrastructureDeleteHandler.class);
    this.context = context;
  }

  // TODO: delegate id and instance id should be injected
  // TODO: add delegate agent lifecycle observer implementation to abstract frozen, self-destruct etc.
  public void serve(AcquireTasksResponse acquired) {
    var taskPayload = acquired.getTask(0);
    var routed =
        router.route(SchedulingTaskEvent.Method.valueOf(taskPayload.getResourceMethod()), taskPayload.getResourceUri());
    if (routed == null || routed.notFound()) {
      log.error("404 error for {} {}", taskPayload.getResourceMethod(), taskPayload.getResourceUri());
      return;
    }
    var handlerClass = routed.target();
    var handler = injector.getInstance(handlerClass);

    var handlerContext = context.deepCopy();
    handlerContext.set(Context.TASK_ID, taskPayload.getId());
    // TODO: add decrypted secrets here
    handler.handle(routed.params(), taskPayload, context);
    log.info("Finish executing handler {} {}", taskPayload.getResourceMethod(), taskPayload.getResourceUri());
  }
}
