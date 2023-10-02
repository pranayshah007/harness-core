/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.utility.AzureUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.azure.KeyVaultAuthenticator;

import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpClient;
import com.azure.core.management.ProxyResource;
import com.azure.resourcemanager.keyvault.fluent.KeyVaultManagementClient;
import com.microsoft.aad.msal4j.MsalServiceException;
import java.io.IOException;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
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
    } catch (MsalServiceException e) {
      String message =
          format("Failed to list secret engines for due to Azure authentication error. %s", e.getMessage());
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
    } catch (HttpResponseException e) {
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, e.getMessage(), e, USER);
    } catch (Exception ex) {
      String message = format(
          "Failed to list secret engines for due to unexpected network error. Please try again. %s", ex.getMessage());
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, ex, USER);
    }
    return NGAzureKeyVaultFetchEngineResponse.builder().secretEngines(secretEngines).build();
  }

  private List<String> listVaultsInternal(AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO) throws IOException {
    HttpClient httpClient = AzureUtils.getAzureHttpClient();
    TokenCredential credentials = null;
    if (BooleanUtils.isTrue(azureKeyVaultConnectorDTO.getUseManagedIdentity())) {
      credentials = KeyVaultAuthenticator.getManagedIdentityCredentials(
          azureKeyVaultConnectorDTO.getManagedClientId(), azureKeyVaultConnectorDTO.getAzureManagedIdentityType());
    } else {
      credentials = KeyVaultAuthenticator.getAuthenticationTokenCredentials(azureKeyVaultConnectorDTO.getClientId(),
          String.valueOf(azureKeyVaultConnectorDTO.getSecretKey().getDecryptedValue()),
          azureKeyVaultConnectorDTO.getTenantId(), httpClient, azureKeyVaultConnectorDTO.getAzureEnvironmentType());
    }
    KeyVaultManagementClient keyVaultClient =
        KeyVaultAuthenticator.getAzureKeyVaultClient(credentials, azureKeyVaultConnectorDTO, httpClient);
    return keyVaultClient.getVaults().list().stream().map(ProxyResource::name).collect(Collectors.toList());
  }

  public boolean isSupportingErrorFramework() {
    return true;
  }
}
