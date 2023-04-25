/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.health;

import static io.harness.ccm.health.CEConnectorHealthMessages.BILLING_DATA_PIPELINE_SUCCESS;
import static io.harness.ccm.health.CEConnectorHealthMessages.BILLING_PIPELINE_CREATION_SUCCESSFUL;
import static io.harness.ccm.health.CEConnectorHealthMessages.SETTING_ATTRIBUTE_CREATED;
import static io.harness.rule.OwnerRule.HANTANG;
import static io.harness.rule.OwnerRule.ROHIT;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.UTSAV;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.dao.BillingDataPipelineRecordDao;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.Cluster;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.cluster.entities.DirectKubernetesCluster;
import io.harness.ccm.commons.entities.batch.LastReceivedPublishedMessage;
import io.harness.ccm.commons.entities.billing.BillingDataPipelineRecord;
import io.harness.ccm.commons.entities.events.CeExceptionRecord;
import io.harness.ccm.config.CCMConfig;
import io.harness.ccm.config.CCMSettingService;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskUnassignedReason;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.time.FakeClock;

import software.wings.WingsBaseTest;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.beans.ce.CEAwsConfig;
import software.wings.service.intfc.SettingsService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CE)
public class HealthStatusServiceImplTest extends WingsBaseTest {
  private String accountId = "ACCOUNT_ID";
  private static final String masterUrl = "dummyMasterUrl";
  private static final String username = "dummyUsername";
  private static final String password = "dummyPassword";
  private CCMConfig ccmConfig = CCMConfig.builder().cloudCostEnabled(true).build();
  private String cloudProviderId = "CLOUD_PROVIDER_ID";
  private String awsConnectorId = "AWS_CONNECTOR_ID";
  private String clusterId = "CLUSTER_ID";
  private String datasetId = "DATA_SET_ID";
  private static final String status = "SUCCEEDED";
  private static final String s3SyncMessage = "Last Successful S3 Sync at";

  private SettingAttribute cloudProvider;
  private Cluster k8sCluster;
  private ClusterRecord clusterRecord;
  private String[] perpetualTaskIds = new String[] {"1", "2"};
  private String delegateId = "DELEGATE_ID";
  private PerpetualTaskRecord taskRecord;
  private SettingAttribute awsConnector;
  Instant currentInstant;

  FakeClock fakeClock = new FakeClock();

  @Mock SettingsService settingsService;
  @Mock CCMSettingService ccmSettingService;
  @Mock ClusterRecordService clusterRecordService;
  @Mock PerpetualTaskService perpetualTaskService;
  @Mock LastReceivedPublishedMessageDao lastReceivedPublishedMessageDao;
  @Mock BillingDataPipelineRecordDao billingDataPipelineRecordDao;
  @Mock CeExceptionRecordDao ceExceptionRecordDao;

  @InjectMocks HealthStatusServiceImpl healthStatusService;

  @Before
  public void setUp() throws Exception {
    KubernetesClusterConfig kubernetesClusterConfig = KubernetesClusterConfig.builder()
                                                          .masterUrl(masterUrl)
                                                          .username(username.toCharArray())
                                                          .password(password.toCharArray())
                                                          .accountId(accountId)
                                                          .ccmConfig(ccmConfig)
                                                          .build();
    String kubernetesClusterConfigName = "KubernetesCluster-" + System.currentTimeMillis();
    cloudProvider = aSettingAttribute()
                        .withCategory(SettingCategory.CLOUD_PROVIDER)
                        .withUuid(cloudProviderId)
                        .withAccountId(accountId)
                        .withName(kubernetesClusterConfigName)
                        .withValue(kubernetesClusterConfig)
                        .build();

    awsConnector = aSettingAttribute()
                       .withCategory(SettingCategory.CE_CONNECTOR)
                       .withUuid(awsConnectorId)
                       .withAccountId(accountId)
                       .withValue(CEAwsConfig.builder().build())
                       .build();

    k8sCluster = DirectKubernetesCluster.builder().cloudProviderId(cloudProviderId).build();
    clusterRecord = ClusterRecord.builder()
                        .accountId(accountId)
                        .uuid(clusterId)
                        .cluster(k8sCluster)
                        .perpetualTaskIds(perpetualTaskIds)
                        .build();

    taskRecord = buildPerpetualTaskRecord(delegateId, PerpetualTaskState.TASK_ASSIGNED, null);
    currentInstant = Instant.now();

    when(billingDataPipelineRecordDao.fetchBillingPipelineRecord(accountId, awsConnectorId))
        .thenReturn(BillingDataPipelineRecord.builder()
                        .dataSetId(datasetId)
                        .lastSuccessfulS3Sync(currentInstant.minus(1, ChronoUnit.DAYS))
                        .dataTransferJobStatus(status)
                        .awsFallbackTableScheduledQueryStatus(status)
                        .preAggregatedScheduledQueryStatus(status)
                        .build());

    when(settingsService.get(eq(cloudProviderId))).thenReturn(cloudProvider);
    when(ccmSettingService.isCloudCostEnabled(isA(SettingAttribute.class))).thenReturn(true);
    when(clusterRecordService.list(eq(accountId), eq(null), eq(cloudProviderId)))
        .thenReturn(Arrays.asList(clusterRecord));
    when(perpetualTaskService.getTaskRecord(anyString())).thenReturn(taskRecord);
    when(lastReceivedPublishedMessageDao.get(eq(accountId), eq(clusterId)))
        .thenReturn(
            LastReceivedPublishedMessage.builder().lastReceivedAt(Instant.now(fakeClock).toEpochMilli()).build());
  }

