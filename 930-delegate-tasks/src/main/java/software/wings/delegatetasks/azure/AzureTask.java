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
        validateAzureResourceExist(azureTaskParams,
            "Could not retrieve any resource groups because of invalid parameter(s)",
            AzureAdditionalParams.SUBSCRIPTION_ID);
        return azureAsyncTaskHelper.listResourceGroups(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
      case LIST_WEBAPP_NAMES:
        msg = "Could not retrieve any Azure Web App names because of invalid parameter(s)";
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.RESOURCE_GROUP);
        return azureAsyncTaskHelper.listWebAppNames(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP));
      case LIST_DEPLOYMENT_SLOTS:
        msg = "Could not retrieve any azure Web App deployment slots because of invalid parameter(s)";
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.RESOURCE_GROUP);
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.WEB_APP_NAME);
        return azureAsyncTaskHelper.listDeploymentSlots(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.WEB_APP_NAME));
      case LIST_CONTAINER_REGISTRIES: {
        validateAzureResourceExist(azureTaskParams,
            "Could not retrieve any container registries because of invalid parameter(s)",
            AzureAdditionalParams.SUBSCRIPTION_ID);
        return azureAsyncTaskHelper.listContainerRegistries(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
      }
      case LIST_CLUSTERS:
        msg = "Could not retrieve any cluster because of invalid parameter(s)";
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.RESOURCE_GROUP);
        return azureAsyncTaskHelper.listClusters(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP));
      case LIST_REPOSITORIES: {
        msg = "Could not retrieve any repositories because of invalid parameter(s)";
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.CONTAINER_REGISTRY);
        return azureAsyncTaskHelper.listRepositories(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.CONTAINER_REGISTRY));
      }
      case GET_ACR_TOKEN: {
        msg = "Could not retrieve any container registries because of invalid parameter(s)";
        validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.CONTAINER_REGISTRY);
        return azureAsyncTaskHelper.getServicePrincipalCertificateAcrLoginToken(
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.CONTAINER_REGISTRY),
            azureTaskParams.getEncryptionDetails(), azureTaskParams.getAzureConnector());
      }
      case LIST_TAGS:
        validateAzureResourceExist(azureTaskParams, "Could not retrieve any tags because of invalid parameter(s)",
            AzureAdditionalParams.SUBSCRIPTION_ID);
        return azureAsyncTaskHelper.listTags(azureTaskParams.getEncryptionDetails(),
            azureTaskParams.getAzureConnector(),
            azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
      default:
        throw new InvalidRequestException("Task type not identified");
    }
  }

  private void validateAzureResourceExist(
      AzureTaskParams azureTaskParams, String failMessage, AzureAdditionalParams azureResource) {
    if (azureTaskParams == null
        || (azureTaskParams.getAdditionalParams() == null
            || azureTaskParams.getAdditionalParams().get(azureResource) == null)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check " + azureResource.getResourceName() + " configuration parameter", failMessage,
          new AzureConfigException(azureResource.getResourceName() + " not provided"));
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
