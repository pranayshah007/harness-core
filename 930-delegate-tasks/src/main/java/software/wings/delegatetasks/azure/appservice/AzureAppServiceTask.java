/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice;

import static io.harness.azure.model.AzureConstants.UNRECOGNIZED_TASK;
import static io.harness.logging.CommandExecutionStatus.FAILURE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.azure.AzureSecretHelper;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureAppServiceTask extends AbstractDelegateRunnableTask {
  @Inject private AzureSecretHelper azureSecretHelper;
  @Inject private AzureAppServiceTaskFactory azureAppServiceTaskFactory;

  public AzureAppServiceTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented.");
  }

  @Override
  public AzureTaskExecutionResponse run(TaskParameters parameters) {
    if (!(parameters instanceof AzureTaskExecutionRequest)) {
      String message = format(UNRECOGNIZED_TASK, parameters.getClass().getSimpleName());
      log.error(message);
      return AzureTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).errorMessage(message).build();
    }

    AzureTaskExecutionRequest azureTaskExecutionRequest = (AzureTaskExecutionRequest) parameters;
    AzureConfig azureConfig = azureSecretHelper.decryptAndGetAzureConfig(
        azureTaskExecutionRequest.getAzureConfigDTO(), azureTaskExecutionRequest.getAzureConfigEncryptionDetails());
    ILogStreamingTaskClient logStreamingTaskClient = getLogStreamingTaskClient();

    ArtifactStreamAttributes artifactStreamAttributes = azureTaskExecutionRequest.getArtifactStreamAttributes();
    artifactStreamAttributes = azureSecretHelper.decryptArtifactStreamAttributes(artifactStreamAttributes);

    AzureAppServiceTaskParameters azureAppServiceTaskParameters =
        (AzureAppServiceTaskParameters) azureTaskExecutionRequest.getAzureTaskParameters();
    azureSecretHelper.decryptAzureAppServiceTaskParameters(azureAppServiceTaskParameters);

    AbstractAzureAppServiceTaskHandler azureAppServiceTask =
        azureAppServiceTaskFactory.getAzureAppServiceTask(azureAppServiceTaskParameters.getCommandType().name());

    AzureTaskExecutionResponse azureTaskExecutionResponse = azureAppServiceTask.executeTask(
        azureAppServiceTaskParameters, azureConfig, logStreamingTaskClient, artifactStreamAttributes);

    azureSecretHelper.encryptAzureTaskResponseParams(azureTaskExecutionResponse.getAzureTaskResponse(),
        azureAppServiceTaskParameters.getAccountId(), azureAppServiceTaskParameters.getCommandType());
    return azureTaskExecutionResponse;
  }
}
