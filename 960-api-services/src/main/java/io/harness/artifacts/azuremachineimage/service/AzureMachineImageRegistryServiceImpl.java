package io.harness.artifacts.azuremachineimage.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureConfig;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.fluentcore.arm.models.HasName;
import com.microsoft.rest.LogLevel;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
@OwnedBy(CDC)
@Singleton
@Slf4j
public class AzureMachineImageRegistryServiceImpl implements AzureMachineImageRegistryService {
  protected Azure getAzureClient(AzureConfig azureConfig, String subscriptionId) {
    try {
      ApplicationTokenCredentials credentials =
          new ApplicationTokenCredentials(azureConfig.getClientId(), azureConfig.getTenantId(),
              new String(azureConfig.getKey()), getAzureEnvironment(azureConfig.getAzureEnvironmentType()));

      return Azure.configure().withLogLevel(LogLevel.NONE).authenticate(credentials).withSubscription(subscriptionId);
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }
    return null;
  }
  public final AzureEnvironment getAzureEnvironment(AzureEnvironmentType azureEnvironmentType) {
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
  public void handleAzureAuthenticationException(Exception e) {
    log.error("HandleAzureAuthenticationException: Exception:" + e);

    Throwable e1 = e;
    while (e1.getCause() != null) {
      e1 = e1.getCause();
      if (e1 instanceof AuthenticationException) {
        throw new InvalidRequestException("Invalid Azure credentials.", USER);
      }
    }

    throw new InvalidRequestException("Failed to connect to Azure cluster. " + ExceptionUtils.getMessage(e), USER);
  }
  @Override
  public List<Map<String, String>> getResourceGroups(AzureConfig azureConfig, String subscriptionId) {
    Azure azure = getAzureClient(azureConfig, subscriptionId);
    try {
      List<ResourceGroup> resourceGroupList = azure.resourceGroups().list();
      resourceGroupList.stream().map(HasName::name).collect(Collectors.toSet());
      return null;
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }
    return null;
  }
}
