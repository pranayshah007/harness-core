/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesynchandlerfactory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandler.K8sInstanceSyncHandler;
import io.harness.service.instancesynchandler.NativeHelmInstanceSyncHandler;
import io.harness.service.instancesynchandler.ServerlessAwsLambdaInstanceSyncHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceSyncHandlerFactoryServiceImpl implements InstanceSyncHandlerFactoryService {
  private final K8sInstanceSyncHandler k8sInstanceSyncHandler;
  private final NativeHelmInstanceSyncHandler nativeHelmInstanceSyncHandler;
  private final ServerlessAwsLambdaInstanceSyncHandler serverlessAwsLambdaInstanceSyncHandler;
  @Override
  public AbstractInstanceSyncHandler getInstanceSyncHandler(final String deploymentType) {
    switch (deploymentType) {
      case ServiceSpecType.KUBERNETES:
        return k8sInstanceSyncHandler;
      case ServiceSpecType.NATIVE_HELM:
        return nativeHelmInstanceSyncHandler;
      case ServiceSpecType.SERVERLESS_AWS_LAMBDA:
        return serverlessAwsLambdaInstanceSyncHandler;
      default:
        throw new UnexpectedException("No instance sync handler registered for deploymentType: " + deploymentType);
    }
  }
}
