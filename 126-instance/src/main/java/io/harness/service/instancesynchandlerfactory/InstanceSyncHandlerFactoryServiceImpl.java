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
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandler.AsgInstanceSyncHandler;
import io.harness.service.instancesynchandler.AwsLambdaInstanceSyncHandler;
import io.harness.service.instancesynchandler.AwsSamInstanceSyncHandler;
import io.harness.service.instancesynchandler.AwsSshWinrmInstanceSyncHandler;
import io.harness.service.instancesynchandler.AzureSshWinrmInstanceSyncHandler;
import io.harness.service.instancesynchandler.AzureWebAppInstanceSyncHandler;
import io.harness.service.instancesynchandler.CustomDeploymentInstanceSyncHandler;
import io.harness.service.instancesynchandler.EcsInstanceSyncHandler;
import io.harness.service.instancesynchandler.GitOpsInstanceSyncHandler;
import io.harness.service.instancesynchandler.GoogleFunctionInstanceSyncHandler;
import io.harness.service.instancesynchandler.K8sInstanceSyncHandler;
import io.harness.service.instancesynchandler.NativeHelmInstanceSyncHandler;
import io.harness.service.instancesynchandler.PdcInstanceSyncHandler;
import io.harness.service.instancesynchandler.ServerlessAwsLambdaInstanceSyncHandler;
import io.harness.service.instancesynchandler.SpotInstanceSyncHandler;
import io.harness.service.instancesynchandler.TasInstanceSyncHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;

@Singleton
@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceSyncHandlerFactoryServiceImpl implements InstanceSyncHandlerFactoryService {
  private final K8sInstanceSyncHandler k8sInstanceSyncHandler;
  private final GitOpsInstanceSyncHandler gitOpsInstanceSyncHandler;
  private final NativeHelmInstanceSyncHandler nativeHelmInstanceSyncHandler;
  private final ServerlessAwsLambdaInstanceSyncHandler serverlessAwsLambdaInstanceSyncHandler;
  private final AzureWebAppInstanceSyncHandler azureWebAppInstanceSyncHandler;
  private final EcsInstanceSyncHandler ecsInstanceSyncHandler;
  private final PdcInstanceSyncHandler pdcInstanceSyncHandler;
  private final AzureSshWinrmInstanceSyncHandler azureSshWinrmInstanceSyncHandler;
  private final AwsSshWinrmInstanceSyncHandler awsSshWinrmInstanceSyncHandler;
  private final CustomDeploymentInstanceSyncHandler customDeploymentInstanceSyncHandler;
  private final SpotInstanceSyncHandler spotInstanceSyncHandler;
  private final TasInstanceSyncHandler tasInstanceSyncHandler;
  private final AsgInstanceSyncHandler asgInstanceSyncHandler;
  private final GoogleFunctionInstanceSyncHandler googleFunctionInstanceSyncHandler;
  private final AwsSamInstanceSyncHandler awsSamInstanceSyncHandler;
  private final AwsLambdaInstanceSyncHandler awsLambdaInstanceSyncHandler;

  @Override
  public AbstractInstanceSyncHandler getInstanceSyncHandler(final String deploymentType, String infraKind) {
    switch (deploymentType) {
      case ServiceSpecType.GITOPS:
        return gitOpsInstanceSyncHandler;
      case ServiceSpecType.KUBERNETES:
        return k8sInstanceSyncHandler;
      case ServiceSpecType.NATIVE_HELM:
        return nativeHelmInstanceSyncHandler;
      case ServiceSpecType.SERVERLESS_AWS_LAMBDA:
        return serverlessAwsLambdaInstanceSyncHandler;
      case ServiceSpecType.AZURE_WEBAPP:
        return azureWebAppInstanceSyncHandler;
      case ServiceSpecType.ECS:
        return ecsInstanceSyncHandler;
      case ServiceSpecType.SSH:
      case ServiceSpecType.WINRM:
        return getSshWinRmInstanceSyncHandler(infraKind);
      case ServiceSpecType.CUSTOM_DEPLOYMENT:
        return customDeploymentInstanceSyncHandler;
      case ServiceSpecType.ELASTIGROUP:
        return spotInstanceSyncHandler;
      case ServiceSpecType.TAS:
        return tasInstanceSyncHandler;
      case ServiceSpecType.ASG:
        return asgInstanceSyncHandler;
      case ServiceSpecType.GOOGLE_CLOUD_FUNCTIONS:
        return googleFunctionInstanceSyncHandler;
      case ServiceSpecType.AWS_SAM:
        return awsSamInstanceSyncHandler;
      case ServiceSpecType.AWS_LAMBDA:
        return awsLambdaInstanceSyncHandler;
      default:
        throw new UnexpectedException("No instance sync handler registered for deploymentType: " + deploymentType);
    }
  }

  private AbstractInstanceSyncHandler getSshWinRmInstanceSyncHandler(String infraKind) {
    switch (infraKind) {
      case InfrastructureKind.PDC:
        return pdcInstanceSyncHandler;
      case InfrastructureKind.SSH_WINRM_AZURE:
        return azureSshWinrmInstanceSyncHandler;
      case InfrastructureKind.SSH_WINRM_AWS:
        return awsSshWinrmInstanceSyncHandler;
      default:
        throw new UnexpectedException("No instance sync handler registered for infraKind: " + infraKind);
    }
  }
}
