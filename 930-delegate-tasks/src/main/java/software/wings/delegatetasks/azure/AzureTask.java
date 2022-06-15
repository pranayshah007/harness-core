/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.azure.response.AzureValidateTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.AzureConfigException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AzureTask extends AbstractDelegateRunnableTask {
  @Inject private AzureAsyncTaskHelper azureAsyncTaskHelper;

  public AzureTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Object Array parameters not supported");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    if (!(parameters instanceof AzureTaskParams)) {
      throw new InvalidRequestException("Task Params are not of expected type: AzureTaskParameters");
    }
    AzureTaskParams azureTaskParams = (AzureTaskParams) parameters;
    String msg;
    switch (azureTaskParams.getAzureTaskType()) {
      case VALIDATE:
        return AzureValidateTaskResponse.builder()
            .connectorValidationResult(azureAsyncTaskHelper.getConnectorValidationResult(
                azureTaskParams.getEncryptionDetails(), azureTaskParams.getAzureConnector()))
            .build();
      case LIST_SUBSCRIPTIONS:
        return azureAsyncTaskHelper.listSubscriptions(
            azureTaskParams.getEncryptionDetails(), azureTaskParams.getAzureConnector());
      case LIST_RESOURCE_GROUPS:
        validateSubscriptionIdExist(
            azureTaskParams, "Could not retrieve any resource groups because of invalid parameter(s)");
        return azureAsyncTaskHelper.listResourceGroups(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
      case LIST_CONTAINER_REGISTRIES: {
        validateSubscriptionIdExist(
            azureTaskParams, "Could not retrieve any container registries because of invalid parameter(s)");
        return azureAsyncTaskHelper.listContainerRegistries(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
      }
      case LIST_CLUSTERS:
        msg = "Could not retrieve any cluster because of invalid parameter(s)";
        validateSubscriptionIdExist(azureTaskParams, msg);
        validateResourceGroupExist(azureTaskParams, msg);
        return azureAsyncTaskHelper.listClusters(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP));
      case LIST_REPOSITORIES: {
        msg = "Could not retrieve any repositories because of invalid parameter(s)";
        validateSubscriptionIdExist(azureTaskParams, msg);
        validateContainerRegistryExist(azureTaskParams, msg);
        return azureAsyncTaskHelper.listRepositories(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.CONTAINER_REGISTRY));
      }
      case GET_ACR_TOKEN: {
        msg = "Could not retrieve any container registries because of invalid parameter(s)";
        validateContainerRegistryExist(azureTaskParams, msg);
        return azureAsyncTaskHelper.getServicePrincipalCertificateAcrLoginToken(
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.CONTAINER_REGISTRY),
            azureTaskParams.getEncryptionDetails(), azureTaskParams.getAzureConnector());
      }
      default:
        throw new InvalidRequestException("Task type not identified");
    }
  }

  private void validateSubscriptionIdExist(AzureTaskParams azureTaskParams, String failMessage) {
    if (azureTaskParams == null
        || (azureTaskParams.getAdditionalParams() == null
            || azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID) == null)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please check your subscription configuration parameter",
          failMessage, new AzureConfigException("Subscription ID not provided"));
    }
  }

  private void validateContainerRegistryExist(AzureTaskParams azureTaskParams, String failMessage) {
    if (azureTaskParams == null
        || (azureTaskParams.getAdditionalParams() == null
            || azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.CONTAINER_REGISTRY) == null)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check your container registry name configuration parameter", failMessage,
          new AzureConfigException("Container registry name not provided"));
    }
  }

  private void validateResourceGroupExist(AzureTaskParams azureTaskParams, String failMessage) {
    if (azureTaskParams == null
        || (azureTaskParams.getAdditionalParams() == null
            || azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP) == null)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check your resource group configuration parameter", failMessage,
          new AzureConfigException("Resource group name not provided"));
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
