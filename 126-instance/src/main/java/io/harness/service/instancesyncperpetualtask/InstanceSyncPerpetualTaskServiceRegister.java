/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask;

import static io.harness.perpetualtask.PerpetualTaskType.K8S_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.NATIVE_HELM_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.SERVERLESS_AWS_LAMBDA_INSTANCE_SYNC;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.helm.NativeHelmInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.k8s.K8SInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.serverless.ServerlessAwsLambdaInstanceSyncPerpetualTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public final class InstanceSyncPerpetualTaskServiceRegister {
  private final K8SInstanceSyncPerpetualTaskHandler k8sInstanceSyncPerpetualService;
  private final NativeHelmInstanceSyncPerpetualTaskHandler nativeHelmInstanceSyncPerpetualTaskHandler;
  private final ServerlessAwsLambdaInstanceSyncPerpetualTaskHandler serverlessAwsLambdaInstanceSyncPerpetualTaskHandler;

  public InstanceSyncPerpetualTaskHandler getInstanceSyncPerpetualService(String perpetualTaskType) {
    switch (perpetualTaskType) {
      case K8S_INSTANCE_SYNC:
        return k8sInstanceSyncPerpetualService;
      case NATIVE_HELM_INSTANCE_SYNC:
        return nativeHelmInstanceSyncPerpetualTaskHandler;
      case SERVERLESS_AWS_LAMBDA_INSTANCE_SYNC:
        return serverlessAwsLambdaInstanceSyncPerpetualTaskHandler;
      default:
        throw new UnexpectedException(
            "No instance sync service registered for perpetual task type: " + perpetualTaskType);
    }
  }
}
