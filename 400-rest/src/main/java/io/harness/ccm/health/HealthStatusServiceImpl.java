/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.health;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.cluster.entities.ClusterType.AWS_ECS;
import static io.harness.ccm.cluster.entities.ClusterType.AZURE_KUBERNETES;
import static io.harness.ccm.cluster.entities.ClusterType.DIRECT_KUBERNETES;
import static io.harness.ccm.cluster.entities.ClusterType.GCP_KUBERNETES;
import static io.harness.ccm.health.CEConnectorHealthMessages.AWS_S3_SYNC_MESSAGE;
import static io.harness.ccm.health.CEConnectorHealthMessages.AZURE_STORAGE_SYNC_MESSAGE;
import static io.harness.ccm.health.CEConnectorHealthMessages.BILLING_DATA_PIPELINE_ERROR;
import static io.harness.ccm.health.CEConnectorHealthMessages.BILLING_DATA_PIPELINE_SUCCESS;
import static io.harness.ccm.health.CEConnectorHealthMessages.BILLING_PIPELINE_CREATION_FAILED;
import static io.harness.ccm.health.CEConnectorHealthMessages.BILLING_PIPELINE_CREATION_SUCCESSFUL;
import static io.harness.ccm.health.CEConnectorHealthMessages.SETTING_ATTRIBUTE_CREATED;
import static io.harness.ccm.health.CEConnectorHealthMessages.WAITING_FOR_SUCCESSFUL_AWS_S3_SYNC_MESSAGE;
import static io.harness.ccm.health.CEConnectorHealthMessages.WAITING_FOR_SUCCESSFUL_AZURE_STORAGE_SYNC_MESSAGE;
import static io.harness.ccm.health.CEError.AWS_ECS_CLUSTER_NOT_FOUND;
import static io.harness.ccm.health.CEError.DELEGATE_NOT_AVAILABLE;
import static io.harness.ccm.health.CEError.DELEGATE_NOT_INSTALLED;
import static io.harness.ccm.health.CEError.K8S_PERMISSIONS_MISSING;
import static io.harness.ccm.health.CEError.METRICS_SERVER_NOT_FOUND;
import static io.harness.ccm.health.CEError.NODES_IS_FORBIDDEN;
import static io.harness.ccm.health.CEError.NO_CLUSTERS_TRACKED_BY_HARNESS_CE;
import static io.harness.ccm.health.CEError.NO_ELIGIBLE_DELEGATE;
import static io.harness.ccm.health.CEError.NO_RECENT_EVENTS_PUBLISHED;
import static io.harness.ccm.health.CEError.PERPETUAL_TASK_CREATION_FAILURE;
import static io.harness.ccm.health.CEError.PERPETUAL_TASK_NOT_ASSIGNED;
import static io.harness.ccm.health.CEError.PVC_PERMISSION_ERROR;
import static io.harness.ccm.health.CEError.TASK_VALIDATION_FAILED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.billing.dao.BillingDataPipelineRecordDao;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.commons.entities.batch.LastReceivedPublishedMessage;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.events.CeExceptionRecord;
import io.harness.ccm.config.CCMSettingService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import software.wings.beans.AwsConfig;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.beans.ce.CEAzureConfig;
import software.wings.beans.ce.CEGcpConfig;
import software.wings.service.intfc.SettingsService;
import software.wings.settings.SettingValue;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CE)
public class HealthStatusServiceImpl implements HealthStatusService {
  private static final String SUCCEEDED = "SUCCEEDED";
  @Inject SettingsService settingsService;
  @Inject CCMSettingService ccmSettingService;
  @Inject ClusterRecordService clusterRecordService;
  @Inject PerpetualTaskService perpetualTaskService;
  @Inject LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  @Inject CeExceptionRecordDao ceExceptionRecordDao;
  @Inject BillingDataPipelineRecordDao billingDataPipelineRecordDao;

