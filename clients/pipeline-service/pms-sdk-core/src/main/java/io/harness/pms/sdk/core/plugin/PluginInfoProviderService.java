/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.PluginCreationBatchRequest;
import io.harness.pms.contracts.plan.PluginCreationBatchResponse;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginInfoProviderServiceGrpc.PluginInfoProviderServiceImplBase;
import io.harness.pms.security.PmsSecurityContextEventGuard;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class PluginInfoProviderService extends PluginInfoProviderServiceImplBase {
  @Inject PluginInfoProviderHelper pluginInfoProvider;

  @Override
  public void getPluginInfosList(
      PluginCreationBatchRequest batchRequest, StreamObserver<PluginCreationBatchResponse> responseObserver) {
    Map<String, PluginCreationResponseList> requestIdToPluginCreationResponse = new HashMap<>();
    Set<Integer> usedPorts = new HashSet<>(batchRequest.getUsedPortDetails().getUsedPortsList());
    Ambiance ambiance = batchRequest.getAmbiance();
    try (PmsSecurityContextEventGuard securityContextEventGuard = new PmsSecurityContextEventGuard(ambiance)) {
      for (PluginCreationRequest request : batchRequest.getPluginCreationRequestList()) {
        PluginCreationResponseList pluginCreationResponse;
        usedPorts.addAll(request.getUsedPortDetails().getUsedPortsList());
        try {
          pluginCreationResponse = pluginInfoProvider.getPluginInfo(request, usedPorts, ambiance);
        } catch (Exception ex) {
          log.error("Got error in getting plugin info", ex);
          pluginCreationResponse = PluginCreationResponseList.newBuilder().build();
        }
        for (PluginCreationResponseWrapper wrapper : pluginCreationResponse.getResponseList()) {
          usedPorts.addAll(wrapper.getResponse().getPluginDetails().getPortUsedList());
        }
        requestIdToPluginCreationResponse.put(request.getRequestId(), pluginCreationResponse);
      }
      responseObserver.onNext(PluginCreationBatchResponse.newBuilder()
                                  .putAllRequestIdToResponse(requestIdToPluginCreationResponse)
                                  .build());
      responseObserver.onCompleted();
    } catch (Exception e) {
      responseObserver.onNext(PluginCreationBatchResponse.newBuilder()
                                  .putAllRequestIdToResponse(requestIdToPluginCreationResponse)
                                  .build());
      responseObserver.onCompleted();
    }
  }
}
