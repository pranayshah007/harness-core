/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.common.AzureAppServiceService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProviderFactory;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.azure.appservice.AbstractAzureAppServiceTaskHandler;
import software.wings.delegatetasks.azure.common.ArtifactDownloaderServiceLogWrapper;
import software.wings.delegatetasks.azure.common.AutoCloseableWorkingDirectory;
import software.wings.delegatetasks.azure.common.context.ArtifactDownloaderContext;

import com.google.inject.Inject;
import java.io.File;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class AbstractAzureWebAppTaskHandler extends AbstractAzureAppServiceTaskHandler {
  @Inject protected AzureAppServiceDeploymentService azureAppServiceDeploymentService;
  @Inject protected AzureAppServiceService azureAppServiceService;
  @Inject protected AzureLogCallbackProviderFactory logCallbackProviderFactory;
  @Inject private ArtifactDownloaderServiceLogWrapper artifactDownloaderServiceLogWrapper;

  protected AzureWebClientContext buildAzureWebClientContext(
      AzureAppServiceTaskParameters appServiceTaskParameters, AzureConfig azureConfig) {
    return AzureWebClientContext.builder()
        .azureConfig(azureConfig)
        .appName(appServiceTaskParameters.getAppName())
        .subscriptionId(appServiceTaskParameters.getSubscriptionId())
        .resourceGroupName(appServiceTaskParameters.getResourceGroupName())
        .build();
  }

  protected AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    throw new UnsupportedOperationException("Concrete subclass method implementation not available yet");
  }

  protected AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient,
      ArtifactStreamAttributes artifactStreamAttributes) {
    throw new UnsupportedOperationException("Concrete subclass method implementation not available yet");
  }

  protected File getArtifactFile(AzureAppServiceTaskParameters azureWebAppTaskParameters,
      ArtifactStreamAttributes streamAttributes, AutoCloseableWorkingDirectory autoCloseableWorkingDirectory,
      ILogStreamingTaskClient logStreamingTaskClient) {
    ArtifactDownloaderContext artifactDownloaderContext =
        toArtifactDownloaderContext(azureWebAppTaskParameters, streamAttributes, autoCloseableWorkingDirectory);
    return artifactDownloaderServiceLogWrapper.fetchArtifactFileForDeploymentAndLog(
        artifactDownloaderContext, logStreamingTaskClient);
  }

  private ArtifactDownloaderContext toArtifactDownloaderContext(AzureAppServiceTaskParameters azureWebAppTaskParameters,
      ArtifactStreamAttributes streamAttributes, AutoCloseableWorkingDirectory autoCloseableWorkingDirectory) {
    return ArtifactDownloaderContext.builder()
        .accountId(azureWebAppTaskParameters.getAccountId())
        .activityId(azureWebAppTaskParameters.getActivityId())
        .appId(azureWebAppTaskParameters.getAppId())
        .commandName(azureWebAppTaskParameters.getCommandName())
        .artifactStreamAttributes(streamAttributes)
        .workingDirectory(autoCloseableWorkingDirectory.workingDir())
        .build();
  }
}
