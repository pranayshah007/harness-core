/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.wsapi;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.harness.delegate.beans.DelegateWebsocketAPIEvent;
import io.harness.delegate.core.beans.WebsocketRequestPayload;
import io.harness.delegate.service.common.AcquireTaskHelper;
import io.harness.delegate.service.wsapi.handlers.ExecutionHandler;
import io.harness.delegate.service.wsapi.handlers.ExecutionInfrastructureDeleteHandler;
import io.harness.delegate.service.wsapi.handlers.ExecutionInfrastructureHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

import java.io.IOException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class WebsocketAPIServer {
    @Inject @Named("taskExecutor") private ThreadPoolExecutor taskExecutor;
    @Inject AcquireTaskHelper acquireTaskHelper;

    private static WebsocketAPIRouter router = new WebsocketAPIRouter()
        .POST("/executionInfrastructure/:runnerType", ExecutionInfrastructureHandler.class)
        .POST("/execution/:runnerType/:infraId", ExecutionHandler.class)
        .DELETE("/executionInfrastructure/:runnerType/:infraId", ExecutionInfrastructureDeleteHandler.class);

    // TODO: delegate id and instance id should be injected
    // TODO: add delegate agent lifecycle observer implementation to abstract frozen, self-destruct etc.
    public void serve(DelegateWebsocketAPIEvent apiRequestEvent, String delegateId, String delegateInstanceId, Consumer<Response<WebsocketRequestPayload>> failedResponseHandler) throws IOException {
        // 1. check if handler exists
        // 2. check if acquire can be successful, and acquire
        // 3. Invoke the corresponding handler
        var routed = router.route(DelegateWebsocketAPIEvent.Method.valueOf(apiRequestEvent.getMethod()), apiRequestEvent.getUri());
        if (routed.notFound()) {
            log.error("404 error for {} {}", apiRequestEvent.getMethod(), apiRequestEvent.getUri());
            return;
        }
        WebsocketRequestPayload acquired = acquireTaskHelper.acquireWebsocketAPIPayload(apiRequestEvent.getAccountId(), delegateId, delegateInstanceId, apiRequestEvent.getStateMachineId(), failedResponseHandler);
        log.info("Acquired statemachine entity: {}", acquired.getId());
    }
}
