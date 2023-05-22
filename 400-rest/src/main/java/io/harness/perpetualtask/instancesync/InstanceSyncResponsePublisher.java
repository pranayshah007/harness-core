/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.instancesync.InstanceSyncResourceClient;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.DX)
@Singleton
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class InstanceSyncResponsePublisher {
  @Inject private InstanceSyncResourceClient instanceSyncResourceClient;

  public void publishInstanceSyncResponseToNG(
      String accountIdentifier, String perpetualTaskId, DelegateResponseData instanceSyncPerpetualTaskResponse) {
    if (instanceSyncPerpetualTaskResponse == null) {
      log.error("Instance sync perpetual task response is null for accountIdentifier : {} and perpetualTaskId : {}",
          accountIdentifier, perpetualTaskId);
    }
    boolean response = false;
    int retry = 0;
    while (!response && retry < 3) {
      try {
        response = NGRestUtils.getResponse(instanceSyncResourceClient.sendPerpetualTaskResponse(
            accountIdentifier, perpetualTaskId, instanceSyncPerpetualTaskResponse));
      } catch (Exception exception) {
        log.error(
            "Error occured while sending instance sync perpetual task response from CG to NG for accountIdentifier : {} and perpetualTaskId : {}",
            accountIdentifier, perpetualTaskId, exception);
      }
      retry += 1;
    }

    log.info(
        "Successfully pushed instance sync perpetual task response from CG to NG for accountIdentifier : {} and perpetualTaskId : {}",
        accountIdentifier, perpetualTaskId);
  }

  public void publishInstanceSyncResponseV2ToNG(
      String accountIdentifier, String perpetualTaskId, InstanceSyncResponseV2 instanceSyncResponseV2) {
    if (instanceSyncResponseV2 == null) {
      log.error("Instance sync perpetual task response is null for accountIdentifier : {} and perpetualTaskId : {}",
          accountIdentifier, perpetualTaskId);
    }
    boolean response = false;
    int retry = 0;
    while (!response && retry < 3) {
      try {
        response = NGRestUtils.getResponse(instanceSyncResourceClient.sendPerpetualTaskV2Response(
            accountIdentifier, perpetualTaskId, instanceSyncResponseV2));
      } catch (Exception exception) {
        log.error(
            "Error occured while sending instance sync perpetual task v2 response from CG to NG for accountIdentifier : {} and perpetualTaskId : {}",
            accountIdentifier, perpetualTaskId, exception);
      }
      retry += 1;
    }

    log.info(
        "Successfully pushed instance sync perpetual task v2 response from CG to NG for accountIdentifier : {} and perpetualTaskId : {}",
        accountIdentifier, perpetualTaskId);
  }

  public InstanceSyncTaskDetails fetchTaskDetails(String perpetualTaskId, String accountId) {
    try {
      return getResponse(instanceSyncResourceClient.getInstanceSyncTaskDetails(perpetualTaskId, accountId));
    } catch (Exception exception) {
      log.error(
          "Error occurred while sending fetch Task Details response from NG to CG for accountIdentifier : {} and perpetualTaskId : {}",
          accountId, perpetualTaskId, exception);
    }
    return InstanceSyncTaskDetails.builder().details(emptyList()).build();
  }
}