  private PerpetualTaskRecord buildPerpetualTaskRecord(
      String delegateId, PerpetualTaskState state, PerpetualTaskUnassignedReason reason) {
    return PerpetualTaskRecord.builder().delegateId(delegateId).state(state).unassignedReason(reason).build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForCEDisabledCloudProviders() {
    when(ccmSettingService.isCloudCostEnabled(isA(SettingAttribute.class))).thenReturn(false);
    assertThatThrownBy(() -> healthStatusService.getHealthStatus(cloudProviderId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldParseMetricsServerRelatedCEExceptionRecord() {
    final String nodesForbiddenMesage =
        "code=[403] {\"kind\":\"Status\",\"apiVersion\":\"v1\",\"metadata\":{},\"status\":\"Failure\",\"message\":\"nodes is forbidden: User \\\"system:serviceaccount:default:ce-readonly\\\" cannot list resource \\\"nodes\\\" in API group \\\"\\\" at the cluster scope\",\"reason\":\"Forbidden\",\"details\":{\"kind\":\"nodes\"},\"code\":403}\n";
    final io.harness.ccm.commons.entities.events.CeExceptionRecord ceExceptionRecord =
        CeExceptionRecord.builder()
            .accountId(accountId)
            .clusterId(clusterId)
            .createdAt(Instant.now().toEpochMilli())
            .message(nodesForbiddenMesage)
            .build();

    when(ceExceptionRecordDao.getRecentException(eq(accountId), eq(clusterRecord.getUuid()), anyLong()))
        .thenReturn(ceExceptionRecord);

    final CEHealthStatus ceHealthStatus = healthStatusService.getHealthStatus(cloudProviderId);

    assertThat(ceHealthStatus.isHealthy()).isFalse();
    assertThat(ceHealthStatus.getClusterHealthStatusList()).isNotNull().hasSize(1);
    assertThat(ceHealthStatus.getClusterHealthStatusList().get(0).getMessages()).isNotNull().hasSize(1);
    assertThat(ceHealthStatus.getClusterHealthStatusList().get(0).getMessages().get(0))
        .isEqualTo(CEError.NODES_IS_FORBIDDEN.getMessage());
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldReturnUnhealthyWhenDelegateDisconnected() {
    PerpetualTaskRecord taskRecord = buildPerpetualTaskRecord(
        null, PerpetualTaskState.TASK_UNASSIGNED, PerpetualTaskUnassignedReason.NO_DELEGATE_AVAILABLE);
    when(perpetualTaskService.getTaskRecord(anyString())).thenReturn(taskRecord);
    CEHealthStatus status = healthStatusService.getHealthStatus(cloudProviderId);
    assertThat(status.isHealthy()).isFalse();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldReturnUnhealthyWhenNoInstalledDelegate() {
    PerpetualTaskRecord taskRecord = buildPerpetualTaskRecord(
        null, PerpetualTaskState.TASK_UNASSIGNED, PerpetualTaskUnassignedReason.NO_DELEGATE_INSTALLED);
    when(perpetualTaskService.getTaskRecord(anyString())).thenReturn(taskRecord);
    CEHealthStatus status = healthStatusService.getHealthStatus(cloudProviderId);
    assertThat(status.isHealthy()).isFalse();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldReturnHealthyForCloudProviders() {
    when(ccmSettingService.isCloudCostEnabled(isA(SettingAttribute.class))).thenReturn(true);
    CEHealthStatus status = healthStatusService.getHealthStatus(cloudProviderId);
    assertThat(status.isHealthy()).isTrue();
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void healthStatusForAConnectorJustCreated() {
    when(billingDataPipelineRecordDao.fetchBillingPipelineRecord(accountId, awsConnectorId)).thenReturn(null);
    when(settingsService.get(awsConnectorId)).thenReturn(awsConnector);
    awsConnector.setCreatedAt(currentInstant.minus(0, ChronoUnit.DAYS).toEpochMilli());
    CEHealthStatus healthStatus = healthStatusService.getHealthStatus(awsConnectorId);
    assertThat(healthStatus.getClusterHealthStatusList().get(0).getMessages().get(0))
        .isEqualTo(SETTING_ATTRIBUTE_CREATED.getMessage());
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void healthStatusForCaseOfSuccessfulPipelineCreation() {
    when(settingsService.get(awsConnectorId)).thenReturn(awsConnector);
    awsConnector.setCreatedAt(currentInstant.minus(1, ChronoUnit.DAYS).toEpochMilli());
    CEHealthStatus healthStatus = healthStatusService.getHealthStatus(awsConnectorId);
    assertThat(healthStatus.getClusterHealthStatusList().get(0).getMessages().get(0))
        .isEqualTo(BILLING_PIPELINE_CREATION_SUCCESSFUL.getMessage());
    assertThat(healthStatus.getClusterHealthStatusList().get(0).getMessages().get(1)).contains(s3SyncMessage);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void healthStatusForAConnectorRunningSuccessfullyForFiveDays() {
    when(settingsService.get(awsConnectorId)).thenReturn(awsConnector);
    awsConnector.setCreatedAt(currentInstant.minus(5, ChronoUnit.DAYS).toEpochMilli());
    CEHealthStatus healthStatus = healthStatusService.getHealthStatus(awsConnectorId);
    assertThat(healthStatus.getClusterHealthStatusList().get(0).getMessages().get(0))
        .isEqualTo(BILLING_DATA_PIPELINE_SUCCESS.getMessage());
    assertThat(healthStatus.getClusterHealthStatusList().get(0).getMessages().get(1)).contains(s3SyncMessage);
  }
}
