/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service.impl;

import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.batch.processing.ccm.InstanceEvent;
import io.harness.batch.processing.ccm.InstanceInfo;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.event.payloads.Lifecycle;
import io.harness.rule.Owner;

import software.wings.dl.WingsPersistence;
import software.wings.security.authentication.BatchQueryConfig;
import software.wings.security.authentication.BulkOperationBatchQueryConfig;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InstanceDataBulkWriteServiceImplTest extends BatchProcessingTestBase {
  private static final String ACCOUNT_ID = "account_id";
  private static final String INSTANCE_ID = "instance_id";
  private static final String INSTANCE_NAME = "instance_name";
  private static final String CLUSTER_NAME = "cluster_name";
  private static final String CLUSTER_ID = "cluster_id";

  private static final Timestamp TIMESTAMP = Timestamp.getDefaultInstance();
  private static final Instant INSTANT = Instant.now();

  private final WingsPersistence wingsPersistence = mock(WingsPersistence.class, RETURNS_DEEP_STUBS);
  @Mock private BatchMainConfig config;
  @InjectMocks private InstanceDataBulkWriteServiceImpl instanceDataBulkWriteService;

  @Before
  public void setUp() throws Exception {
    when(config.getBatchQueryConfig()).thenReturn(BatchQueryConfig.builder().queryBatchSize(10).build());
    when(config.getBulkOperationBatchQueryConfig())
        .thenReturn(BulkOperationBatchQueryConfig.builder().queryBatchSize(10).build());
    when(wingsPersistence.getCollection(any()).initializeUnorderedBulkOperation().execute().isAcknowledged())
        .thenReturn(true);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldUpdateLifecycle() {
    boolean result = instanceDataBulkWriteService.updateLifecycle(ImmutableList.of(getLifecycle()));
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldUpdateInstanceInfo() {
    boolean result = instanceDataBulkWriteService.upsertInstanceInfo(ImmutableList.of(getInstanceInfo()));
    assertThat(result).isTrue();
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void shouldUpdateInstanceEvent() {
    boolean result = instanceDataBulkWriteService.updateInstanceEvent(ImmutableList.of(getInstanceEvent()));
    assertThat(result).isTrue();
  }

  private static Lifecycle getLifecycle() {
    return Lifecycle.newBuilder()
        .setType(Lifecycle.EventType.EVENT_TYPE_STOP)
        .setTimestamp(TIMESTAMP)
        .setInstanceId(INSTANCE_ID)
        .build();
  }

  private static InstanceEvent getInstanceEvent() {
    return InstanceEvent.builder()
        .type(InstanceEvent.EventType.STOP)
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .instanceId(INSTANCE_ID)
        .timestamp(INSTANT)
        .build();
  }

  private static InstanceInfo getInstanceInfo() {
    return InstanceInfo.builder()
        .instanceId(INSTANCE_ID)
        .usageStartTime(INSTANT)
        .accountId(ACCOUNT_ID)
        .instanceName(INSTANCE_NAME)
        .instanceType(InstanceType.K8S_POD)
        .clusterId(CLUSTER_ID)
        .clusterName(CLUSTER_NAME)
        .instanceState(InstanceState.RUNNING)
        .build();
  }
}
