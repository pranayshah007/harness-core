/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

@Slf4j
public class HttpTaskNotifyCallback implements NotifyCallbackWithErrorHandling {
    @Override
    public void notify(Map<String, Supplier<ResponseData>> response) {
        buildResponse(response);
    }

    private void buildResponse(Map<String, Supplier<ResponseData>> response) {
        Iterator<Supplier<ResponseData>> iterator = response.values().iterator();
        String taskId = response.keySet().iterator().next();
        log.info("Task Successfully queued with taskId: {}", taskId);
        try {
            Supplier<ResponseData> responseDataSupplier = iterator.next();
            HttpStepResponse responseData = (HttpStepResponse) responseDataSupplier.get();
            log.info("responseData header: {}",responseData.getHeader());
            log.info("responseData body: {}",responseData.getHttpResponseBody());
        } catch (Exception e) {
            log.error("IDP proxy callback triggered with error response response", e);
        }
    }
}