  private static final String TIMESTAMP_FORMAT_SPECIFIER = "{}";
  private static final String LAST_EVENT_TIMESTAMP_MESSAGE = "Last event collected at %s";
  private static final Long EVENT_TIMESTAMP_RECENCY_THRESHOLD_DEFAULT =
      TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);
  private static final Long EVENT_TIMESTAMP_RECENCY_THRESHOLD_FOR_K8S =
      TimeUnit.MILLISECONDS.convert(30, TimeUnit.MINUTES);
  private static final Long EVENT_TIMESTAMP_RECENCY_THRESHOLD_FOR_AWS =
      TimeUnit.MILLISECONDS.convert(80, TimeUnit.MINUTES);

  @Override
  public CEHealthStatus getHealthStatus(String cloudProviderId) {
    return getHealthStatus(cloudProviderId, true);
  }

  @Override
  public CEHealthStatus getHealthStatus(String cloudProviderId, boolean cloudCostEnabled) {
    SettingAttribute cloudProvider = settingsService.get(cloudProviderId);
    Preconditions.checkNotNull(cloudProvider);
    if (cloudProvider.getCategory() == SettingCategory.CE_CONNECTOR) {
      return getConnectorHealth(cloudProvider);
    }

    if (cloudCostEnabled) {
      Preconditions.checkArgument(ccmSettingService.isCloudCostEnabled(cloudProvider),
          format("The cloud provider with id=%s has CE disabled.", cloudProvider.getUuid()));
    }

    List<ClusterRecord> clusterRecords = clusterRecordService.list(cloudProvider.getAccountId(), null, cloudProviderId);

    if (clusterRecords.isEmpty()) {
      if (cloudProvider.getValue().getType().equals("AWS")) {
        return CEHealthStatus.builder()
            .isHealthy(true)
            .messages(Collections.singletonList(
                format(NO_CLUSTERS_TRACKED_BY_HARNESS_CE.getMessage(), cloudProvider.getName())))
            .build();
      }
      return CEHealthStatus.builder().isHealthy(true).build();
    }

    List<CEClusterHealth> ceClusterHealthList = new ArrayList<>();
    clusterRecords.forEach(clusterRecord -> ceClusterHealthList.add(getClusterHealth(clusterRecord)));

    boolean isHealthy = ceClusterHealthList.stream().allMatch(CEClusterHealth::isHealthy);
    return CEHealthStatus.builder().isHealthy(isHealthy).clusterHealthStatusList(ceClusterHealthList).build();
  }

  private CEHealthStatus getConnectorHealth(SettingAttribute cloudProvider) {
    boolean dataTransferJobStatus;
    boolean preAggregatedJobStatus;
    boolean awsFallbackTableJob = true;
    String s3SyncHealthStatus = WAITING_FOR_SUCCESSFUL_AWS_S3_SYNC_MESSAGE.getMessage();
    String storageSyncHealthStatus = WAITING_FOR_SUCCESSFUL_AZURE_STORAGE_SYNC_MESSAGE.getMessage();
    long lastS3SyncTimestamp = 0L;
    long lastStorageSyncTimestamp = 0L;
    boolean isAWSConnector = false;
    boolean isAzureConnector = false;
    String billingPipelineRecordSettingId = cloudProvider.getUuid();

    if (cloudProvider.getValue() instanceof CEAwsConfig) {
      isAWSConnector = true;
    }

    if (cloudProvider.getValue() instanceof CEAzureConfig) {
      isAzureConnector = true;
    }

    if (cloudProvider.getValue() instanceof CEGcpConfig) {
      CEGcpConfig value = (CEGcpConfig) cloudProvider.getValue();
      billingPipelineRecordSettingId = value.getOrganizationSettingId();
    }

    Instant connectorCreationInstantDayTruncated =
        Instant.ofEpochMilli(cloudProvider.getCreatedAt()).truncatedTo(ChronoUnit.DAYS);
    Instant currentInstantDayTruncated = Instant.now().truncatedTo(ChronoUnit.DAYS);

    long timeDifference =
        currentInstantDayTruncated.toEpochMilli() - connectorCreationInstantDayTruncated.toEpochMilli();

    BillingDataPipelineRecord billingDataPipelineRecord = billingDataPipelineRecordDao.fetchBillingPipelineRecord(
        cloudProvider.getAccountId(), billingPipelineRecordSettingId);

    if (billingDataPipelineRecord == null) {
      // Case just after creating the connector.
      return serialiseHealthStatusToPOJO(
          true, Collections.singletonList(SETTING_ATTRIBUTE_CREATED.getMessage()), lastS3SyncTimestamp);
    }

    if (isAWSConnector && billingDataPipelineRecord.getLastSuccessfulS3Sync() != null) {
      s3SyncHealthStatus = getS3SyncHealthStatus(billingDataPipelineRecord);
      lastS3SyncTimestamp = billingDataPipelineRecord.getLastSuccessfulS3Sync().toEpochMilli();
    }

    if (isAzureConnector && billingDataPipelineRecord.getLastSuccessfulStorageSync() != null) {
      storageSyncHealthStatus = getStorageSyncHealthStatus(billingDataPipelineRecord);
      lastStorageSyncTimestamp = billingDataPipelineRecord.getLastSuccessfulStorageSync().toEpochMilli();
    }

    if (timeDifference == ChronoUnit.DAYS.getDuration().toMillis()) {
      boolean isBillingDataPipelineCreationSuccessful = billingDataPipelineRecord.getDataSetId() != null;
      List<String> messages = new ArrayList<>();
      messages.add(isBillingDataPipelineCreationSuccessful ? BILLING_PIPELINE_CREATION_SUCCESSFUL.getMessage()
                                                           : BILLING_PIPELINE_CREATION_FAILED.getMessage());
      if (isAWSConnector) {
        messages.add(s3SyncHealthStatus);
        return serialiseHealthStatusToPOJO(isBillingDataPipelineCreationSuccessful, messages, lastS3SyncTimestamp);
      }
      if (isAzureConnector) {
        messages.add(storageSyncHealthStatus);
        return serialiseHealthStatusToPOJO(isBillingDataPipelineCreationSuccessful, messages, lastStorageSyncTimestamp);
      }
      return serialiseHealthStatusToPOJO(isBillingDataPipelineCreationSuccessful, messages, lastS3SyncTimestamp);
    }

    if (billingDataPipelineRecord.getDataSetId() == null) {
      List<String> messages = new ArrayList<>();
      messages.add(BILLING_PIPELINE_CREATION_FAILED.getMessage());
      if (isAWSConnector) {
        messages.add(s3SyncHealthStatus);
        return serialiseHealthStatusToPOJO(true, messages, lastS3SyncTimestamp);
      }
      if (isAzureConnector) {
        messages.add(storageSyncHealthStatus);
        return serialiseHealthStatusToPOJO(true, messages, lastStorageSyncTimestamp);
      }
    }

    dataTransferJobStatus = billingDataPipelineRecord.getDataTransferJobStatus().equals(SUCCEEDED);
    preAggregatedJobStatus = billingDataPipelineRecord.getPreAggregatedScheduledQueryStatus().equals(SUCCEEDED);
    if (isAWSConnector) {
      awsFallbackTableJob = billingDataPipelineRecord.getAwsFallbackTableScheduledQueryStatus().equals(SUCCEEDED);
    }

    boolean healthStatus = dataTransferJobStatus && preAggregatedJobStatus && awsFallbackTableJob;
    List<String> messages = new ArrayList<>();
    messages.add(healthStatus ? BILLING_DATA_PIPELINE_SUCCESS.getMessage() : BILLING_DATA_PIPELINE_ERROR.getMessage());
    if (isAWSConnector) {
      messages.add(s3SyncHealthStatus);
      return serialiseHealthStatusToPOJO(healthStatus, messages, lastS3SyncTimestamp);
    }
    if (isAzureConnector) {
      messages.add(storageSyncHealthStatus);
      return serialiseHealthStatusToPOJO(healthStatus, messages, lastStorageSyncTimestamp);
    }
    return serialiseHealthStatusToPOJO(healthStatus, messages, lastS3SyncTimestamp);
  }

  private CEHealthStatus serialiseHealthStatusToPOJO(
      boolean healthStatus, List<String> messages, long lastS3SyncMillis) {
    return CEHealthStatus.builder()
        .isHealthy(healthStatus)
        .isCEConnector(true)
        .clusterHealthStatusList(Collections.singletonList(CEClusterHealth.builder()
                                                               .isHealthy(healthStatus)
                                                               .lastEventTimestamp(lastS3SyncMillis)
                                                               .messages(messages)
                                                               .build()))
        .build();
  }

  private String getS3SyncHealthStatus(BillingDataPipelineRecord billingDataPipelineRecord) {
    String s3SyncHealthStatus = WAITING_FOR_SUCCESSFUL_AWS_S3_SYNC_MESSAGE.getMessage();

    Instant lastSuccessfulS3SyncInstant = billingDataPipelineRecord.getLastSuccessfulS3Sync();
    if (!lastSuccessfulS3SyncInstant.equals(Instant.MIN)) {
      s3SyncHealthStatus = AWS_S3_SYNC_MESSAGE.getMessage();
    }
    return s3SyncHealthStatus;
  }

  private String getStorageSyncHealthStatus(BillingDataPipelineRecord billingDataPipelineRecord) {
    String storageSyncHealthStatus = WAITING_FOR_SUCCESSFUL_AZURE_STORAGE_SYNC_MESSAGE.getMessage();

    Instant lastSuccessfulStorageSyncInstant = billingDataPipelineRecord.getLastSuccessfulStorageSync();
    if (!lastSuccessfulStorageSyncInstant.equals(Instant.MIN)) {
      storageSyncHealthStatus = AZURE_STORAGE_SYNC_MESSAGE.getMessage();
    }
    return storageSyncHealthStatus;
  }

  private CEClusterHealth getClusterHealth(ClusterRecord clusterRecord) {
    List<CEError> errors = getErrors(clusterRecord);
    return CEClusterHealth.builder()
        .isHealthy(isEmpty(errors))
        .clusterId(clusterRecord.getUuid())
        .clusterName(clusterRecord.getCluster().getClusterName())
        .clusterRecord(clusterRecord)
        .messages(getMessages(clusterRecord, errors))
        .lastEventTimestamp(getLastEventTimestamp(clusterRecord.getAccountId(), clusterRecord.getUuid()))
        .build();
  }

  private List<CEError> getErrors(ClusterRecord clusterRecord) {
    List<CEError> errors = new ArrayList<>();
    if (null == clusterRecord.getPerpetualTaskIds()) {
      errors.add(PERPETUAL_TASK_CREATION_FAILURE);
      return errors;
    }
    String[] perpetualTaskIds = clusterRecord.getPerpetualTaskIds();
    for (String taskId : perpetualTaskIds) {
      PerpetualTaskRecord perpetualTaskRecord = perpetualTaskService.getTaskRecord(taskId);
      if (null == perpetualTaskRecord) {
        continue;
      }
      String delegateId = perpetualTaskRecord.getDelegateId();
      if (isNullOrEmpty(delegateId)) {
        if (perpetualTaskRecord.getUnassignedReason() == null) {
          errors.add(PERPETUAL_TASK_NOT_ASSIGNED);
        } else {
          switch (perpetualTaskRecord.getUnassignedReason()) {
            case NO_DELEGATE_AVAILABLE:
              errors.add(DELEGATE_NOT_AVAILABLE);
              break;
            case NO_ELIGIBLE_DELEGATES:
              errors.add(NO_ELIGIBLE_DELEGATE);
              break;
            case NO_DELEGATE_INSTALLED:
              errors.add(DELEGATE_NOT_INSTALLED);
              break;
            case VALIDATION_TASK_FAILED:
              errors.add(TASK_VALIDATION_FAILED);
              break;
            default:
              log.warn("Unexpected perpetual task state:{} for empty delegate id",
                  perpetualTaskRecord.getUnassignedReason());
          }
        }
      }
    }

    long recentTimestamp = Instant.now().minus(3, ChronoUnit.MINUTES).toEpochMilli();
    CeExceptionRecord ceExceptionRecord =
        ceExceptionRecordDao.getRecentException(clusterRecord.getAccountId(), clusterRecord.getUuid(), recentTimestamp);

    if (ceExceptionRecord != null) {
      String exceptionMessage = ceExceptionRecord.getMessage();
      if (exceptionMessage.contains("Service: AmazonECS; Status Code: 400; Error Code: ClusterNotFoundException;")) {
        errors.add(AWS_ECS_CLUSTER_NOT_FOUND);
      } else {
        metricsServerErrorCheck(exceptionMessage, errors);
      }
    }

    long lastEventTimestamp = getLastEventTimestamp(clusterRecord.getAccountId(), clusterRecord.getUuid());
    String clusterType = clusterRecord.getCluster().getClusterType();
    if (lastEventTimestamp != 0 && !hasRecentEvents(lastEventTimestamp, clusterType)) {
      errors.add(NO_RECENT_EVENTS_PUBLISHED);
    }
    return errors;
  }

  private void metricsServerErrorCheck(final String exceptionMessage, final List<CEError> errors) {
    CEError ceError = null;

    // on first level check by code=[4xx], then in inner block check for specific message related to resource
    if (exceptionMessage.startsWith("code=[401]")) {
      ceError = K8S_PERMISSIONS_MISSING;

      if (exceptionMessage.contains("\\\"message\\\":\\\"Unauthorized\\\"")) {
        ceError = K8S_PERMISSIONS_MISSING;
      }
    } else if (exceptionMessage.startsWith("code=[403]")) {
      // generaly the 403 is due to 'nodes is forbidden: User \\\"system:anonymous\\\" cannot list resource
      // \\\"nodes\\\" in API group \\\"\\\" at the cluster scope\"'
      ceError = K8S_PERMISSIONS_MISSING;

      if (exceptionMessage.contains("persistentvolumeclaims")) {
        ceError = PVC_PERMISSION_ERROR;
      } else if (exceptionMessage.contains("nodes is forbidden")
          || exceptionMessage.contains("nodes.metrics.k8s.io is forbidden")) {
        ceError = NODES_IS_FORBIDDEN;
      }
    } else if (exceptionMessage.startsWith("code=[404]")) {
      ceError = METRICS_SERVER_NOT_FOUND;
    }

    if (ceError != null) {
      errors.add(ceError);
    }
  }

  private List<String> getMessages(ClusterRecord clusterRecord, List<CEError> errors) {
    Preconditions.checkNotNull(clusterRecord.getCluster());

    String clusterName = clusterRecord.getCluster().getClusterName();
    List<String> messages = new ArrayList<>();
    long lastEventTimestamp = getLastEventTimestamp(clusterRecord.getAccountId(), clusterRecord.getUuid());
    for (CEError error : errors) {
      switch (error) {
        case PERPETUAL_TASK_NOT_ASSIGNED:
          messages.add(PERPETUAL_TASK_NOT_ASSIGNED.getMessage());
          break;
        case DELEGATE_NOT_AVAILABLE:
          String delegateName = getDelegateName(clusterRecord.getCluster().getCloudProviderId());
          if (delegateName == null) {
            messages.add(String.format(DELEGATE_NOT_AVAILABLE.getMessage(), ""));
          } else {
            messages.add(String.format(DELEGATE_NOT_AVAILABLE.getMessage(), "\"" + delegateName + "\""));
          }
          break;
        case NO_ELIGIBLE_DELEGATE:
          messages.add(format(NO_ELIGIBLE_DELEGATE.getMessage(), clusterName));
          break;
        case NO_RECENT_EVENTS_PUBLISHED:
          messages.add(String.format(NO_RECENT_EVENTS_PUBLISHED.getMessage(), clusterName, TIMESTAMP_FORMAT_SPECIFIER));
          break;
        case METRICS_SERVER_NOT_FOUND:
          messages.add(String.format(METRICS_SERVER_NOT_FOUND.getMessage(), clusterName));
          break;
        case AWS_ECS_CLUSTER_NOT_FOUND:
          messages.add(String.format(AWS_ECS_CLUSTER_NOT_FOUND.getMessage(), clusterName));
          break;
        case K8S_PERMISSIONS_MISSING:
          messages.add(K8S_PERMISSIONS_MISSING.getMessage());
          break;
        case PERPETUAL_TASK_CREATION_FAILURE:
          log.warn("The cluster id={} encounters the error {}.", clusterRecord.getUuid(),
              PERPETUAL_TASK_CREATION_FAILURE.getMessage());
          break;
        default:
          messages.add(error.getMessage());
          log.warn("The cluster id={} encounters an unexpected error {}.", clusterRecord.getUuid(), error.getMessage());
          break;
      }
    }

    if (isEmpty(messages)) {
      if (lastEventTimestamp <= 0) {
        messages.add("No events received. It typically takes 3 to 5 minutes to start receiving events.");
      } else {
        messages.add(String.format(LAST_EVENT_TIMESTAMP_MESSAGE, TIMESTAMP_FORMAT_SPECIFIER));
      }
    }
    return messages;
  }

  private String getDelegateName(String cloudProviderId) {
    String delegateName = null;
    SettingValue cloudProvider = settingsService.get(cloudProviderId).getValue();
    if (cloudProvider instanceof KubernetesClusterConfig) {
      KubernetesClusterConfig k8sCloudProvider = (KubernetesClusterConfig) cloudProvider;
      delegateName = !EmptyPredicate.isEmpty(k8sCloudProvider.getDelegateSelectors())
          ? k8sCloudProvider.getDelegateSelectors().iterator().next()
          : null;
    } else if (cloudProvider instanceof AwsConfig) {
      AwsConfig awsCloudProvider = (AwsConfig) cloudProvider;
      delegateName = awsCloudProvider.getTag();
    }
    return delegateName;
  }

  private long getLastEventTimestamp(String accountId, String identifier) {
    LastReceivedPublishedMessage lastReceivedPublishedMessage =
        lastReceivedPublishedMessageDao.get(accountId, identifier);
    if (lastReceivedPublishedMessage == null) {
      return 0;
    }
    return lastReceivedPublishedMessage.getLastReceivedAt();
  }

  private boolean hasRecentEvents(long eventTimestamp, String clusterType) {
    if (clusterType.equals(DIRECT_KUBERNETES) || clusterType.equals(GCP_KUBERNETES)
        || clusterType.equals(AZURE_KUBERNETES)) {
      return (Instant.now().toEpochMilli() - eventTimestamp) < EVENT_TIMESTAMP_RECENCY_THRESHOLD_FOR_K8S;
    } else if (clusterType.equals(AWS_ECS)) {
      return (Instant.now().toEpochMilli() - eventTimestamp) < EVENT_TIMESTAMP_RECENCY_THRESHOLD_FOR_AWS;
    } else {
      return (Instant.now().toEpochMilli() - eventTimestamp) < EVENT_TIMESTAMP_RECENCY_THRESHOLD_DEFAULT;
    }
  }
}
