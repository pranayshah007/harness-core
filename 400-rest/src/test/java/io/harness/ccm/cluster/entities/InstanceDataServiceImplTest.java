/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.cluster.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.Resource;
import io.harness.ccm.commons.dao.InstanceDataDao;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.impl.InstanceDataServiceImpl;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CE)
public class InstanceDataServiceImplTest extends WingsBaseTest {
  @Mock private InstanceDataDao instanceDataDao;
  @Inject @InjectMocks InstanceDataServiceImpl instanceDataService;
  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String UUID = "UUID";
  private static final String INSTANCE_ID = "instanceId";
  private static final String INSTANCE_NAME = "instanceName";
  private static final Instant USAGE_START_TIME = Instant.now();
  private static final Instant USAGE_STOP_TIME = Instant.now();
  private static final double CPU_UNITS = 4014;
  private static final double MEMORY_MB = 12401;
  private static final String INSTANCE_CATEGORY = "instance_category";
  private static final String OPERATING_SYSTEM = "operating_system";

  @Before
  public void setUp() {
    when(instanceDataDao.fetchInstanceDataForGivenInstances(ACCOUNT_ID, Collections.singletonList(INSTANCE_ID)))
        .thenReturn(Collections.singletonList(getTestInstanceData()));
    when(instanceDataDao.fetchInstanceDataForGivenInstances(
             ACCOUNT_ID, CLUSTER_ID, Collections.singletonList(INSTANCE_ID)))
        .thenReturn(Collections.singletonList(getTestInstanceData()));
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldFetchInstanceDataForGivenInstances() {
    List<InstanceData> instanceData =
        instanceDataService.fetchInstanceDataForGivenInstances(ACCOUNT_ID, Collections.singletonList(INSTANCE_ID));
    assertThat(instanceData.get(0).getUuid()).isEqualTo(UUID);
    assertThat(instanceData.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceData.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceData.get(0).getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(instanceData.get(0).getInstanceName()).isEqualTo(INSTANCE_NAME);
    assertThat(instanceData.get(0).getTotalResource().getCpuUnits()).isEqualTo(CPU_UNITS);
    assertThat(instanceData.get(0).getTotalResource().getMemoryMb()).isEqualTo(MEMORY_MB);
    assertThat(instanceData.get(0).getUsageStartTime()).isEqualTo(USAGE_START_TIME);
    assertThat(instanceData.get(0).getUsageStopTime()).isEqualTo(USAGE_STOP_TIME);
    assertThat(instanceData.get(0).getMetaData().get(INSTANCE_CATEGORY)).isEqualTo("SPOT");
    assertThat(instanceData.get(0).getMetaData().get(OPERATING_SYSTEM)).isEqualTo("linux");
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void shouldFetchInstanceDataForGivenInstancesInCluster() {
    List<InstanceData> instanceData = instanceDataService.fetchInstanceDataForGivenInstances(
        ACCOUNT_ID, CLUSTER_ID, Collections.singletonList(INSTANCE_ID));
    assertThat(instanceData.get(0).getUuid()).isEqualTo(UUID);
    assertThat(instanceData.get(0).getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(instanceData.get(0).getClusterId()).isEqualTo(CLUSTER_ID);
    assertThat(instanceData.get(0).getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(instanceData.get(0).getInstanceName()).isEqualTo(INSTANCE_NAME);
    assertThat(instanceData.get(0).getTotalResource().getCpuUnits()).isEqualTo(CPU_UNITS);
    assertThat(instanceData.get(0).getTotalResource().getMemoryMb()).isEqualTo(MEMORY_MB);
    assertThat(instanceData.get(0).getUsageStartTime()).isEqualTo(USAGE_START_TIME);
    assertThat(instanceData.get(0).getUsageStopTime()).isEqualTo(USAGE_STOP_TIME);
    assertThat(instanceData.get(0).getMetaData().get(INSTANCE_CATEGORY)).isEqualTo("SPOT");
    assertThat(instanceData.get(0).getMetaData().get(OPERATING_SYSTEM)).isEqualTo("linux");
  }

  private InstanceData getTestInstanceData() {
    Map<String, String> metaData = new HashMap<>();
    metaData.put(INSTANCE_CATEGORY, "SPOT");
    metaData.put(OPERATING_SYSTEM, "linux");
    return InstanceData.builder()
        .uuid(UUID)
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .instanceId(INSTANCE_ID)
        .instanceName(INSTANCE_NAME)
        .totalResource(Resource.builder().cpuUnits(CPU_UNITS).memoryMb(MEMORY_MB).build())
        .metaData(metaData)
        .usageStartTime(USAGE_START_TIME)
        .usageStopTime(USAGE_STOP_TIME)
        .build();
  }
}
