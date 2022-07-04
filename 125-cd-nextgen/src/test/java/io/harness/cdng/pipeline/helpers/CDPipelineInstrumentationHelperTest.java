/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.ACTIVE_SERVICES_COUNT;
import static io.harness.cdng.instrumentation.ServiceInstrumentationConstants.SERVICE_INSTANCES_COUNT;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo;
import io.harness.dtos.InstanceDTO;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.telemetry.TelemetryReporter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.jooq.Row3;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class CDPipelineInstrumentationHelperTest extends CategoryTest {
  @Mock private InstanceService instanceService;
  @InjectMocks private CDPipelineInstrumentationHelper cdPipelineInstrumentationHelper;
  @Mock private TelemetryReporter telemetryReporter;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetServiceInstancesInIntervalException() {
    String accountId = "TestAccountId";
    String orgId = "OrgId";
    String projectId = "ProjectId";
    long startInterval = -10;
    long endInterval = 0;

    doThrow(new RuntimeException("Some runtime exception problem"))
        .when(instanceService)
        .getInstancesDeployedInInterval(any(), anyLong(), anyLong());

    List<InstanceDTO> serviceInstances =
        cdPipelineInstrumentationHelper.getServiceInstancesInInterval(accountId, startInterval, endInterval);

    assertThat(serviceInstances).isNotNull();
    assertThat(serviceInstances.size()).isEqualTo(0);

    doThrow(new RuntimeException("Some runtime exception problem again"))
        .when(instanceService)
        .getInstancesDeployedInInterval(any(), any(), any(), anyLong(), anyLong());

    List<InstanceDTO> serviceInstances2 = cdPipelineInstrumentationHelper.getServiceInstancesInInterval(
        accountId, orgId, projectId, startInterval, endInterval);

    assertThat(serviceInstances).isNotNull();
    assertThat(serviceInstances.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGatheringServiceInstancesAndActiveServices() {
    String accountId = "TestAccountId";
    long startInterval = System.currentTimeMillis() - 24 * 60 * 60 * 1000;
    long endInterval = System.currentTimeMillis();

    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj1")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId1")
                                   .build();

    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj2")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId2")
                                   .build();

    InstanceDTO instanceDTO3 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj2")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId3")
                                   .build();

    InstanceDTO instanceDTO4 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj1")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId1")
                                   .build();

    List<InstanceDTO> responseList = new ArrayList<>();
    responseList.add(instanceDTO1);
    responseList.add(instanceDTO2);
    responseList.add(instanceDTO3);
    responseList.add(instanceDTO4);

    doReturn(responseList).when(instanceService).getInstancesDeployedInInterval(any(), anyLong(), anyLong());

    List<InstanceDTO> serviceInstances =
        cdPipelineInstrumentationHelper.getServiceInstancesInInterval(accountId, startInterval, endInterval);
    long activeServicesCount = cdPipelineInstrumentationHelper.getTotalNumberOfActiveServices(serviceInstances);

    assertThat(serviceInstances).isNotNull();
    assertThat(serviceInstances.size()).isEqualTo(4);
    assertThat(activeServicesCount).isEqualTo(3);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetOrgProjectServiceRowsException() {
    Row3<String, String, String>[] rows = cdPipelineInstrumentationHelper.getOrgProjectServiceRows(null);

    assertThat(rows.length).isEqualTo(0);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendCountOfDistinctActiveServicesEvent() {
    String pipelineId = "TestPipelineId";
    String identity = "test@email.com";
    String accountId = "TestAccountId";
    String accountName = "Test Account Name";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";

    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj1")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId1")
                                   .build();

    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj2")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId2")
                                   .build();

    InstanceDTO instanceDTO3 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj2")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId3")
                                   .build();

    InstanceDTO instanceDTO4 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj1")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId1")
                                   .build();

    List<InstanceDTO> responseList = new ArrayList<>();
    responseList.add(instanceDTO1);
    responseList.add(instanceDTO2);
    responseList.add(instanceDTO3);
    responseList.add(instanceDTO4);

    doReturn(responseList)
        .when(instanceService)
        .getInstancesDeployedInInterval(any(), any(), any(), anyLong(), anyLong());

    cdPipelineInstrumentationHelper.sendCountOfDistinctActiveServicesEvent(
        pipelineId, identity, accountId, accountName, orgId, projectId);

    ArgumentCaptor<HashMap> captor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), captor.capture(), any(), any(), any());
    HashMap prop = (HashMap) captor.getValue();

    assertThat(prop.size()).isEqualTo(6);
    assertThat(prop.get(ACTIVE_SERVICES_COUNT)).isEqualTo(3L);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendCountOfServiceInstancesEvent() {
    String pipelineId = "TestPipelineId";
    String identity = "test@email.com";
    String accountId = "TestAccountId";
    String accountName = "Test Account Name";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";

    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj1")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId1")
                                   .build();

    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj2")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId2")
                                   .build();

    InstanceDTO instanceDTO3 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj2")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId3")
                                   .build();

    InstanceDTO instanceDTO4 = InstanceDTO.builder()
                                   .accountIdentifier(accountId)
                                   .projectIdentifier("proj1")
                                   .orgIdentifier("org")
                                   .serviceIdentifier("svcId1")
                                   .build();

    List<InstanceDTO> instancesList = new ArrayList<>();
    instancesList.add(instanceDTO1);
    instancesList.add(instanceDTO2);
    instancesList.add(instanceDTO3);
    instancesList.add(instanceDTO4);

    cdPipelineInstrumentationHelper.sendCountOfServiceInstancesEvent(
        pipelineId, identity, accountId, accountName, orgId, projectId, instancesList);

    ArgumentCaptor<HashMap> captor = ArgumentCaptor.forClass(HashMap.class);
    verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), captor.capture(), any(), any(), any());
    HashMap prop = (HashMap) captor.getValue();

    assertThat(prop.size()).isEqualTo(6);
    assertThat(prop.get(SERVICE_INSTANCES_COUNT)).isEqualTo(4);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendServiceUsedEventsForPipelineExecution() {
    String pipelineId = "TestPipelineId";
    String identity = "test@email.com";
    String accountId = "TestAccountId";
    String accountName = "Test Account Name";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";
    String planExecutionId = "123456789098765432";

    List<String> serviceIds = new ArrayList<>();
    serviceIds.add("Service1");
    serviceIds.add("Service1");
    serviceIds.add("Service3");
    serviceIds.add("Service3");
    serviceIds.add("Service5");
    CDPipelineModuleInfo cdPipelineModuleInfo = CDPipelineModuleInfo.builder().serviceIdentifiers(serviceIds).build();
    OrchestrationEvent event = OrchestrationEvent.builder().moduleInfo(cdPipelineModuleInfo).build();

    cdPipelineInstrumentationHelper.sendServiceUsedEventsForPipelineExecution(
        pipelineId, identity, accountId, accountName, orgId, projectId, planExecutionId, event);

    verify(telemetryReporter, times(3)).sendTrackEvent(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSendServiceUsedEventsForPipelineExecutionWithoutModuleInfo() {
    String pipelineId = "TestPipelineId";
    String identity = "test@email.com";
    String accountId = "TestAccountId";
    String accountName = "Test Account Name";
    String orgId = "TestOrgId";
    String projectId = "TestProjectId";
    String planExecutionId = "123456789098765432";

    OrchestrationEvent event = OrchestrationEvent.builder().moduleInfo(null).build();

    cdPipelineInstrumentationHelper.sendServiceUsedEventsForPipelineExecution(
        pipelineId, identity, accountId, accountName, orgId, projectId, planExecutionId, event);

    verify(telemetryReporter, times(0)).sendTrackEvent(any(), any(), any(), any(), any(), any(), any());
  }
}
