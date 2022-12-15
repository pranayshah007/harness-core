/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import static io.harness.ccm.commons.beans.InstanceType.K8S_NODE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD;
import static io.harness.ccm.commons.beans.InstanceType.K8S_POD_FARGATE;
import static io.harness.ccm.commons.beans.InstanceType.K8S_PV;
import static io.harness.rule.OwnerRule.HITESH;
import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.InstanceState;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class InstanceDataDaoImplTest extends BatchProcessingTestBase {
  @Inject @InjectMocks private InstanceDataDaoImpl instanceDataDao;

  private static final String RUNNING_INSTANCE_ID = "running_instance_id";
  private static final String INSTANCE_NAME = "instance_name";
  private static final String ACCOUNT_ID = "account_id";
  private static final String CLOUD_PROVIDER_ID = "cloud_provider_id";
  private static final String CLUSTER_NAME = "cluster_name";
  private static final String CLUSTER_ID = "cluster_id";
  private final Instant NOW = Instant.now();
  private final Instant START_INSTANT = NOW.truncatedTo(ChronoUnit.DAYS);
  private final Instant PREV_START_INSTANT = NOW.minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
  private final Instant END_INSTANT = NOW.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnInstanceDataWithName() {
    instanceDataDao.create(instanceData(RUNNING_INSTANCE_ID, InstanceState.RUNNING));
    InstanceData instanceData =
        instanceDataDao.fetchInstanceDataWithName(ACCOUNT_ID, CLUSTER_ID, INSTANCE_NAME, START_INSTANT.toEpochMilli());
    assertThat(instanceData).isNotNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnInstanceData() {
    instanceDataDao.create(instanceData(RUNNING_INSTANCE_ID, InstanceState.RUNNING));
    InstanceData instanceData = instanceDataDao.fetchInstanceData(ACCOUNT_ID, CLUSTER_ID, RUNNING_INSTANCE_ID);
    assertThat(instanceData).isNotNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnActiveInstance() {
    instanceDataDao.create(instanceData(RUNNING_INSTANCE_ID, InstanceState.RUNNING));
    InstanceData instanceData =
        instanceDataDao.fetchActiveInstanceData(ACCOUNT_ID, CLUSTER_ID, RUNNING_INSTANCE_ID, getActiveInstanceState());
    assertThat(instanceData).isNotNull();
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnEmptyClusterActiveInstance() {
    instanceDataDao.create(instanceData(RUNNING_INSTANCE_ID, InstanceState.RUNNING));
    List<InstanceData> instanceData = instanceDataDao.fetchClusterActiveInstanceData(
        ACCOUNT_ID, CLUSTER_ID, getActiveInstanceState(), PREV_START_INSTANT);
    assertThat(instanceData).hasSize(0);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnClusterActiveInstance() {
    instanceDataDao.create(instanceData(RUNNING_INSTANCE_ID, InstanceState.RUNNING));
    List<InstanceData> instanceData =
        instanceDataDao.fetchClusterActiveInstanceData(ACCOUNT_ID, CLUSTER_ID, getActiveInstanceState(), END_INSTANT);
    assertThat(instanceData).hasSize(1);
    assertThat(instanceData.get(0).getInstanceId()).isEqualTo(RUNNING_INSTANCE_ID);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldReturnActiveInstanceData() {
    instanceDataDao.create(instanceData(RUNNING_INSTANCE_ID, InstanceState.RUNNING));
    List<InstanceData> instanceDataLists =
        instanceDataDao.getInstanceDataListsOfTypes(ACCOUNT_ID, 1, START_INSTANT.plus(2, ChronoUnit.DAYS),
            START_INSTANT.plus(3, ChronoUnit.DAYS), ImmutableList.of(K8S_POD, K8S_POD_FARGATE, K8S_NODE));

    assertThat(instanceDataLists.size()).isEqualTo(1);
    assertThat(instanceDataLists.get(0).getInstanceId()).isEqualTo(RUNNING_INSTANCE_ID);
  }

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testGetInstanceDataListsByType() {
    Instant startTime = NOW;
    Instant endTime = NOW.plus(1, ChronoUnit.DAYS);

    instanceDataDao.create(getInstanceOfType(startTime, K8S_PV));
    instanceDataDao.create(getInstanceOfType(startTime, K8S_POD));
    instanceDataDao.create(getInstanceOfType(startTime, K8S_POD));
    instanceDataDao.create(getInstanceOfType(startTime, K8S_NODE));

    List<InstanceData> instanceDataList =
        instanceDataDao.getInstanceDataListsOfTypes(ACCOUNT_ID, 10, startTime, endTime, ImmutableList.of(K8S_PV));
    assertThat(instanceDataList).isNotEmpty().hasSize(1);
    assertThat(instanceDataList.get(0).getInstanceType()).isEqualTo(K8S_PV);

    List<InstanceData> instanceDataListsOtherThanPV = instanceDataDao.getInstanceDataListsOfTypes(
        ACCOUNT_ID, 10, startTime, endTime, ImmutableList.of(K8S_POD, K8S_NODE));
    assertThat(instanceDataListsOtherThanPV).isNotEmpty().hasSize(3);
    assertThat(instanceDataListsOtherThanPV.stream().map(InstanceData::getInstanceType).collect(Collectors.toList()))
        .doesNotContain(K8S_PV);
  }

  private static InstanceData getInstanceOfType(Instant startTime, InstanceType instanceType) {
    return InstanceData.builder()
        .usageStartTime(startTime)
        .accountId(ACCOUNT_ID)
        .instanceType(instanceType)
        .activeInstanceIterator(startTime.plus(365 * 50, ChronoUnit.DAYS))
        .build();
  }

  private List<InstanceState> getActiveInstanceState() {
    return new ArrayList<>(Arrays.asList(InstanceState.INITIALIZING, InstanceState.RUNNING));
  }

  private InstanceData instanceData(String instanceId, InstanceState instanceState) {
    return InstanceData.builder()
        .instanceId(instanceId)
        .instanceName(INSTANCE_NAME)
        .accountId(ACCOUNT_ID)
        .settingId(CLOUD_PROVIDER_ID)
        .instanceState(instanceState)
        .clusterName(CLUSTER_NAME)
        .clusterId(CLUSTER_ID)
        .instanceType(K8S_NODE)
        .instanceState(InstanceState.RUNNING)
        .usageStartTime(START_INSTANT)
        .activeInstanceIterator(START_INSTANT.plus(365 * 50, ChronoUnit.DAYS))
        .build();
  }
}
