/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azureartifacts;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTestConnectionTaskParams;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTestConnectionTaskResponse;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsValidationParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Slf4j
public class AzureArtifactsTestConnectionDelegateTask extends AbstractDelegateRunnableTask {
  private static final String EMPTY_STR = "";

  @Inject private AzureArtifactsValidationHandler azureArtifactsValidationHandler;

  public AzureArtifactsTestConnectionDelegateTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AzureArtifactsTestConnectionTaskResponse run(TaskParameters parameters) {
    AzureArtifactsTestConnectionTaskParams azureArtifactsTestConnectionTaskParams =
        (AzureArtifactsTestConnectionTaskParams) parameters;

    AzureArtifactsConnectorDTO azureArtifactsConnector =
        azureArtifactsTestConnectionTaskParams.getAzureArtifactsConnector();

    final AzureArtifactsValidationParams azureArtifactsValidationParams =
        AzureArtifactsValidationParams.builder()
            .encryptionDataDetails(azureArtifactsTestConnectionTaskParams.getEncryptionDetails())
            .registryUrl(azureArtifactsTestConnectionTaskParams.getRegistryUrl())
            .azureArtifactsConnectorDTO(azureArtifactsConnector)
            .build();

    ConnectorValidationResult azureArtifactsConnectorValidationResult =
        azureArtifactsValidationHandler.validate(azureArtifactsValidationParams, getAccountId());

    azureArtifactsConnectorValidationResult.setDelegateId(getDelegateId());

    return AzureArtifactsTestConnectionTaskResponse.builder()
        .connectorValidationResult(azureArtifactsConnectorValidationResult)
        .build();
  }

  @Override
  public AzureArtifactsTestConnectionTaskResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
