/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.taskhandler;

import io.harness.azure.client.AzureManagementClient;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMListSubscriptionLocationsResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import software.wings.delegatetasks.azure.arm.AbstractAzureARMTaskHandler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@NoArgsConstructor
@Slf4j
public class AzureARMListSubscriptionLocationsTaskHandler extends AbstractAzureARMTaskHandler {
  @Inject private AzureManagementClient azureManagementClient;

  @Override
  protected AzureARMTaskResponse executeTaskInternal(AzureARMTaskParameters azureARMTaskParameters,
      AzureConfig azureConfig, AzureLogCallbackProvider logStreamingTaskClient) {
    String subscriptionId = azureARMTaskParameters.getSubscriptionId();

    List<String> locations = azureManagementClient.listLocationsBySubscriptionId(azureConfig, subscriptionId);
    return AzureARMListSubscriptionLocationsResponse.builder().locations(locations).build();
  }
}
