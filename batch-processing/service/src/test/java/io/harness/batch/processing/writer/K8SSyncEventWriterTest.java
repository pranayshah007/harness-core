/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.dao.intfc.PublishedMessageDao;
import io.harness.batch.processing.service.intfc.InstanceDataBulkWriteService;
import io.harness.batch.processing.service.intfc.InstanceDataService;
import io.harness.batch.processing.tasklet.K8SSyncEventTasklet;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.payloads.Lifecycle;
import io.harness.ff.FeatureFlagService;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.k8s.watch.K8SClusterSyncEvent;
import io.harness.rule.Owner;

import software.wings.security.authentication.BatchQueryConfig;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

@RunWith(MockitoJUnitRunner.class)
public class K8SSyncEventWriterTest extends CategoryTest {
  private final String TEST_ACCOUNT_ID = "K8S_INSTANCE_INFO_ACCOUNT_ID_" + this.getClass().getSimpleName();
  private final String TEST_CLUSTER_ID = "K8S_TEST_CLUSTER_ID_" + this.getClass().getSimpleName();
  private final String CLOUD_PROVIDER_ID = "K8S_CLOUD_PROVIDER_ID_" + this.getClass().getSimpleName();
  private final String TEST_INSTANCE_ID_NODE_RUNNING =
      "K8S_INSTANCE_INFO_INSTANCE_ID_NODE_RUNNING_" + this.getClass().getSimpleName();
  private final String TEST_ACTIVE_INSTANCE_ID =
      "K8S_INSTANCE_INFO_ACTIVE_INSTANCE_ID_" + this.getClass().getSimpleName();
  private static final String ACCOUNT_ID = "account_id";

  @Mock private FeatureFlagService featureFlagService;
  @Mock private BatchMainConfig config;
  @Mock private PublishedMessageDao publishedMessageDao;
  @Mock private InstanceDataService instanceDataService;
  @Mock private InstanceDataBulkWriteService instanceDataBulkWriteService;

  @InjectMocks private K8SSyncEventTasklet k8SSyncEventTasklet;

  private final Instant NOW = Instant.now();
  private final Instant INSTANCE_STOP_TIMESTAMP = NOW.minus(1, ChronoUnit.DAYS);
  private final Instant INSTANCE_START_TIMESTAMP = NOW.minus(2, ChronoUnit.DAYS);
  private final long START_TIME_MILLIS = INSTANCE_START_TIMESTAMP.toEpochMilli();
  private final long END_TIME_MILLIS = INSTANCE_STOP_TIMESTAMP.toEpochMilli();

  @Before
  public void setUpData() {
    when(featureFlagService.isEnabled(eq(FeatureName.NODE_RECOMMENDATION_AGGREGATE), eq(ACCOUNT_ID))).thenReturn(false);
    when(config.getBatchQueryConfig())
        .thenReturn(BatchQueryConfig.builder().queryBatchSize(50).syncJobDisabled(false).build());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldStopK8SInstances() {
    ChunkContext chunkContext = mock(ChunkContext.class);
    StepContext stepContext = mock(StepContext.class);
    StepExecution stepExecution = mock(StepExecution.class);
    JobParameters parameters = mock(JobParameters.class);
    when(chunkContext.getStepContext()).thenReturn(stepContext);
    when(stepContext.getStepExecution()).thenReturn(stepExecution);
    when(stepExecution.getJobParameters()).thenReturn(parameters);

    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(START_TIME_MILLIS));
    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(END_TIME_MILLIS));
    when(publishedMessageDao.fetchPublishedMessage(any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(k8sSyncEvent()));
    when(instanceDataService.fetchClusterActiveInstanceIds(any(), any(), any()))
        .thenReturn(ImmutableSet.of(TEST_INSTANCE_ID_NODE_RUNNING));
    when(instanceDataService.fetchActiveInstanceData(any(), any(), any(), any()))
        .thenReturn(getInstanceData(TEST_INSTANCE_ID_NODE_RUNNING));

    k8SSyncEventTasklet.execute(null, chunkContext);

    ArgumentCaptor<ArrayList<Lifecycle>> lifecycleArgumentCaptor =
        ArgumentCaptor.forClass((Class<ArrayList<Lifecycle>>) (Class) ArrayList.class);
    verify(instanceDataBulkWriteService).updateLifecycle(lifecycleArgumentCaptor.capture());

    List<Lifecycle> lifecycleList = lifecycleArgumentCaptor.getValue();
    assertThat(lifecycleList).isNotEmpty();
    assertThat(lifecycleList.get(0)).isInstanceOf(Lifecycle.class);
    Lifecycle lifecycle = lifecycleList.get(0);
    assertThat(lifecycle.getInstanceId()).isEqualTo(TEST_INSTANCE_ID_NODE_RUNNING);
    assertThat(lifecycle.getType()).isEqualTo(Lifecycle.EventType.EVENT_TYPE_STOP);
  }

  private InstanceData getInstanceData(String instanceId) {
    return InstanceData.builder().instanceId(instanceId).build();
  }

  private PublishedMessage k8sSyncEvent() {
    K8SClusterSyncEvent k8SClusterSyncEvent = K8SClusterSyncEvent.newBuilder()
                                                  .setClusterId(TEST_CLUSTER_ID)
                                                  .setCloudProviderId(CLOUD_PROVIDER_ID)
                                                  .addAllActiveNodeUids(Arrays.asList(TEST_ACTIVE_INSTANCE_ID))
                                                  .setLastProcessedTimestamp(HTimestamps.fromInstant(NOW))
                                                  .build();
    Any payload = Any.pack(k8SClusterSyncEvent);
    return PublishedMessage.builder()
        .accountId(TEST_ACCOUNT_ID)
        .data(payload.toByteArray())
        .type(k8SClusterSyncEvent.getClass().getName())
        .build();
  }
}
