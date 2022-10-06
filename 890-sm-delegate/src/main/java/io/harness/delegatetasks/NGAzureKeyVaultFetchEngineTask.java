/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.SecretManagementDelegateException;

import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.CloudException;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.keyvault.Vault;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import com.microsoft.rest.RestException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(PL)
@Slf4j
public class NGAzureKeyVaultFetchEngineTask extends AbstractDelegateRunnableTask {
  private static String possibleExceptionMessage;
  public NGAzureKeyVaultFetchEngineTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    NGAzureKeyVaultFetchEngineTaskParameters azureKeyVaultFetchEngineTaskParameters =
        (NGAzureKeyVaultFetchEngineTaskParameters) parameters;
    List<String> secretEngines;
    try {
      secretEngines = listVaultsInternal(azureKeyVaultFetchEngineTaskParameters.getAzureKeyVaultConnectorDTO());
    } catch (IOException exception) {
      String message = "Failed to list secret engines for due to unexpected network error. Please try again.";
      log.error(message, exception);
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, USER);
    } catch (RuntimeException ex) {
      if (ex instanceof CloudException) {
        throw ex;
      } else if (isNestedAuthenticationException(ex)) {
        throw new RestException(possibleExceptionMessage, null);
      } else {
        String message = "Failed to list secret engines for due to unexpected network error. Please try again.";
        throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, USER);
      }
    }
    return NGAzureKeyVaultFetchEngineResponse.builder().secretEngines(secretEngines).build();
  }

  private List<String> listVaultsInternal(AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO) throws IOException {
    Azure azure;
    List<Vault> vaultList = new ArrayList<>();
    ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(azureKeyVaultConnectorDTO.getClientId(),
        azureKeyVaultConnectorDTO.getTenantId(),
        String.valueOf(azureKeyVaultConnectorDTO.getSecretKey().getDecryptedValue()),
        getAzureEnvironment(azureKeyVaultConnectorDTO.getAzureEnvironmentType()));

    Azure.Authenticated authenticate = Azure.configure().authenticate(credentials);

    if (isEmpty(azureKeyVaultConnectorDTO.getSubscription())) {
      azure = authenticate.withDefaultSubscription();
    } else {
      azure = authenticate.withSubscription(azureKeyVaultConnectorDTO.getSubscription());
    }

    for (ResourceGroup rGroup : azure.resourceGroups().list()) {
      vaultList.addAll(azure.vaults().listByResourceGroup(rGroup.name()));
    }

    return vaultList.stream().map(HasName::name).collect(Collectors.toList());
  }

  private AzureEnvironment getAzureEnvironment(AzureEnvironmentType azureEnvironmentType) {
    if (azureEnvironmentType == null) {
      return AzureEnvironment.AZURE;
    }

    switch (azureEnvironmentType) {
      case AZURE_US_GOVERNMENT:
        return AzureEnvironment.AZURE_US_GOVERNMENT;

      case AZURE:
      default:
        return AzureEnvironment.AZURE;
    }
  }

  private boolean isNestedAuthenticationException(Exception exception) {
    if (exception.getCause() instanceof IOException) {
      Exception nestedLevel1Exception = (IOException) exception.getCause();
      if (nestedLevel1Exception.getCause() instanceof ExecutionException) {
        Exception nestedLevel2Exception = (ExecutionException) nestedLevel1Exception.getCause();
        if (nestedLevel2Exception.getCause() instanceof AuthenticationException) {
          Exception nestedLevel3Exception = (AuthenticationException) nestedLevel2Exception.getCause();
          possibleExceptionMessage = nestedLevel3Exception.getMessage();
          return true;
        }
      }
    }
    return false;
  }

  public boolean isSupportingErrorFramework() {
    return true;
  }
}
