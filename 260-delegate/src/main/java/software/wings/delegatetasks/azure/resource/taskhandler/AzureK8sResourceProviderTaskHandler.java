/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.resource.taskhandler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureKubernetesClient;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperation;
import io.harness.delegate.task.azure.resource.operation.AzureResourceOperationResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AzureK8sResourceProviderTaskHandler extends AbstractAzureResourceTaskHandler {
  @Inject private AzureKubernetesClient kubernetesClient;

  @Override
  public AzureResourceOperationResponse executeTask(AzureResourceOperation operation, AzureConfig azureConfig) {
    return operation.executeOperation(kubernetesClient, azureConfig);
  }
}
