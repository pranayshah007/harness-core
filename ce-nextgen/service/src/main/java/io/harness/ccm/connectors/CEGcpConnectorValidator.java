/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.connectors;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.gcpccm.GcpCloudCostConnectorDTO;
import io.harness.ng.core.dto.ErrorDetail;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.gax.paging.Page;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.TestIamPermissionsRequest;
import com.google.api.services.cloudresourcemanager.model.TestIamPermissionsResponse;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryException;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Dataset;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Singleton
@OwnedBy(HarnessTeam.CE)
public class CEGcpConnectorValidator extends io.harness.ccm.connectors.AbstractCEConnectorValidator {
  @Inject CENextGenConfiguration configuration;
  @Inject CEConnectorsHelper ceConnectorsHelper;

  public static final String GCP_CREDENTIALS_PATH = "CE_GCP_CREDENTIALS_PATH";
  public static final String GCP_BILLING_EXPORT_V_1 = "gcp_billing_export_v1";
  private final String GENERIC_LOGGING_ERROR =
      "Failed to validate accountIdentifier:{} orgIdentifier:{} projectIdentifier:{} connectorIdentifier:{} ";

  public ConnectorValidationResult validate(ConnectorResponseDTO connectorResponseDTO, String accountIdentifier) {
    final GcpCloudCostConnectorDTO gcpCloudCostConnectorDTO =
        (GcpCloudCostConnectorDTO) connectorResponseDTO.getConnector().getConnectorConfig();
    String projectIdentifier = connectorResponseDTO.getConnector().getProjectIdentifier();
    String orgIdentifier = connectorResponseDTO.getConnector().getOrgIdentifier();
    String connectorIdentifier = connectorResponseDTO.getConnector().getIdentifier();
    String projectId = gcpCloudCostConnectorDTO.getProjectId(); // Source project id
    String impersonatedServiceAccount = gcpCloudCostConnectorDTO.getServiceAccountEmail();
    final List<CEFeatures> featuresEnabled = gcpCloudCostConnectorDTO.getFeaturesEnabled();
    final List<ErrorDetail> errorList = new ArrayList<>();
    String datasetId = "";
    String gcpTableName = "";
    if (gcpCloudCostConnectorDTO.getBillingExportSpec() != null) {
      datasetId = gcpCloudCostConnectorDTO.getBillingExportSpec().getDatasetId();
      gcpTableName = gcpCloudCostConnectorDTO.getBillingExportSpec().getTableId();
    }
    if (featuresEnabled.contains(CEFeatures.BILLING)
        && (projectId.isEmpty() || datasetId.isEmpty() || impersonatedServiceAccount.isEmpty())) {
      return ConnectorValidationResult.builder()
          .errorSummary("Invalid connector configuration")
          .errors(ImmutableList.of(ErrorDetail.builder()
                                       .code(400)
                                       .message("Verify if valid projectId/datasetId exists in connector. "
                                           + "For more information, refer to the documentation.")
                                       .reason("Invalid connector configuration")
                                       .build()))
          .status(ConnectivityStatus.FAILURE)
          .build();
    }
    CloudResourceManager service = null;
    try {
      if (!configuration.getCeGcpSetupConfig().isEnableServiceAccountPermissionsCheck()) {
        log.info("Service-account permissions check is disabled in config.");
      } else {
        service = createCloudResourceManagerService(impersonatedServiceAccount);
      }
    } catch (IOException | GeneralSecurityException e) {
      log.error("Unable to initialize Cloud-Resource-Manager Service: ", e);
      errorList.add(ErrorDetail.builder()
                        .reason("Failed to test required permissions for service account " + impersonatedServiceAccount)
                        .message("") // UI adds "Contact Harness Support or Harness Community Forum." in this case
                        .code(400)
                        .build());
      return ConnectorValidationResult.builder()
          .errorSummary("Failed to test required permissions for service account " + impersonatedServiceAccount)
          .errors(errorList)
          .status(ConnectivityStatus.FAILURE)
          .build();
    }
    try {
      if (configuration.getCeGcpSetupConfig().isEnableServiceAccountPermissionsCheck()) {
        Set<String> requiredPermissions = new HashSet<>();
        if (featuresEnabled.contains(CEFeatures.VISIBILITY)) {
          requiredPermissions.addAll(getRequiredPermissionsForVisibility());
        }
        if (featuresEnabled.contains(CEFeatures.OPTIMIZATION)) {
          requiredPermissions.addAll(getRequiredPermissionsForOptimization());
        }
        ConnectorValidationResult permissionsValidationResult = validatePermissionsList(
            service, projectId, new ArrayList<>(requiredPermissions), impersonatedServiceAccount);
        if (permissionsValidationResult != null) {
          return permissionsValidationResult;
        }
      }
      if (featuresEnabled.contains(CEFeatures.BILLING)) {
        ConnectorValidationResult connectorValidationResult =
            validateAccessToBillingReport(projectId, datasetId, gcpTableName, impersonatedServiceAccount);
        if (connectorValidationResult != null) {
          return connectorValidationResult;
        } else {
          // 4. Check for data at destination only when 24 hrs have elapsed since connector last modified at
          long now = Instant.now().toEpochMilli() - 24 * 60 * 60 * 1000;
          if (connectorResponseDTO.getCreatedAt() < now) {
            if (featuresEnabled.contains(CEFeatures.BILLING)
                && !ceConnectorsHelper.isDataSyncCheck(accountIdentifier, connectorIdentifier,
                    ConnectorType.GCP_CLOUD_COST, ceConnectorsHelper.JOB_TYPE_CLOUDFUNCTION)) {
              log.error("Error with processing data"); // Used for log based metrics
              errorList.add(
                  ErrorDetail.builder()
                      .reason("Internal error with data processing")
                      .message("") // UI adds "Contact Harness Support or Harness Community Forum." in this case
                      .code(500)
                      .build());
              return ConnectorValidationResult.builder()
                  .errorSummary("Error with processing data")
                  .errors(errorList)
                  .status(ConnectivityStatus.FAILURE)
                  .build();
            }
          }
        }
      }
    } catch (Exception ex) {
      // 5. Generic Error
      log.error(GENERIC_LOGGING_ERROR, accountIdentifier, orgIdentifier, projectIdentifier, connectorIdentifier, ex);
      errorList.add(ErrorDetail.builder().reason("Unknown error occurred").message("").build());
      return ConnectorValidationResult.builder()
          .errorSummary("Unknown error occurred")
          .errors(errorList)
          .status(ConnectivityStatus.FAILURE)
          .build();
    }

    log.info("Validation successful for connector {}", connectorIdentifier);
    return ConnectorValidationResult.builder()
        .status(ConnectivityStatus.SUCCESS)
        .testedAt(Instant.now().toEpochMilli())
        .build();
  }

