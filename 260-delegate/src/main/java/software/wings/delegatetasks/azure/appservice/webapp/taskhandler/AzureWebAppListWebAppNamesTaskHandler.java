/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppNamesParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppNamesResponse;

import software.wings.delegatetasks.azure.appservice.webapp.AbstractAzureWebAppTaskHandler;

import com.azure.resourcemanager.appservice.models.WebAppBasic;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppListWebAppNamesTaskHandler extends AbstractAzureWebAppTaskHandler {
  @Inject private AzureWebClient azureWebClient;

  @Override
  protected AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    String subscriptionId = azureAppServiceTaskParameters.getSubscriptionId();
    String resourceGroupName =
        ((AzureWebAppListWebAppNamesParameters) azureAppServiceTaskParameters).getResourceGroupName();

    AzureClientContext azureClientContext =
        new AzureClientContext(azureConfig, subscriptionId, resourceGroupName, false);

    List<WebAppBasic> webAppsBasic = azureWebClient.listWebAppsByResourceGroupName(azureClientContext);

    return AzureWebAppListWebAppNamesResponse.builder().webAppNames(toWebAppNames(webAppsBasic)).build();
  }

  @NotNull
  private List<String> toWebAppNames(List<WebAppBasic> webAppsBasic) {
    return webAppsBasic.stream().map(WebAppBasic::name).collect(Collectors.toList());
  }
}
