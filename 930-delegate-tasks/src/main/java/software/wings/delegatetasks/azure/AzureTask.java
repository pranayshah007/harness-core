/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure;

import static io.harness.azure.model.AzureConstants.AZURE_AUTH_CERT_DIR_PATH;
import static io.harness.azure.model.AzureConstants.REPOSITORY_DIR_PATH;
import static io.harness.eraro.ErrorCode.AZURE_CLIENT_EXCEPTION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureHostConnectionType;
import io.harness.azure.model.AzureOSType;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.azure.AzureValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.azure.AzureConfigContext;
import io.harness.delegate.beans.azure.AzureConfigContext.AzureConfigContextBuilder;
import io.harness.delegate.beans.azure.response.AzureValidateTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureAdditionalParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.AzureClientException;
import io.harness.exception.AzureConfigException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.LazyAutoCloseableWorkingDirectory;

import com.azure.core.exception.HttpResponseException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AzureTask extends AbstractDelegateRunnableTask {
  @Inject private AzureAsyncTaskHelper azureAsyncTaskHelper;
  @Inject private AzureValidationHandler azureValidationHandler;

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

    try (LazyAutoCloseableWorkingDirectory certificateWorkingDirectory =
             new LazyAutoCloseableWorkingDirectory(REPOSITORY_DIR_PATH, AZURE_AUTH_CERT_DIR_PATH)) {
      AzureConfigContextBuilder azureConfigContextBuilder =
          AzureConfigContext.builder()
              .azureConnector(azureTaskParams.getAzureConnector())
              .encryptedDataDetails(azureTaskParams.getEncryptionDetails())
              .certificateWorkingDirectory(certificateWorkingDirectory);
      switch (azureTaskParams.getAzureTaskType()) {
        case VALIDATE:
          ConnectorValidationResult connectorValidationResult =
              azureValidationHandler.validate(azureConfigContextBuilder.build());
          connectorValidationResult.setDelegateId(getDelegateId());
          return AzureValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
        case LIST_MNG_GROUP:
          return azureAsyncTaskHelper.listMngGroup(azureConfigContextBuilder.build());
        case LIST_SUBSCRIPTION_LOCATIONS:
          if (azureTaskParams.getAdditionalParams() != null
              && azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID) != null) {
            azureConfigContextBuilder.subscriptionId(
                azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
            return azureAsyncTaskHelper.listSubscriptionLocations(azureConfigContextBuilder.build());
          } else {
            return azureAsyncTaskHelper.listLocations(azureConfigContextBuilder.build());
          }
        case LIST_SUBSCRIPTIONS:
          return azureAsyncTaskHelper.listSubscriptions(azureConfigContextBuilder.build());
        case LIST_RESOURCE_GROUPS:
          validateAzureResourceExist(azureTaskParams,
              "Could not retrieve any resource groups because of invalid parameter(s)",
              AzureAdditionalParams.SUBSCRIPTION_ID);
          azureConfigContextBuilder.subscriptionId(
              azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
          return azureAsyncTaskHelper.listResourceGroups(azureConfigContextBuilder.build());
        case LIST_IMAGE_GALLERIES:
          validateAzureResourceExist(azureTaskParams,
              "Could not retrieve any image galleries because of invalid parameter(s)",
              AzureAdditionalParams.SUBSCRIPTION_ID);
          validateAzureResourceExist(azureTaskParams,
              "Could not retrieve any image galleries because of invalid parameter(s)",
              AzureAdditionalParams.RESOURCE_GROUP);
          azureConfigContextBuilder
              .subscriptionId(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID))
              .resourceGroup(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP));
          return azureAsyncTaskHelper.listImageGalleries(azureConfigContextBuilder.build());
        case LIST_WEBAPP_NAMES:
          msg = "Could not retrieve any Azure Web App names because of invalid parameter(s)";
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.RESOURCE_GROUP);
          azureConfigContextBuilder
              .subscriptionId(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID))
              .resourceGroup(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP));
          return azureAsyncTaskHelper.listWebAppNames(azureConfigContextBuilder.build());
        case LIST_DEPLOYMENT_SLOTS:
          msg = "Could not retrieve any azure Web App deployment slots because of invalid parameter(s)";
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.RESOURCE_GROUP);
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.WEB_APP_NAME);
          azureConfigContextBuilder
              .subscriptionId(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID))
              .resourceGroup(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP))
              .webAppName(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.WEB_APP_NAME));
          return azureAsyncTaskHelper.listDeploymentSlots(azureConfigContextBuilder.build());
        case LIST_CONTAINER_REGISTRIES: {
          validateAzureResourceExist(azureTaskParams,
              "Could not retrieve any container registries because of invalid parameter(s)",
              AzureAdditionalParams.SUBSCRIPTION_ID);
          azureConfigContextBuilder.subscriptionId(
              azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
          return azureAsyncTaskHelper.listContainerRegistries(azureConfigContextBuilder.build());
        }
        case LIST_CLUSTERS:
          msg = "Could not retrieve any cluster because of invalid parameter(s)";
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.RESOURCE_GROUP);
          azureConfigContextBuilder
              .subscriptionId(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID))
              .resourceGroup(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP));
          return azureAsyncTaskHelper.listClusters(azureConfigContextBuilder.build());
        case LIST_REPOSITORIES: {
          msg = "Could not retrieve any repositories because of invalid parameter(s)";
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.SUBSCRIPTION_ID);
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.CONTAINER_REGISTRY);
          azureConfigContextBuilder
              .subscriptionId(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID))
              .containerRegistry(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.CONTAINER_REGISTRY));
          return azureAsyncTaskHelper.listRepositories(azureConfigContextBuilder.build());
        }
        case GET_ACR_TOKEN: {
          msg = "Could not retrieve any container registries because of invalid parameter(s)";
          validateAzureResourceExist(azureTaskParams, msg, AzureAdditionalParams.CONTAINER_REGISTRY);
          azureConfigContextBuilder.containerRegistry(
              azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.CONTAINER_REGISTRY));
          return azureAsyncTaskHelper.getAcrLoginToken(azureConfigContextBuilder.build());
        }
        case LIST_TAGS:
          validateAzureResourceExist(azureTaskParams, "Could not retrieve any tags because of invalid parameter(s)",
              AzureAdditionalParams.SUBSCRIPTION_ID);
          azureConfigContextBuilder.subscriptionId(
              azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID));
          return azureAsyncTaskHelper.listTags(azureConfigContextBuilder.build());
        case LIST_HOSTS:
          validateAzureResourceExist(azureTaskParams, "Could not retrieve hosts because of invalid parameter(s)",
              AzureAdditionalParams.SUBSCRIPTION_ID);
          azureConfigContextBuilder
              .subscriptionId(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.SUBSCRIPTION_ID))
              .resourceGroup(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.RESOURCE_GROUP))
              .azureOSType(
                  AzureOSType.fromString(azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.OS_TYPE)))
              .azureHostConnectionType(AzureHostConnectionType.fromString(
                  azureTaskParams.getAdditionalParams().get(AzureAdditionalParams.HOST_CONNECTION_TYPE)))
              .tags((Map<String, String>) azureTaskParams.getParams().get("tags"));

          return azureAsyncTaskHelper.listHosts(azureConfigContextBuilder.build());
        default:
          throw new InvalidRequestException("Task type not identified");
      }
    } catch (IOException ioe) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ioe);
      log.error("Exception in processing Azure task [{}]", azureTaskParams.getAzureTaskType(), sanitizedException);
      throw new AzureClientException(
          "Issue with creating Azure working temp dir", AZURE_CLIENT_EXCEPTION, WingsException.USER);
    } catch (HttpResponseException e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing Azure task [{}]", azureTaskParams.getAzureTaskType(), sanitizedException);
      throw new AzureClientException(sanitizedException.getMessage(), AZURE_CLIENT_EXCEPTION, WingsException.USER);
    } catch (WingsException we) {
      throw we;
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      throw new AzureClientException(sanitizedException.getMessage(), AZURE_CLIENT_EXCEPTION, WingsException.USER);
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
