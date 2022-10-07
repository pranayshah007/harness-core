/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.rule.OwnerRule.BUHA;

import static software.wings.api.ServiceInstanceIdsParam.SERVICE_INSTANCE_IDS_PARAMS;
import static software.wings.api.ServiceInstanceIdsParam.ServiceInstanceIdsParamBuilder.aServiceInstanceIdsParam;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.ServiceInstance.Builder.aServiceInstance;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.UUID;

import static com.mongodb.internal.connection.tlschannel.util.Util.assertTrue;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceInstanceIdsParam;
import software.wings.beans.ServiceInstance;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
public class CollectRemainingInstancesStateTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SweepingOutputService sweepingOutputService;
  @InjectMocks private CollectRemainingInstancesState collectRemainingInstancesState;

  @Mock private ExecutionContextImpl context;

  @Before
  public void setUp() {
    when(context.getApp()).thenReturn(anApplication().uuid(APP_ID).build());
    when(context.fetchInfraMappingId()).thenReturn(INFRA_MAPPING_ID);
    when(sweepingOutputService.find(any()))
        .thenReturn(SweepingOutputInstance.builder().uuid(UUID).appId(APP_ID).build());

    when(infrastructureMappingService.listHostDisplayNames(any(), any(), any())).thenReturn(Collections.emptyList());

    when(context.prepareSweepingOutputBuilder(any())).thenReturn(SweepingOutputInstance.builder());
    when(context.prepareSweepingOutputInquiryBuilder()).thenReturn(SweepingOutputInquiry.builder());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSkipCollectingRemainingInstancesWhenItsNotASG() {
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .phaseNameForRollback("Canary")
                        .build());
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(false).build());

    ExecutionResponse executionResponse = collectRemainingInstancesState.execute(context);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testWhenThereIsOneAdditionalServicesInASG() {
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .phaseNameForRollback("Canary0")
                        .build());
    when(context.isLastPhase(true)).thenReturn(true);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(true).build());
    when(sweepingOutputService.findManyWithNamePrefix(any(), any()))
        .thenReturn(sweepingOutputInstanceList(new int[][] {{1, 2}}));
    when(infrastructureMappingService.selectServiceInstances(any(), any(), any(), any()))
        .thenReturn(getServiceInstancesFromASGWithServiceRunning(new int[] {1, 2, 3}));

    ExecutionResponse executionResponse = collectRemainingInstancesState.execute(context);

    verify(sweepingOutputService).deleteById(APP_ID, UUID);

    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService).save(captor.capture());

    ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) captor.getValue().getValue();
    assertThat(serviceInstanceIdsParam.getInstanceIds().size()).isEqualTo(3);
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance1"));
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance2"));
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance3"));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRolledBackPhase3() {
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .phaseNameForRollback("Canary3")
                        .build());
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(true).build());
    when(sweepingOutputService.findManyWithNamePrefix(any(), any()))
        .thenReturn(sweepingOutputInstanceList(new int[][] {{1}, {2, 3}, {4, 5}, {6, 7, 8}}));
    when(infrastructureMappingService.selectServiceInstances(any(), any(), any(), any()))
        .thenReturn(getServiceInstancesFromASGWithServiceRunning(
            new int[] {1, 2, 3, 4, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}));

    ExecutionResponse executionResponse = collectRemainingInstancesState.execute(context);

    verify(sweepingOutputService).deleteById(APP_ID, UUID);

    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService).save(captor.capture());

    ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) captor.getValue().getValue();
    assertThat(serviceInstanceIdsParam.getInstanceIds().size()).isEqualTo(6);
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance7"));
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance8"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance1"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance2"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance3"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance4"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance5"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance6"));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRolledBackPhase2() {
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .phaseNameForRollback("Canary2")
                        .build());
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(true).build());
    when(sweepingOutputService.findManyWithNamePrefix(any(), any()))
        .thenReturn(sweepingOutputInstanceList(new int[][] {{1}, {2, 3}, {4, 5}, {6, 7, 8}}));
    when(infrastructureMappingService.selectServiceInstances(any(), any(), any(), any()))
        .thenReturn(getServiceInstancesFromASGWithServiceRunning(
            new int[] {1, 2, 3, 4, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}));

    ExecutionResponse executionResponse = collectRemainingInstancesState.execute(context);

    verify(sweepingOutputService).deleteById(APP_ID, UUID);

    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService).save(captor.capture());

    ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) captor.getValue().getValue();
    assertThat(serviceInstanceIdsParam.getInstanceIds().size()).isEqualTo(4);
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance4"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance1"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance2"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance3"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance5"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance6"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance7"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance8"));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRolledBackPhase1() {
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .phaseNameForRollback("Canary1")
                        .build());
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(true).build());
    when(sweepingOutputService.findManyWithNamePrefix(any(), any()))
        .thenReturn(sweepingOutputInstanceList(new int[][] {{1}, {2, 3}, {4, 5}, {6, 7, 8}}));
    when(infrastructureMappingService.selectServiceInstances(any(), any(), any(), any()))
        .thenReturn(getServiceInstancesFromASGWithServiceRunning(
            new int[] {1, 2, 3, 4, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}));

    ExecutionResponse executionResponse = collectRemainingInstancesState.execute(context);

    verify(sweepingOutputService).deleteById(APP_ID, UUID);

    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService).save(captor.capture());

    ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) captor.getValue().getValue();
    assertThat(serviceInstanceIdsParam.getInstanceIds().size()).isEqualTo(4);
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance2"));
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance3"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance1"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance4"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance5"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance6"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance7"));
    assertTrue(!serviceInstanceIdsParam.getInstanceIds().contains("instance8"));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRolledBackPhase0() {
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .phaseNameForRollback("Canary0")
                        .build());
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(true).build());
    when(sweepingOutputService.findManyWithNamePrefix(any(), any())).thenReturn(getSweepingOutputForLastPhase());
    when(infrastructureMappingService.selectServiceInstances(any(), any(), any(), any()))
        .thenReturn(getServiceInstancesFromASGWithServiceRunning(
            new int[] {1, 2, 3, 4, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}));

    ExecutionResponse executionResponse = collectRemainingInstancesState.execute(context);

    verify(sweepingOutputService).deleteById(APP_ID, UUID);

    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService).save(captor.capture());

    ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) captor.getValue().getValue();
    assertThat(serviceInstanceIdsParam.getInstanceIds().size()).isEqualTo(1);
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance1"));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testRolledBackWhenItIsScaleUp2() {
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM))
        .thenReturn(PhaseElement.builder()
                        .serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build())
                        .phaseNameForRollback("Canary0")
                        .build());
    when(context.isLastPhase(true)).thenReturn(true);
    when(infrastructureMappingService.get(APP_ID, INFRA_MAPPING_ID))
        .thenReturn(anAwsInfrastructureMapping().withProvisionInstances(true).build());
    when(sweepingOutputService.findManyWithNamePrefix(any(), any()))
        .thenReturn(sweepingOutputInstanceList(new int[][] {{1}, {2, 3}, {4, 5}, {6, 7, 8}}));
    when(infrastructureMappingService.selectServiceInstances(any(), any(), any(), any()))
        .thenReturn(getServiceInstancesFromASGWithServiceRunning(new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9}));

    ExecutionResponse executionResponse = collectRemainingInstancesState.execute(context);

    verify(sweepingOutputService).deleteById(APP_ID, UUID);

    ArgumentCaptor<SweepingOutputInstance> captor = ArgumentCaptor.forClass(SweepingOutputInstance.class);
    verify(sweepingOutputService).save(captor.capture());

    ServiceInstanceIdsParam serviceInstanceIdsParam = (ServiceInstanceIdsParam) captor.getValue().getValue();
    assertThat(serviceInstanceIdsParam.getInstanceIds().size()).isEqualTo(2);
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance1"));
    assertTrue(serviceInstanceIdsParam.getInstanceIds().contains("instance9"));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  private List<ServiceInstance> getServiceInstancesFromASGWithServiceRunning(int[] instances) {
    List<ServiceInstance> serviceInstances = new ArrayList<>();
    for (int instance : instances) {
      serviceInstances.add(aServiceInstance()
                               .withServiceId(SERVICE_ID)
                               .withUuid("instance" + instance)
                               .withHostId("hostId" + instance)
                               .withHostName("host" + instance)
                               .withPublicDns("publicDNS" + instance)
                               .build());
    }
    return serviceInstances;
  }

  private List<SweepingOutputInstance> sweepingOutputInstanceList(int[][] instancesThroughPhases) {
    List<SweepingOutputInstance> sweepingOutputInstanceList = new ArrayList<>();

    for (int i = 0; i < instancesThroughPhases.length; i++) {
      List<String> instanceList = new ArrayList<>();
      for (int j = 0; j < instancesThroughPhases[i].length; j++) {
        instanceList.add("instance" + instancesThroughPhases[i][j]);
      }
      sweepingOutputInstanceList.add(SweepingOutputInstance.builder()
                                         .name(SERVICE_INSTANCE_IDS_PARAMS + "Canary" + i)
                                         .value(aServiceInstanceIdsParam().withInstanceIds(instanceList).build())
                                         .build());
    }
    return sweepingOutputInstanceList;
  }

  private List<SweepingOutputInstance> getSweepingOutputForLastPhase() {
    return new ArrayList<>(
        asList(SweepingOutputInstance.builder()
                   .name(SERVICE_INSTANCE_IDS_PARAMS + "Canary0")
                   .value(aServiceInstanceIdsParam().withInstanceIds(Collections.singletonList("instance1")).build())
                   .build(),
            SweepingOutputInstance.builder()
                .name(SERVICE_INSTANCE_IDS_PARAMS + "Canary1")
                .value(aServiceInstanceIdsParam()
                           .withInstanceIds(asList("instance2", "instance3", "instance16", "instance17"))
                           .withInitiallyDeployedInstancesCount(2)
                           .build())
                .build(),
            SweepingOutputInstance.builder()
                .name(SERVICE_INSTANCE_IDS_PARAMS + "Canary2")
                .value(aServiceInstanceIdsParam()
                           .withInstanceIds(asList("instance4", "instance13", "instance14", "instance15"))
                           .withInitiallyDeployedInstancesCount(2)
                           .build())
                .build(),
            SweepingOutputInstance.builder()
                .name(SERVICE_INSTANCE_IDS_PARAMS + "Canary3")
                .value(aServiceInstanceIdsParam()
                           .withInstanceIds(
                               asList("instance7", "instance8", "instance9", "instance10", "instance11", "instance12"))
                           .withInitiallyDeployedInstancesCount(3)
                           .build())
                .build()));
  }
}
