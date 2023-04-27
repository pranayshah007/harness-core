/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.writer;

import static io.harness.batch.processing.ccm.UtilizationInstanceType.K8S_NODE;
import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.batch.processing.billing.timeseries.data.K8sGranularUtilizationData;
import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
import io.harness.batch.processing.integration.EcsEventGenerator;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.entities.events.PublishedMessage;
import io.harness.event.payloads.AggregatedUsage;
import io.harness.event.payloads.NodeMetric;
import io.harness.rule.Owner;

import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NodeUtilizationMetricsWriterTest extends CategoryTest implements EcsEventGenerator {
  @InjectMocks private NodeUtilizationMetricsWriter nodeUtilizationMetricsWriter;
  @Mock private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;

  private final String ACCOUNT_ID = "ACCOUNT_ID_" + this.getClass().getSimpleName();
  private final String INSTANCEID = "INSTANCEID" + this.getClass().getSimpleName();
  private final String INSTANCETYPE = K8S_NODE;
  private final String CLUSTERID = "CLUSTERID" + this.getClass().getSimpleName();
  private final String SETTINGID = "SETTINGID" + this.getClass().getSimpleName();
  private final long START_TIME_STAMP = 1000000000L;
  private final long END_TIME_STAMP = 1200000000L;
  private final long WINDOW = 200000000L;
  private final long CPU = 2 * 1_000_000_000L;
  private final long MAX_CPU = 4 * 1_000_000_000L;
  private final long MEMORY = 1024 * (1 << 20);
  private final long MAX_MEMORY = 2 * MEMORY;

  @Captor private ArgumentCaptor<List<K8sGranularUtilizationData>> K8sGranularUtilizationDataArgumentCaptor;

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void shouldWriteNodeUtilizationMetrics() {
    PublishedMessage nodeUtilizationMetricsMessages = getNodeUtilizationMetricsMessages();
    nodeUtilizationMetricsWriter.write(Collections.singletonList(nodeUtilizationMetricsMessages));
    verify(k8sUtilizationGranularDataService).create(K8sGranularUtilizationDataArgumentCaptor.capture());
    K8sGranularUtilizationData k8sGranularUtilizationData = K8sGranularUtilizationDataArgumentCaptor.getValue().get(0);
    assertThat(k8sGranularUtilizationData.getCpu()).isEqualTo(2048);
    assertThat(k8sGranularUtilizationData.getMemory()).isEqualTo(1024);
    assertThat(k8sGranularUtilizationData.getMaxCpu()).isEqualTo(4096);
    assertThat(k8sGranularUtilizationData.getMaxMemory()).isEqualTo(2048);
    assertThat(k8sGranularUtilizationData.getStartTimestamp()).isEqualTo(START_TIME_STAMP * 1000);
    assertThat(k8sGranularUtilizationData.getEndTimestamp()).isEqualTo(END_TIME_STAMP * 1000);
    assertThat(k8sGranularUtilizationData.getInstanceId()).isEqualTo(INSTANCEID);
    assertThat(k8sGranularUtilizationData.getInstanceType()).isEqualTo(INSTANCETYPE);
    assertThat(k8sGranularUtilizationData.getClusterId()).isEqualTo(CLUSTERID);
    assertThat(k8sGranularUtilizationData.getSettingId()).isEqualTo(SETTINGID);
  }

  private PublishedMessage getNodeUtilizationMetricsMessages() {
    NodeMetric nodeMetric = NodeMetric.newBuilder()
                                .setName(INSTANCEID)
                                .setCloudProviderId(SETTINGID)
                                .setClusterId(CLUSTERID)
                                .setTimestamp(Timestamp.newBuilder().setSeconds(END_TIME_STAMP).build())
                                .setWindow(Duration.newBuilder().setSeconds(WINDOW).build())
                                .setAggregatedUsage(AggregatedUsage.newBuilder()
                                                        .setAvgCpuNano(CPU)
                                                        .setAvgMemoryByte(MEMORY)
                                                        .setMaxCpuNano(MAX_CPU)
                                                        .setMaxMemoryByte(MAX_MEMORY)
                                                        .build())
                                .build();

    return getPublishedMessage(ACCOUNT_ID, nodeMetric);
  }
}
