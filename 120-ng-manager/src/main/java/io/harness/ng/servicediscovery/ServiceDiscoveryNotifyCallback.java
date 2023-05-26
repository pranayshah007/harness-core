/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.servicediscovery;

import io.harness.remote.client.NGRestUtils;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryApplyManifestResponse;
import io.harness.servicediscovery.client.beans.ServiceDiscoveryApplyManifestResponse.ServiceDiscoveryApplyManifestResponseBuilder;
import io.harness.servicediscovery.client.remote.ServiceDiscoveryHttpClient;
import io.harness.tasks.ResponseData;
import io.harness.waiter.NotifyCallbackWithErrorHandling;

import com.google.inject.Inject;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceDiscoveryNotifyCallback implements NotifyCallbackWithErrorHandling {
  @Inject ServiceDiscoveryHttpClient serviceDiscoveryHttpClient;

  String serviceDiscoveryUuid;

  public ServiceDiscoveryNotifyCallback(String serviceDiscoveryUuid) {
    this.serviceDiscoveryUuid = serviceDiscoveryUuid;
  }

  @Override
  public void notify(Map<String, Supplier<ResponseData>> response) {
    ServiceDiscoveryApplyManifestResponse applyManifestResponse = buildResponse(response);

    try {
      Boolean isSuccessful =
          NGRestUtils.getResponse(serviceDiscoveryHttpClient.pushTaskResponse(applyManifestResponse));
      if (Boolean.TRUE.equals(isSuccessful)) {
        log.info("Service Discovery server informed successfully");
      } else {
        log.error("Service Discovery server not informed");
      }
    } catch (Exception ex) {
      log.error("Service Discovery server not informed", ex);
    }
  }

  private ServiceDiscoveryApplyManifestResponse buildResponse(Map<String, Supplier<ResponseData>> response) {
    String taskId = response.keySet().iterator().next();
    ServiceDiscoveryApplyManifestResponseBuilder responseBuilder =
        ServiceDiscoveryApplyManifestResponse.builder().taskId(taskId);
    try {
      responseBuilder = responseBuilder.status("SUCCESS");
      log.info("Service Discovery callback triggered with success response");
    } catch (Exception e) {
      log.error("Service Discovery callback triggered with error response", e);
      responseBuilder = responseBuilder.status("FAILED");
    }
    return responseBuilder.build();
  }
}
