/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.connectors;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.utility.AzureUtils;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceazure.BillingExportSpecDTO;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;
import io.harness.ng.core.dto.ErrorDetail;

import com.azure.core.http.HttpClient;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.authorization.models.RoleAssignment;
import com.azure.resourcemanager.authorization.models.RoleDefinition;
import com.azure.resourcemanager.authorization.models.ServicePrincipal;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.aad.msal4j.MsalServiceException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Singleton
@OwnedBy(HarnessTeam.CE)
public class CEAzureConnectorValidator extends io.harness.ccm.connectors.AbstractCEConnectorValidator {
  private final String AZURE_STORAGE_SUFFIX = "blob.core.windows.net";
  private final String AZURE_STORAGE_URL_FORMAT = "https://%s.%s";
  private final String GENERIC_LOGGING_ERROR =
      "Failed to validate accountIdentifier:{} orgIdentifier:{} projectIdentifier:{} connectorIdentifier:{} ";
  private final String AZURE_RBAC_READER_ROLE = "Reader";
  private final String AZURE_RBAC_CONTRIBUTOR_ROLE = "Contributor";

  @Inject CENextGenConfiguration configuration;
  @Inject CEConnectorsHelper ceConnectorsHelper;

  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier) {
    final CEAzureConnectorDTO ceAzureConnectorDTO =
        (CEAzureConnectorDTO) connectorResponseDTO.getConnector().getConnectorConfig();
    final List<CEFeatures> featuresEnabled = ceAzureConnectorDTO.getFeaturesEnabled();
    String projectIdentifier = connectorResponseDTO.getConnector().getProjectIdentifier();
    String orgIdentifier = connectorResponseDTO.getConnector().getOrgIdentifier();
    String connectorIdentifier = connectorResponseDTO.getConnector().getIdentifier();
    long oneDayOld = Instant.now().toEpochMilli() - 24 * 60 * 60 * 1000;
    String storageAccountName;
    String containerName;
    String directoryName;
    String reportName;
    String prefix;
    String tenantId = ceAzureConnectorDTO.getTenantId();
    String subscriptionId = ceAzureConnectorDTO.getSubscriptionId();
    final List<ErrorDetail> errorList = new ArrayList<>();
    try {
      if (featuresEnabled.contains(CEFeatures.BILLING)) {
        BillingExportSpecDTO billingExportSpec = ceAzureConnectorDTO.getBillingExportSpec();
        if (billingExportSpec == null) {
          log.error("No billing export spec found for this connector {}", connectorIdentifier);
          return ConnectorValidationResult.builder()
              .status(ConnectivityStatus.FAILURE)
              .errors(ImmutableList.of(
                  ErrorDetail.builder()
                      .reason("No billing export spec found")
                      .message(
                          "Verify the billing export configuration in Harness and in your Azure account. For more information, refer to the documentation.")
                      .build()))
              .errorSummary("Invalid connector configuration")
              .testedAt(Instant.now().toEpochMilli())
              .build();
        }
        storageAccountName = billingExportSpec.getStorageAccountName();
        containerName = billingExportSpec.getContainerName();
        directoryName = billingExportSpec.getDirectoryName();
        reportName = billingExportSpec.getReportName();
        prefix = directoryName + "/" + reportName + "/" + ceConnectorsHelper.getReportMonth();
        String endpoint = String.format(AZURE_STORAGE_URL_FORMAT, storageAccountName, AZURE_STORAGE_SUFFIX);
        BlobContainerClient blobContainerClient = getBlobContainerClient(endpoint, containerName, tenantId);
        errorList.addAll(validateIfFileIsPresent(getBlobItems(blobContainerClient, prefix), prefix));
        if (!errorList.isEmpty()) {
          return ConnectorValidationResult.builder()
              .status(ConnectivityStatus.FAILURE)
              .errors(errorList)
              .errorSummary("No billing export file is found in last 24hrs in " + prefix)
              .testedAt(Instant.now().toEpochMilli())
              .build();
        }
      }

      List<String> requiredRoles = new ArrayList<>();
      if (featuresEnabled.contains(CEFeatures.OPTIMIZATION) || featuresEnabled.contains(CEFeatures.GOVERNANCE)) {
        requiredRoles.add(AZURE_RBAC_CONTRIBUTOR_ROLE);
      } else if (featuresEnabled.contains(CEFeatures.VISIBILITY)) {
        requiredRoles.add(AZURE_RBAC_READER_ROLE);
      }
      errorList.addAll(validateServiceAccountPermissions(tenantId, subscriptionId, requiredRoles));
      if (!errorList.isEmpty()) {
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errors(errorList)
            .errorSummary("Authorization permission mismatch")
            .testedAt(Instant.now().toEpochMilli())
            .build();
      }

    } catch (BlobStorageException ex) {
      log.error("Error", ex);
      if (ex.getErrorCode().toString().equals("ContainerNotFound")) {
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errors(ImmutableList.of(ErrorDetail.builder()
                                         .code(ex.getStatusCode())
                                         .reason("Container not found")
                                         .message("Review the billing export settings in your Azure account."
                                             + " For more information, refer to the documentation.")
                                         .build()))
            .errorSummary("The specified container does not exist")
            .testedAt(Instant.now().toEpochMilli())
            .build();
      } else if (ex.getErrorCode().toString().equals("AuthorizationPermissionMismatch")) {
        // TODO: Review this when autostopping validations are performed.
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errors(ImmutableList.of(
                ErrorDetail.builder()
                    .code(ex.getStatusCode())
                    .reason("Missing required permissions on the storage account")
                    .message(
                        "Review the permissions on your storage account. For more information, refer to the documentation.")
                    .build()))
            .errorSummary("Authorization permission mismatch")
            .testedAt(Instant.now().toEpochMilli())
            .build();
      }
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.FAILURE)
          .errors(ImmutableList.of(
              ErrorDetail.builder()
                  .code(ex.getStatusCode())
                  .reason(ex.getMessage())
                  .message("Review the billing export settings in your Azure account and in CCM connector."
                      + " For more information, refer to the documentation.")
                  .build()))
          .errorSummary("Exception while validating storage account details")
          .testedAt(Instant.now().toEpochMilli())
          .build();
    } catch (MsalServiceException ex) {
      log.error("Error", ex);
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.FAILURE)
          .errors(ImmutableList.of(
              ErrorDetail.builder()
                  .code(ex.statusCode())
                  .reason("Incorrect tenantID.")
                  .message(
                      "Verify if you have entered the correct tenant ID in CCM Connector. For more information, refer to the documentation.")
                  .build()))
          .errorSummary("The specified tenantID does not exist")
          .testedAt(Instant.now().toEpochMilli())
          .build();
    } catch (Exception ex) {
      log.error("Error", ex);
      if (ex.getCause() instanceof UnknownHostException) {
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errors(ImmutableList.of(
                ErrorDetail.builder()
                    .reason("Incorrect storage account.")
                    .message(
                        "Verify if you have entered the correct storage account in CCM Connector. For more information, refer to the documentation.")
                    .build()))
            .errorSummary("The specified storage account does not exist")
            .testedAt(Instant.now().toEpochMilli())
            .build();
      } else if (ex.getMessage() != null && ex.getMessage().startsWith("No billing export file")) {
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errors(ImmutableList.of(
                ErrorDetail.builder()
                    .message(
                        "Verify the billing export configuration in Harness and in your Azure account. For more information, refer to the documentation.")
                    .reason("")
                    .build()))
            .errorSummary(ex.getMessage())
            .testedAt(Instant.now().toEpochMilli())
            .build();
      } else {
        log.error(GENERIC_LOGGING_ERROR, accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, ex);
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errors(ImmutableList.of(ErrorDetail.builder()
                                         .reason("Unknown error while validating billing export details")
                                         .message("Contact Harness Support or Harness Community Forum.")
                                         .build()))
            .errorSummary("Unknown error")
            .testedAt(Instant.now().toEpochMilli())
            .build();
      }
    }

    // Check for data at destination only when 24 hrs have elapsed since connector last modified at
    if (connectorResponseDTO.getCreatedAt() < oneDayOld) {
      if (featuresEnabled.contains(CEFeatures.BILLING)
          && !ceConnectorsHelper.isDataSyncCheck(accountIdentifier, connectorIdentifier, ConnectorType.CE_AZURE,
              ceConnectorsHelper.JOB_TYPE_CLOUDFUNCTION)) {
        // Issue with CFs
        log.error("Error with processing data"); // Used for log based metrics
        return ConnectorValidationResult.builder()
            .errors(ImmutableList.of(
                ErrorDetail.builder()
                    .reason("Internal error with data processing")
                    .message("") // UI adds "Contact Harness Support or Harness Community Forum." in this case
                    .code(500)
                    .build()))
            .errorSummary("Error with processing data")
            .status(ConnectivityStatus.FAILURE)
            .build();
      }
    }
    log.info("Validation successful for connector {}", connectorIdentifier);
    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.SUCCESS)
        .testedAt(Instant.now().toEpochMilli())
        .build();
  }

  public Collection<ErrorDetail> validateIfFileIsPresent(
      com.azure.core.http.rest.PagedIterable<BlobItem> blobItems, String prefix) {
    List<ErrorDetail> errorDetails = new ArrayList<>();
    if (!configuration.getCeAzureSetupConfig().isEnableFileCheckAtSource()) {
      log.info("File present check is disabled in config.");
      return errorDetails;
    }
    String latestFileName = "";
    Instant latestFileLastModifiedTime = Instant.EPOCH;
    Instant lastModifiedTime;
    // Caveat: This can be slow for some accounts.
    for (BlobItem blobItem : blobItems) {
      lastModifiedTime = Instant.from(blobItem.getProperties().getLastModified());
      if (blobItem.getName().endsWith(".csv")) {
        if (lastModifiedTime.compareTo(latestFileLastModifiedTime) > 0) {
          latestFileLastModifiedTime = lastModifiedTime;
          latestFileName = blobItem.getName();
        }
      }
    }
    log.info("Latest .csv.gz file in {} latestFileName: {} latestFileLastModifiedTime: {}", prefix, latestFileName,
        latestFileLastModifiedTime);
    if (latestFileLastModifiedTime.getEpochSecond() < (Instant.now().getEpochSecond() - 24 * 60 * 60)) {
      String reason = String.format("No billing export csv file is found in last 24 hrs at %s. ", prefix);
      errorDetails.add(
          ErrorDetail.builder()
              .reason(reason)
              .message(
                  "Verify the billing export configuration in CCM and in your Azure account. For more information, refer to the documentation.")
              .code(403)
              .build());
    }
    return errorDetails;
  }

  public List<ErrorDetail> validateServiceAccountPermissions(
      String tenantId, String subscriptionId, List<String> requiredRoles) {
    List<ErrorDetail> errorDetails = new ArrayList<>();
    try {
      AzureProfile profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
      HttpClient httpClient = AzureUtils.getAzureHttpClient();
      ClientSecretCredential clientSecretCredential =
          new ClientSecretCredentialBuilder()
              .clientId(configuration.getCeAzureSetupConfig().getAzureAppClientId())
              .clientSecret(configuration.getCeAzureSetupConfig().getAzureAppClientSecret())
              .tenantId(profile.getTenantId())
              .httpClient(httpClient)
              .build();

      AzureResourceManager azureResourceManager =
          AzureResourceManager
              .authenticate(
                  AzureUtils.getAzureHttpPipeline(clientSecretCredential, profile,
                      AzureUtils.getRetryPolicy(AzureUtils.getRetryOptions(AzureUtils.getDefaultDelayOptions())),
                      httpClient),
                  profile)
              .withSubscription(subscriptionId);

      ServicePrincipal servicePrincipal = azureResourceManager.accessManagement().servicePrincipals().getByName(
          configuration.getCeAzureSetupConfig().getAzureAppClientId());
      PagedIterable<RoleAssignment> roles =
          azureResourceManager.accessManagement().roleAssignments().listByServicePrincipal(servicePrincipal);

      for (RoleAssignment item : roles) {
        RoleDefinition role =
            azureResourceManager.accessManagement().roleDefinitions().getById(item.roleDefinitionId());
        log.info("ServicePrincipal has role: {}", role.roleName());
        requiredRoles.remove(role.roleName());
      }
    } catch (Exception e) {
      log.info("Exception occurred while fetching list of roles assigned to the service principal: {}", e.getMessage());
    }
    if (!requiredRoles.isEmpty()) {
      String reason = "";
      for (String requiredRole : requiredRoles) {
        reason += String.format("Required role '%s' is not assigned to service principal.%n", requiredRole);
      }
      log.info(reason);
      errorDetails.add(ErrorDetail.builder()
                           .code(403)
                           .reason(reason)
                           .message("Review Azure access permissions as per the documentation.")
                           .build());
    }
    return errorDetails;
  }

  @VisibleForTesting
  public BlobContainerClient getBlobContainerClient(String endpoint, String containerName, String tenantId) {
    ClientSecretCredential clientSecretCredential =
        new ClientSecretCredentialBuilder()
            .clientId(configuration.getCeAzureSetupConfig().getAzureAppClientId())
            .clientSecret(configuration.getCeAzureSetupConfig().getAzureAppClientSecret())
            .tenantId(tenantId)
            .build();
    BlobServiceClient blobServiceClient =
        new BlobServiceClientBuilder().endpoint(endpoint).credential(clientSecretCredential).buildClient();
    return blobServiceClient.getBlobContainerClient(containerName);
  }

  public com.azure.core.http.rest.PagedIterable<BlobItem> getBlobItems(
      BlobContainerClient blobContainerClient, String prefix) {
    return blobContainerClient.listBlobs(new ListBlobsOptions().setPrefix(prefix), null);
  }
}