  private CloudResourceManager createCloudResourceManagerService(String impersonatedServiceAccount)
      throws GeneralSecurityException, IOException {
    GoogleCredentials googleCredentials;
    boolean usingWorkloadIdentity = Boolean.parseBoolean(System.getenv("USE_WORKLOAD_IDENTITY"));
    if (!usingWorkloadIdentity) {
      log.info("WI: Using JSON key file");
      googleCredentials = getGcpCredentials(GCP_CREDENTIALS_PATH);
    } else {
      log.info("WI: Using Google ADC");
      googleCredentials = GoogleCredentials.getApplicationDefault();
    }
    if (googleCredentials == null) {
      return null;
    }
    Credentials credentials = getGcpImpersonatedCredentials(googleCredentials, impersonatedServiceAccount);

    return new CloudResourceManager
        .Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(),
            new HttpCredentialsAdapter(credentials))
        .setApplicationName("service-accounts")
        .build();
  }

  public ConnectorValidationResult validatePermissionsList(
      CloudResourceManager service, String projectId, List<String> permissionsList, String impersonatedServiceAccount) {
    if (permissionsList.isEmpty()) {
      return null;
    }
    final List<ErrorDetail> errorList = new ArrayList<>();
    TestIamPermissionsRequest requestBody = new TestIamPermissionsRequest().setPermissions(permissionsList);
    try {
      TestIamPermissionsResponse testIamPermissionsResponse =
          service.projects().testIamPermissions(projectId, requestBody).execute();

      if (testIamPermissionsResponse.getPermissions() != null
          && testIamPermissionsResponse.getPermissions().containsAll(permissionsList)) {
        return null;
      }

      List<String> missingPermissions = new ArrayList<>(permissionsList);
      if (testIamPermissionsResponse.getPermissions() != null) {
        missingPermissions.removeAll(testIamPermissionsResponse.getPermissions());
      }
      log.error("Some required permissions were found to be missing for service account {}. {}",
          impersonatedServiceAccount, missingPermissions);
      for (String missingPermission : missingPermissions) {
        errorList.add(ErrorDetail.builder()
                          .reason(missingPermission + " permission was found to be missing for service account "
                              + impersonatedServiceAccount)
                          .message("Review GCP access permissions as per the documentation.")
                          .code(403)
                          .build());
      }
      return ConnectorValidationResult.builder()
          .errorSummary(
              "Some required permissions were found to be missing for service account " + impersonatedServiceAccount)
          .errors(errorList)
          .status(ConnectivityStatus.FAILURE)
          .testedAt(Instant.now().toEpochMilli())
          .build();
    } catch (IOException e) {
      log.error("Failed to test required permissions", e);
      errorList.add(ErrorDetail.builder()
                        .reason("Failed to test required permissions")
                        .message("") // UI adds "Contact Harness Support or Harness Community Forum." in this case
                        .build());
      return ConnectorValidationResult.builder()
          .errorSummary("Failed to test required permissions for service account " + impersonatedServiceAccount)
          .errors(errorList)
          .status(ConnectivityStatus.FAILURE)
          .build();
    }
  }

  public List<String> getRequiredPermissionsForVisibility() {
    return Arrays.asList(
        "compute.disks.list", "compute.instances.list", "compute.regions.list", "compute.snapshots.list");
  }

  public List<String> getRequiredPermissionsForOptimization() {
    return Arrays.asList("compute.addresses.create", "compute.addresses.createInternal", "compute.addresses.delete",
        "compute.addresses.deleteInternal", "compute.addresses.get", "compute.addresses.list",
        "compute.addresses.setLabels", "compute.addresses.use", "compute.addresses.useInternal",
        "compute.autoscalers.create", "compute.autoscalers.delete", "compute.autoscalers.get",
        "compute.autoscalers.list", "compute.autoscalers.update", "compute.instanceGroupManagers.create",
        "compute.instanceGroupManagers.delete", "compute.instanceGroupManagers.get",
        "compute.instanceGroupManagers.list", "compute.instanceGroupManagers.update",
        "compute.instanceGroupManagers.use", "compute.instanceGroups.create", "compute.instanceGroups.delete",
        "compute.instanceGroups.get", "compute.instanceGroups.list", "compute.instanceGroups.update",
        "compute.instanceGroups.use", "compute.instances.addAccessConfig", "compute.instances.attachDisk",
        "compute.instances.create", "compute.instances.createTagBinding", "compute.instances.delete",
        "compute.instances.deleteAccessConfig", "compute.instances.deleteTagBinding", "compute.instances.detachDisk",
        "compute.instances.get", "compute.instances.getEffectiveFirewalls", "compute.instances.getIamPolicy",
        "compute.instances.getSerialPortOutput", "compute.instances.list", "compute.instances.listEffectiveTags",
        "compute.instances.listTagBindings", "compute.instances.osAdminLogin", "compute.instances.osLogin",
        "compute.instances.removeResourcePolicies", "compute.instances.reset", "compute.instances.resume",
        "compute.instances.sendDiagnosticInterrupt", "compute.instances.setDeletionProtection",
        "compute.instances.setDiskAutoDelete", "compute.instances.setIamPolicy", "compute.instances.setLabels",
        "compute.instances.setMachineResources", "compute.instances.setMachineType", "compute.instances.setMetadata",
        "compute.instances.setMinCpuPlatform", "compute.instances.setScheduling", "compute.instances.setServiceAccount",
        "compute.instances.setShieldedInstanceIntegrityPolicy", "compute.instances.setShieldedVmIntegrityPolicy",
        "compute.instances.setTags", "compute.instances.start", "compute.instances.stop", "compute.instances.suspend",
        "compute.instances.update", "compute.instances.updateAccessConfig", "compute.instances.updateDisplayDevice",
        "compute.instances.updateNetworkInterface", "compute.instances.updateSecurity",
        "compute.instances.updateShieldedInstanceConfig", "compute.instances.updateShieldedVmConfig",
        "compute.instances.use", "compute.instances.useReadOnly", "compute.machineTypes.list",
        "compute.networks.access", "compute.networks.get", "compute.networks.getEffectiveFirewalls",
        "compute.networks.getRegionEffectiveFirewalls", "compute.networks.list", "compute.networks.mirror",
        "compute.regions.get", "compute.regions.list", "secretmanager.versions.access");
  }

  public ConnectorValidationResult validateAccessToBillingReport(
      String projectId, String datasetId, String gcpTableName, String impersonatedServiceAccount) throws IOException {
    boolean isTablePresent = false;
    final List<ErrorDetail> errorList = new ArrayList<>();
    Table tableGranularData = null;
    GoogleCredentials googleCredentials;
    boolean usingWorkloadIdentity = Boolean.parseBoolean(System.getenv("USE_WORKLOAD_IDENTITY"));
    if (!usingWorkloadIdentity) {
      log.info("WI: Using JSON key file");
      googleCredentials = getGcpCredentials(GCP_CREDENTIALS_PATH);
    } else {
      log.info("WI: Using Google ADC");
      googleCredentials = GoogleCredentials.getApplicationDefault();
    }
    Credentials credentials = getGcpImpersonatedCredentials(googleCredentials, impersonatedServiceAccount);
    BigQuery bigQuery;
    BigQueryOptions.Builder bigQueryOptionsBuilder = BigQueryOptions.newBuilder().setCredentials(credentials);
    log.info("projectId '{}', datasetId '{}', impersonatedServiceAccount '{}'", projectId, datasetId,
        impersonatedServiceAccount);
    if (projectId != null) {
      bigQueryOptionsBuilder.setProjectId(projectId);
    }
    bigQuery = bigQueryOptionsBuilder.build().getService();

    try {
      // 1. Check presence of dataset
      Dataset dataset = bigQuery.getDataset(datasetId);
      if (dataset == null) {
        log.error("Unable to find the dataset :" + datasetId);
        errorList.add(
            ErrorDetail.builder()
                .reason("Dataset doesnt exists or service account does not have permissions")
                .message("Please check if dataset " + datasetId + " and project " + projectId
                    + " exists and service account " + impersonatedServiceAccount + " has required permissions"
                    + "For more information, refer to the documentation.")
                .build());
        return ConnectorValidationResult.builder()
            .status(ConnectivityStatus.FAILURE)
            .errors(errorList)
            .errorSummary("Unable to find the dataset " + datasetId + " in project " + projectId)
            .testedAt(Instant.now().toEpochMilli())
            .build();
      } else {
        // 2. Check presence of table "gcp_billing_export_v1_*"
        log.info("dataset '{}' is present in gcp project '{}'", datasetId, projectId);
        if (isEmpty(gcpTableName)) {
          Page<Table> tableList = dataset.list(BigQuery.TableListOption.pageSize(1000));
          for (Table table : tableList.getValues()) {
            if (table.getTableId().getTable().contains(GCP_BILLING_EXPORT_V_1)) {
              isTablePresent = true;
              gcpTableName = table.getTableId().getTable();
              break;
            }
          }
        }
        TableId tableIdBq = TableId.of(projectId, datasetId, gcpTableName);
        tableGranularData = bigQuery.getTable(tableIdBq);
        if (tableGranularData != null) {
          isTablePresent = true;
        }

        if (!isTablePresent) {
          errorList.add(ErrorDetail.builder()
                            .reason("Pricing table at source not found")
                            .message("If you are setting up pricing export to BigQuery for the first time,  "
                                + "it might take up to 48 hours to start seeing your Google Cloud pricing data."
                                + " For more information, refer to the documentation.")
                            .build());
          return ConnectorValidationResult.builder()
              .status(ConnectivityStatus.PARTIAL)
              .errors(errorList)
              .errorSummary("Billing export table " + gcpTableName + " is not found in the dataset " + datasetId
                  + " in GCP project " + projectId)
              .testedAt(Instant.now().toEpochMilli())
              .build();
        } else {
          log.info("table {} is present in GCP project {} in Dataset {}", gcpTableName, projectId, datasetId);
          // Check when this table was last modified on
          Long lastModifiedTime = tableGranularData.getLastModifiedTime();
          lastModifiedTime = lastModifiedTime != null ? lastModifiedTime : tableGranularData.getCreationTime();
          // Check for data at source only when 24 hrs have elapsed since connector last modified at
          long now = Instant.now().toEpochMilli() - 24 * 60 * 60 * 1000;
          if (lastModifiedTime < now) {
            return ConnectorValidationResult.builder()
                .status(ConnectivityStatus.FAILURE)
                .errors(ImmutableList.of(ErrorDetail.builder()
                                             .reason("Billing export configuration might have changed")
                                             .message("Check the billing export configuration in your GCP Project."
                                                 + " For more information, refer to the documentation.")
                                             .build()))
                .errorSummary("Billing table " + gcpTableName + " is not updated in the last 24 hrs.")
                .testedAt(Instant.now().toEpochMilli())
                .build();
          }
        }
      }
    } catch (BigQueryException be) {
      // 3. Permissions check on the dataset
      String message = "Please verify your billing export config in your GCP account and in the CCM connector."
          + " For more information, refer to the documentation.";
      log.error("Unable to access BigQuery Dataset", be);
      if (!be.getMessage().isEmpty() && be.getMessage().contains("has not enabled BigQuery")) {
        message = "Enable BigQuery in project " + projectId + ". For more information, refer to the documentation.";
      } else if (!be.getMessage().isEmpty() && be.getMessage().contains("Error requesting access token")) {
        message = "Verify if the service account " + impersonatedServiceAccount
            + " exists. For more information, refer to the documentation.";
      } else if (!be.getMessage().isEmpty() && be.getMessage().contains("Access Denied")) {
        message = "Provide 'BigQuery Data Viewer' role to service account " + impersonatedServiceAccount
            + "on the dataset. For more information, refer to the documentation.";
      }
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.FAILURE)
          .errors(ImmutableList.of(
              ErrorDetail.builder().code(be.getCode()).reason(be.getMessage()).message(message).build()))
          .errorSummary("Unable to access the dataset " + datasetId + " in project " + projectId)
          .testedAt(Instant.now().toEpochMilli())
          .build();
    }
    return null;
  }

  public GoogleCredentials getGcpCredentials(String googleCredentialPathSystemEnv) {
    String googleCredentialsPath = System.getenv(googleCredentialPathSystemEnv);
    if (isEmpty(googleCredentialsPath)) {
      log.error("Missing environment variable for GCP credentials.");
    }
    File credentialsFile = new File(googleCredentialsPath);
    ServiceAccountCredentials credentials = null;
    try (FileInputStream serviceAccountStream = new FileInputStream(credentialsFile)) {
      credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
    } catch (FileNotFoundException e) {
      log.error("Failed to find Google credential file for the CE service account in the specified path.", e);
    } catch (IOException e) {
      log.error("Failed to get Google credential file for the CE service account.", e);
    }
    return credentials;
  }

  public Credentials getGcpImpersonatedCredentials(
      GoogleCredentials sourceCredentials, String impersonatedServiceAccount) {
    if (impersonatedServiceAccount == null) {
      return sourceCredentials;
    } else {
      return ImpersonatedCredentials.create(sourceCredentials, impersonatedServiceAccount, null,
          Arrays.asList("https://www.googleapis.com/auth/cloud-platform"), 300);
    }
  }
}
