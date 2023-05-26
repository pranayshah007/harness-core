/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask;

import static io.harness.perpetualtask.PerpetualTaskType.ASG_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.AWS_LAMBDA_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.AWS_SAM_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.AWS_SSH_WINRM_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.AZURE_SSH_WINRM_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.AZURE_WEB_APP_NG_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.CUSTOM_DEPLOYMENT_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.ECS_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.GOOGLE_CLOUD_FUNCTION_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.K8S_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.NATIVE_HELM_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.PDC_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.SERVERLESS_AWS_LAMBDA_INSTANCE_SYNC;
import static io.harness.perpetualtask.PerpetualTaskType.SPOT_INSTANCE_SYNC_NG;
import static io.harness.perpetualtask.PerpetualTaskType.TAS_INSTANCE_SYNC_NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.InstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.aws.AsgInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.aws.AwsLambdaInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.aws.AwsSshWinrmInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.awssam.AwsSamInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.azure.AzureSshWinrmInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.azure.AzureWebAppInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.customDeployment.CustomDeploymentInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.ecs.EcsInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.googlefunctions.GoogleFunctionInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.helm.NativeHelmInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.k8s.K8SInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.pdc.PdcInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.serverless.ServerlessAwsLambdaInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.spot.SpotInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.tas.TasInstanceSyncPerpetualTaskHandler;

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
  private final AzureWebAppInstanceSyncPerpetualTaskHandler azureWebAppInstanceSyncPerpetualTaskHandler;
  private final EcsInstanceSyncPerpetualTaskHandler ecsInstanceSyncPerpetualTaskHandler;
  private final PdcInstanceSyncPerpetualTaskHandler pdcInstanceSyncPerpetualTaskHandler;
  private final AzureSshWinrmInstanceSyncPerpetualTaskHandler azureSshWinrmInstanceSyncPerpetualTaskHandler;
  private final AwsSshWinrmInstanceSyncPerpetualTaskHandler awsSshWinrmInstanceSyncPerpetualTaskHandler;
  private final CustomDeploymentInstanceSyncPerpetualTaskHandler CustomDeploymentInstanceSyncPerpetualTaskHandler;
  private final SpotInstanceSyncPerpetualTaskHandler spotInstanceSyncPerpetualTaskHandler;
  private final TasInstanceSyncPerpetualTaskHandler tasInstanceSyncPerpetualTaskHandler;
  private final AsgInstanceSyncPerpetualTaskHandler asgInstanceSyncPerpetualTaskHandler;
  private final GoogleFunctionInstanceSyncPerpetualTaskHandler googleFunctionInstanceSyncPerpetualTaskHandler;
  private final AwsSamInstanceSyncPerpetualTaskHandler awsSamInstanceSyncPerpetualTaskHandler;
  private final AwsLambdaInstanceSyncPerpetualTaskHandler awsLambdaInstanceSyncPerpetualTaskHandler;

  public InstanceSyncPerpetualTaskHandler getInstanceSyncPerpetualService(String perpetualTaskType) {
    switch (perpetualTaskType) {
      case K8S_INSTANCE_SYNC:
        return k8sInstanceSyncPerpetualService;
      case NATIVE_HELM_INSTANCE_SYNC:
        return nativeHelmInstanceSyncPerpetualTaskHandler;
      case SERVERLESS_AWS_LAMBDA_INSTANCE_SYNC:
        return serverlessAwsLambdaInstanceSyncPerpetualTaskHandler;
      case AZURE_WEB_APP_NG_INSTANCE_SYNC:
        return azureWebAppInstanceSyncPerpetualTaskHandler;
      case ECS_INSTANCE_SYNC:
        return ecsInstanceSyncPerpetualTaskHandler;
      case PDC_INSTANCE_SYNC_NG:
        return pdcInstanceSyncPerpetualTaskHandler;
      case AZURE_SSH_WINRM_INSTANCE_SYNC_NG:
        return azureSshWinrmInstanceSyncPerpetualTaskHandler;
      case AWS_SSH_WINRM_INSTANCE_SYNC_NG:
        return awsSshWinrmInstanceSyncPerpetualTaskHandler;
      case CUSTOM_DEPLOYMENT_INSTANCE_SYNC_NG:
        return CustomDeploymentInstanceSyncPerpetualTaskHandler;
      case SPOT_INSTANCE_SYNC_NG:
        return spotInstanceSyncPerpetualTaskHandler;
      case TAS_INSTANCE_SYNC_NG:
        return tasInstanceSyncPerpetualTaskHandler;
      case ASG_INSTANCE_SYNC_NG:
        return asgInstanceSyncPerpetualTaskHandler;
      case GOOGLE_CLOUD_FUNCTION_INSTANCE_SYNC_NG:
        return googleFunctionInstanceSyncPerpetualTaskHandler;
      case AWS_SAM_INSTANCE_SYNC_NG:
        return awsSamInstanceSyncPerpetualTaskHandler;
      case AWS_LAMBDA_INSTANCE_SYNC_NG:
        return awsLambdaInstanceSyncPerpetualTaskHandler;
      default:
        throw new UnexpectedException(
            "No instance sync service registered for perpetual task type: " + perpetualTaskType);
    }
  }
}
