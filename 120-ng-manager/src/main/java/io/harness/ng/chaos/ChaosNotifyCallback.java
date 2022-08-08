/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.chaos;

import io.harness.ng.chaos.client.ChaosApplyManifestResponse;
import io.harness.ng.chaos.client.ChaosApplyManifestResponse.ChaosApplyManifestResponseBuilder;
import io.harness.ng.chaos.client.ChaosHttpClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChaosNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject ChaosHttpClient chaosHttpClient;

  String chaosUuid;

  public ChaosNotifyCallback(String chaosUuid) {
    this.chaosUuid = chaosUuid;
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    ChaosApplyManifestResponse applyManifestResponse = buildResponse(response);

    try {
      Boolean isSuccessful = NGRestUtils.getResponse(chaosHttpClient.pushTaskResponse(applyManifestResponse));
      if (isSuccessful) {
        log.info("Chaos Server informed successfully");
      } else {
        log.error("Chaos Server not informed");
      }
    } catch (Exception ex) {
      log.error("Chaos Server not informed");
    }
  }

  private ChaosApplyManifestResponse buildResponse(Map<String, Supplier<ResponseData>> response) {
    Iterator<Supplier<ResponseData>> iterator = response.values().iterator();
    String taskId = response.keySet().iterator().next();
    ChaosApplyManifestResponseBuilder responseBuilder = ChaosApplyManifestResponse.builder().taskId(taskId);
    try {
      Supplier<ResponseData> responseDataSupplier = iterator.next();
      ResponseData responseData = responseDataSupplier.get();
      responseBuilder = responseBuilder.status("SUCCESS");
      log.info("Chaos callback triggered with success response");
    } catch (Exception e) {
      log.error("Chaos callback triggered with error response response", e);
      responseBuilder = responseBuilder.status("FAILED");
    }
    return responseBuilder.build();
  }
}
