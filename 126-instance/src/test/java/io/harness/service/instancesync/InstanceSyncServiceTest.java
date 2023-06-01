/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.K8sInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.InstanceSyncPerpetualTaskMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.InstanceSyncPerpetualTaskMappingService;
import io.harness.entities.InstanceType;
import io.harness.helper.InstanceSyncHelper;
import io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringService;
import io.harness.k8s.model.K8sContainer;
import io.harness.lock.PersistentLocker;
import io.harness.models.DeploymentEvent;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDP)
public class InstanceSyncServiceTest extends InstancesTestBase {
  AutoCloseable openMocks;
  private static final String OLD_INFRA_MAPPING_ID = "de6a4a1a772862b9a965c202";
  private static final String TEST_INFRA_MAPPING_ID = "62b9a965c202de6a4a1a7728";
  private static final String TEST_ACCOUNT_ID = "TestAccountId";
  private static final String TEST_UUID = "TestUuid";
  private static final String TEST_RELEASE_NAME1 = "release-9a854787f0afbb105cf115d533f7a54624e1ba57";
  private static final String TEST_RELEASE_NAME2 = "release-115d533f7a54624e1ba579a854787f0afbb105cf";
  private static final String TEST_INSTANCESYNC_KEY1 =
      "K8sInstanceInfoDTO_rollingdeploy-deploy-64dfd95958-v4h6s_default_harness/todolist-sample:10";
  private static final String TEST_INSTANCESYNC_KEY2 =
      "K8sInstanceInfoDTO_rollingdeploy-deploy-64dfd95958-ag67f_default_harness/todolist-sample:10";
  private static final String TEST_ORG_ID = "TestOrgId";
  private static final String OLD_PROJECT_ID = "oldinstancesynctest";
  private static final String TEST_PROJECT_ID = "instancesynctest";
  private static final String TEST_INFRA_KIND = "KubernetesDirect";
  private static final String TEST_CONNECTOR_REF = "minikube";
  private static final String TEST_ENV_ID = "instancesyncenv";
  private static final String TEST_SERVICE_ID = "instancesyncservice";
  private static final String TEST_INFRA_KEY1 = "9a854787f0afbb105cf115d533f7a54624e1ba57";
  private static final String TEST_INFRA_KEY2 = "5d533f7a54624e1ba579a854787f0afbb105cf11";
  private static final String TEST_INFRA_ID = "instancesyncinfra";
  private static final String TEST_INFRA_NAME = "Kubernetes";
  private static final String PERPETUAL_TASK_ID = "2vj09pXNSBmsHeC1V4WtDw";
  private static final String TEST_POD_IP = "10.8.3.123";
  private static final String TEST_POD_NAME1 = "rollingdeploy-deploy-64dfd95958-v4h6s";
  private static final String TEST_POD_NAME2 = "rollingdeploy-deploy-64dfd95958-ag67f";
  private static final String TEST_NAMESPACE = "default";
  private static final String BLUE_GREEN_COLOR = "blueGreenColor";
  private static final String TEST_CONTAINER_ID =
      "docker://7c0cf1a985c857105f53ad382ce709dca25944044595840187bfa7a499307f28";
  private static final String TEST_CONTAINER_NAME = "nginx";
  private static final String TEST_CONTAINER_IMAGE = "harness/todolist-sample:10";
  private static final String TEST_SERVICE_NAME = "instancesyncservice";
  private static final String TEST_ENV_NAME = "instance-sync-env";
  private static final long ONE_WEEKS_IN_MILLIS = (long) 7 * 24 * 60 * 60 * 1000;
  @Captor ArgumentCaptor<Map<OperationsOnInstances, List<InstanceDTO>>> instancesToBeModifiedCaptor;

  private InstanceSyncService instanceSyncService;
  @Mock private PersistentLocker persistentLocker;
  @Mock private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  @Inject InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;
  @Mock private InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private InstanceService instanceService;
  @Mock private ServiceEntityService serviceEntityService;
  @Mock private EnvironmentService environmentService;
  @Mock private DeploymentSummaryService deploymentSummaryService;
  @Mock private InstanceSyncServiceUtils instanceSyncServiceUtils;
  @Mock private InstanceSyncMonitoringService instanceSyncMonitoringService;
  @Mock private InstanceSyncPerpetualTaskMappingService instanceSyncPerpetualTaskMappingService;
  @Mock private AccountClient accountClient;
  @Mock private ConnectorService connectorService;
  @Mock private KryoSerializer kryoSerializer;

  @Before
  public void setUp() throws Exception {
    openMocks = MockitoAnnotations.openMocks(this);

    InstanceSyncHelper instanceSyncHelper = new InstanceSyncHelper(instanceSyncPerpetualTaskInfoService,
        instanceSyncPerpetualTaskService, serviceEntityService, environmentService, accountClient);
    instanceSyncService = new InstanceSyncServiceImpl(persistentLocker, instanceSyncPerpetualTaskService,
        instanceSyncPerpetualTaskInfoService, instanceSyncPerpetualTaskMappingService,
        instanceSyncHandlerFactoryService, infrastructureMappingService, instanceService, deploymentSummaryService,
        instanceSyncHelper, connectorService, instanceSyncServiceUtils, instanceSyncMonitoringService, accountClient,
        kryoSerializer);

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .name(TEST_SERVICE_NAME)
                                      .identifier(TEST_SERVICE_ID)
                                      .accountId(TEST_ACCOUNT_ID)
                                      .orgIdentifier(TEST_ORG_ID)
                                      .projectIdentifier(TEST_PROJECT_ID)
                                      .build();
    when(serviceEntityService.get(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(serviceEntity));
    Environment environment = Environment.builder()
                                  .identifier(TEST_ENV_ID)
                                  .type(EnvironmentType.PreProduction)
                                  .name(TEST_ENV_NAME)
                                  .accountId(TEST_ACCOUNT_ID)
                                  .orgIdentifier(TEST_ORG_ID)
                                  .projectIdentifier(TEST_PROJECT_ID)
                                  .build();
    when(environmentService.get(anyString(), anyString(), anyString(), anyString(), anyBoolean()))
        .thenReturn(Optional.of(environment));
    doNothing().when(instanceSyncMonitoringService).recordMetrics(any(), eq(true), anyBoolean(), anyLong());
    Call<RestResponse<Boolean>> request = mock(Call.class);
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(true)));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncForNewDeploymentWhenInfrastructureOutcomeIsNotPresent() {
    // Construct method param - DeploymentEvent
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY1, TEST_POD_NAME1);
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, null);

    // Mock methods
    when(instanceSyncPerpetualTaskService.createPerpetualTask(any(), any(), any(), any()))
        .thenReturn(PERPETUAL_TASK_ID);
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncPerpetualTaskInfoService.save(any()))
        .thenReturn(getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    // Verify 1 instance is added. Delete and Update are never called
    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncForNewDeploymentForInstanceSyncV2() {
    // Construct method param - DeploymentEvent
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY1, TEST_POD_NAME1);
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, null);

    // Mock methods
    when(instanceSyncPerpetualTaskService.createPerpetualTask(any(), any(), any(), any()))
        .thenReturn(PERPETUAL_TASK_ID);
    when(instanceSyncPerpetualTaskService.isInstanceSyncV2Enabled()).thenReturn(true);
    when(instanceSyncPerpetualTaskService.createPerpetualTaskV2(any(), any(), any())).thenReturn("perpetualTaskId");
    when(connectorService.getByRef(any(), any(), any(), anyString()))
        .thenReturn(Optional.ofNullable(
            ConnectorResponseDTO.builder()
                .connector(ConnectorInfoDTO.builder().orgIdentifier("orgId").projectIdentifier("projectId").build())
                .build()));
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncPerpetualTaskInfoService.save(any()))
        .thenReturn(getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    when(instanceSyncPerpetualTaskMappingService.save(any())).thenReturn(getMockInstanceSyncPerpetualTaskMapping());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    // Verify 1 instance is added. Delete and Update are never called
    verify(instanceSyncPerpetualTaskMappingService, times(1)).save(any());
    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncForNewDeploymentForAFreshDeployment() {
    // Construct method param - DeploymentEvent
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY1, TEST_POD_NAME1);
    K8sDirectInfrastructureOutcome infrastructureOutcome = getMockInfraOutcome();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, infrastructureOutcome);

    // Mock methods
    when(instanceSyncPerpetualTaskService.createPerpetualTask(any(), any(), any(), any()))
        .thenReturn(PERPETUAL_TASK_ID);
    when(instanceSyncPerpetualTaskInfoService.save(any()))
        .thenReturn(getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1));
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    // Verify 1 instance is added. Delete and Update are never called
    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncForNewDeploymentForAReRun() {
    // Construct method param - DeploymentEvent
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY2, TEST_POD_NAME2);
    K8sDirectInfrastructureOutcome infrastructureOutcome = getMockInfraOutcome();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, infrastructureOutcome);

    // Mock methods
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1);
    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(TEST_INFRA_MAPPING_ID))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    InstanceDTO instanceDTO = getMockInstanceDTO(TEST_POD_NAME1, TEST_INSTANCESYNC_KEY1);
    when(instanceService.getActiveInstancesByInfrastructureMappingId(
             TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_INFRA_MAPPING_ID))
        .thenReturn(Collections.singletonList(instanceDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstances(any(), any())).thenReturn(mockSyncKeyToInstances(instanceDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    // Verify old instance is deleted and new instance is added. Update is never called.
    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY2, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
    InstanceDTO instanceToBeDeleted = instancesToBeModified.get(OperationsOnInstances.DELETE).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeDeleted.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeDeleted.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncForNewDeploymentForAReRunWhenDeploymentSummaryIsPresent() {
    // Construct method param - DeploymentEvent
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY1, TEST_POD_NAME1);
    K8sDirectInfrastructureOutcome infrastructureOutcome = getMockInfraOutcome();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, infrastructureOutcome);

    // Mock methods
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1);
    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(TEST_INFRA_MAPPING_ID))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    // Construct method param - DeploymentEvent
    deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY2, TEST_POD_NAME2);
    deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, infrastructureOutcome);

    // Mock old instance
    InstanceDTO instanceDTO = getMockInstanceDTO(TEST_POD_NAME1, TEST_INSTANCESYNC_KEY1);
    when(instanceService.getActiveInstancesByInfrastructureMappingId(
             TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_INFRA_MAPPING_ID))
        .thenReturn(Collections.singletonList(getMockInstanceDTO(TEST_POD_NAME1, TEST_INSTANCESYNC_KEY1)));
    when(instanceSyncServiceUtils.getSyncKeyToInstances(any(), any())).thenReturn(mockSyncKeyToInstances(instanceDTO));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    verify(instanceSyncServiceUtils, times(2)).processInstances(instancesToBeModifiedCaptor.capture());
    List<Map<OperationsOnInstances, List<InstanceDTO>>> instancesToBeModifiedList =
        instancesToBeModifiedCaptor.getAllValues();
    // Verify first invocation
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedList.get(0);
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
    // Verify second invocation
    instancesToBeModified = instancesToBeModifiedList.get(1);
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeUpdated = instancesToBeModified.get(OperationsOnInstances.UPDATE).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeUpdated.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeUpdated.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncForNewDeploymentWhenIncomingDeploymentIsNotPartOfInstanceSync() {
    // Construct method param - DeploymentEvent
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY2, TEST_POD_NAME2);
    K8sDirectInfrastructureOutcome infrastructureOutcome = getMockInfraOutcome();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, infrastructureOutcome);

    // Mock methods
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME2);
    instanceSyncPerpetualTaskInfoDTO.getDeploymentInfoDetailsDTOList().get(0).setLastUsedAt(
        System.currentTimeMillis() - ONE_WEEKS_IN_MILLIS);
    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(TEST_INFRA_MAPPING_ID))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(instanceSyncPerpetualTaskInfoService.updateDeploymentInfoDetailsList(instanceSyncPerpetualTaskInfoDTO))
        .thenReturn(instanceSyncPerpetualTaskInfoDTO);
    doNothing().when(instanceSyncPerpetualTaskService).resetPerpetualTask(any(), any(), any(), any(), any(), any());
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    // Verify 1 instance is added. Delete and Update are never called
    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY2, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncForNewDeploymentWithCorruptedInstances() {
    // Construct method param - DeploymentEvent
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY1, TEST_POD_NAME2);
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, null, null);

    // Mock methods
    when(instanceSyncPerpetualTaskService.createPerpetualTask(any(), any(), any(), any()))
        .thenReturn(PERPETUAL_TASK_ID);
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncPerpetualTaskInfoService.save(any()))
        .thenReturn(getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    // Mock corrupted instances
    InfrastructureMappingDTO oldInfraMappingDTO = getMockInfraMapping(TEST_INFRA_KEY1);
    oldInfraMappingDTO.setId(OLD_INFRA_MAPPING_ID);
    oldInfraMappingDTO.setProjectIdentifier(OLD_PROJECT_ID);
    List<InfrastructureMappingDTO> infraMappingDTOs =
        new ArrayList<>(Arrays.asList(oldInfraMappingDTO, deploymentSummaryDTO.getInfrastructureMapping()));
    when(infrastructureMappingService.getAllByInfrastructureKey(TEST_ACCOUNT_ID, TEST_INFRA_KEY1))
        .thenReturn(infraMappingDTOs);

    InstanceDTO instanceDTO = getMockInstanceDTO(TEST_POD_NAME1, TEST_INSTANCESYNC_KEY1);
    when(instanceService.getActiveInstancesByInfrastructureMappingId(
             TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, OLD_INFRA_MAPPING_ID))
        .thenReturn(Collections.singletonList(instanceDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstances(any(), any())).thenReturn(mockSyncKeyToInstances(instanceDTO));
    doNothing().when(instanceService).updateInfrastructureMapping(anyList(), eq(TEST_INFRA_MAPPING_ID));

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);

    // Verify 1 instance is added. Delete and Update are never called
    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    InstanceDTO instanceToBeDeleted = instancesToBeModified.get(OperationsOnInstances.DELETE).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeDeleted.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeDeleted.getInfrastructureMappingId());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY2, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncByPerpetualTaskWhenServerInstanceIsNotPresent() {
    InstanceSyncPerpetualTaskResponse response = K8sInstanceSyncPerpetualTaskResponse.builder().build();
    instanceSyncService.processInstanceSyncByPerpetualTask(TEST_ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(instanceSyncPerpetualTaskInfoService, never()).findByPerpetualTaskId(TEST_ACCOUNT_ID, PERPETUAL_TASK_ID);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncByPerpetualTaskWhenPerpetualTaskIsNotPresent1() {
    InstanceSyncPerpetualTaskResponse response =
        K8sInstanceSyncPerpetualTaskResponse.builder().serverInstanceDetails(Collections.emptyList()).build();

    // Mock
    when(instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(anyString(), anyString()))
        .thenReturn(Optional.empty());

    instanceSyncService.processInstanceSyncByPerpetualTask(TEST_ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(instanceSyncPerpetualTaskService, times(1)).deletePerpetualTask(TEST_ACCOUNT_ID, PERPETUAL_TASK_ID);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncByPerpetualTaskWhenInfrastructureMappingDTOIsNotPresent() {
    InstanceSyncPerpetualTaskResponse response = K8sInstanceSyncPerpetualTaskResponse.builder()
                                                     .serverInstanceDetails(Collections.singletonList(
                                                         getMockServerInstanceInfo(TEST_POD_NAME1, TEST_RELEASE_NAME1)))
                                                     .build();

    // Mock
    when(instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(anyString(), anyString()))
        .thenReturn(Optional.of(getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1)));

    instanceSyncService.processInstanceSyncByPerpetualTask(TEST_ACCOUNT_ID, PERPETUAL_TASK_ID, response);
    verify(instanceService, never())
        .getActiveInstancesByInfrastructureMappingId(
            TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_INFRA_MAPPING_ID);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncByPerpetualTask() {
    InstanceSyncPerpetualTaskResponse response = K8sInstanceSyncPerpetualTaskResponse.builder()
                                                     .serverInstanceDetails(Collections.singletonList(
                                                         getMockServerInstanceInfo(TEST_POD_NAME1, TEST_RELEASE_NAME1)))
                                                     .build();

    // Mock
    when(instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(anyString(), anyString()))
        .thenReturn(Optional.of(getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1)));
    InfrastructureMappingDTO infraMappingDTO = getMockInfraMapping(TEST_INFRA_KEY1);
    when(infrastructureMappingService.getByInfrastructureMappingId(TEST_INFRA_MAPPING_ID))
        .thenReturn(Optional.of(infraMappingDTO));
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY1, TEST_POD_NAME1);
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    instanceSyncService.processInstanceSyncByPerpetualTask(TEST_ACCOUNT_ID, PERPETUAL_TASK_ID, response);

    // Verify 1 instance is added. Delete and Update are never called
    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncByPerpetualTaskWhenInstancesArePresentInDB() {
    InstanceSyncPerpetualTaskResponse response = K8sInstanceSyncPerpetualTaskResponse.builder()
                                                     .serverInstanceDetails(Collections.singletonList(
                                                         getMockServerInstanceInfo(TEST_POD_NAME2, TEST_RELEASE_NAME1)))
                                                     .build();

    // Mock
    when(instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(anyString(), anyString()))
        .thenReturn(Optional.of(getMockInstanceSyncPerpetualTaskInfo(TEST_RELEASE_NAME1)));
    InfrastructureMappingDTO infraMappingDTO = getMockInfraMapping(TEST_INFRA_KEY2);
    when(infrastructureMappingService.getByInfrastructureMappingId(TEST_INFRA_MAPPING_ID))
        .thenReturn(Optional.of(infraMappingDTO));
    DeploymentSummaryDTO deploymentSummaryDTO = getMockDeploymentSummary(TEST_INFRA_KEY2, TEST_POD_NAME2);
    when(deploymentSummaryService.getLatestByInstanceKey(
             TEST_RELEASE_NAME1, deploymentSummaryDTO.getInfrastructureMapping()))
        .thenReturn(Optional.of(deploymentSummaryDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstancesFromServerMap(any(), any()))
        .thenReturn(mockSyncKeyToInstancesFromServerMap(deploymentSummaryDTO.getServerInstanceInfoList().get(0)));
    when(instanceSyncServiceUtils.initMapForTrackingFinalListOfInstances()).thenReturn(initInstancesToBeModified());

    // Mock old instance
    InstanceDTO instanceDTO = getMockInstanceDTO(TEST_POD_NAME1, TEST_INSTANCESYNC_KEY1);
    when(instanceService.getActiveInstancesByInfrastructureMappingId(
             TEST_ACCOUNT_ID, TEST_ORG_ID, TEST_PROJECT_ID, TEST_INFRA_MAPPING_ID))
        .thenReturn(Collections.singletonList(instanceDTO));
    when(instanceSyncServiceUtils.getSyncKeyToInstances(any(), any())).thenReturn(mockSyncKeyToInstances(instanceDTO));

    instanceSyncService.processInstanceSyncByPerpetualTask(TEST_ACCOUNT_ID, PERPETUAL_TASK_ID, response);

    verify(instanceSyncServiceUtils).processInstances(instancesToBeModifiedCaptor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = instancesToBeModifiedCaptor.getValue();
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.DELETE).size());
    assertEquals(0, instancesToBeModified.get(OperationsOnInstances.UPDATE).size());
    assertEquals(1, instancesToBeModified.get(OperationsOnInstances.ADD).size());
    InstanceDTO instanceToBeAdded = instancesToBeModified.get(OperationsOnInstances.ADD).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY2, instanceToBeAdded.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeAdded.getInfrastructureMappingId());
    InstanceDTO instanceToBeDeleted = instancesToBeModified.get(OperationsOnInstances.DELETE).get(0);
    assertEquals(TEST_INSTANCESYNC_KEY1, instanceToBeDeleted.getInstanceKey());
    assertEquals(TEST_INFRA_MAPPING_ID, instanceToBeDeleted.getInfrastructureMappingId());
  }

  private Map<String, List<InstanceDTO>> mockSyncKeyToInstances(InstanceDTO instanceDTO) {
    K8sInstanceInfoDTO k8sInstanceInfoDTO = (K8sInstanceInfoDTO) instanceDTO.getInstanceInfoDTO();
    Map<String, List<InstanceDTO>> syncKeyToInstanceMap = new HashMap<>();
    syncKeyToInstanceMap.put(k8sInstanceInfoDTO.getReleaseName(), Collections.singletonList(instanceDTO));
    return syncKeyToInstanceMap;
  }

  private Map<String, List<InstanceInfoDTO>> mockSyncKeyToInstancesFromServerMap(
      ServerInstanceInfo serverInstanceInfo) {
    K8sServerInstanceInfo k8sServerInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfo;
    K8sInstanceInfoDTO k8sInstanceInfoDTO = K8sInstanceInfoDTO.builder()
                                                .podName(k8sServerInstanceInfo.getName())
                                                .namespace(k8sServerInstanceInfo.getNamespace())
                                                .releaseName(k8sServerInstanceInfo.getReleaseName())
                                                .podIP(k8sServerInstanceInfo.getPodIP())
                                                .blueGreenColor(k8sServerInstanceInfo.getBlueGreenColor())
                                                .containerList(k8sServerInstanceInfo.getContainerList())
                                                .build();
    Map<String, List<InstanceInfoDTO>> syncKeyToInstancesFromServerMap = new HashMap<>();
    syncKeyToInstancesFromServerMap.put(
        k8sInstanceInfoDTO.getReleaseName(), Collections.singletonList(k8sInstanceInfoDTO));
    return syncKeyToInstancesFromServerMap;
  }

  private Map<OperationsOnInstances, List<InstanceDTO>> initInstancesToBeModified() {
    Map<OperationsOnInstances, List<InstanceDTO>> instancesToBeModified = new HashMap<>();
    instancesToBeModified.put(OperationsOnInstances.ADD, new ArrayList<>());
    instancesToBeModified.put(OperationsOnInstances.DELETE, new ArrayList<>());
    instancesToBeModified.put(OperationsOnInstances.UPDATE, new ArrayList<>());
    return instancesToBeModified;
  }

  private InstanceDTO getMockInstanceDTO(String podName, String instanceSyncKey) {
    InstanceInfoDTO instanceInfoDTO = K8sInstanceInfoDTO.builder()
                                          .namespace(TEST_NAMESPACE)
                                          .releaseName(TEST_RELEASE_NAME1)
                                          .podName(podName)
                                          .podIP(TEST_POD_IP)
                                          .blueGreenColor(BLUE_GREEN_COLOR)
                                          .containerList(Collections.singletonList(getMockK8sContainer()))
                                          .build();
    return InstanceDTO.builder()
        .uuid(TEST_UUID)
        .accountIdentifier(TEST_ACCOUNT_ID)
        .orgIdentifier(TEST_ORG_ID)
        .projectIdentifier(TEST_PROJECT_ID)
        .instanceKey(instanceSyncKey)
        .instanceType(InstanceType.K8S_INSTANCE)
        .envIdentifier(TEST_ENV_ID)
        .envName(TEST_ENV_NAME)
        .envType(EnvironmentType.PreProduction)
        .serviceIdentifier(TEST_SERVICE_ID)
        .serviceName(TEST_SERVICE_NAME)
        .infrastructureMappingId(TEST_INFRA_MAPPING_ID)
        .infrastructureKind(TEST_INFRA_KIND)
        .infraIdentifier(TEST_INFRA_ID)
        .infraName(TEST_INFRA_NAME)
        .connectorRef(TEST_CONNECTOR_REF)
        .instanceInfoDTO(instanceInfoDTO)
        .build();
  }

  private InfrastructureMappingDTO getMockInfraMapping(String infraKey) {
    return InfrastructureMappingDTO.builder()
        .id(TEST_INFRA_MAPPING_ID)
        .accountIdentifier(TEST_ACCOUNT_ID)
        .orgIdentifier(TEST_ORG_ID)
        .projectIdentifier(TEST_PROJECT_ID)
        .infrastructureKind(TEST_INFRA_KIND)
        .connectorRef(TEST_CONNECTOR_REF)
        .envIdentifier(TEST_ENV_ID)
        .serviceIdentifier(TEST_SERVICE_ID)
        .infrastructureKey(infraKey)
        .build();
  }

  private ServerInstanceInfo getMockServerInstanceInfo(String podName, String releaseName) {
    return K8sServerInstanceInfo.builder()
        .name(podName)
        .podIP(TEST_POD_IP)
        .blueGreenColor(BLUE_GREEN_COLOR)
        .namespace(TEST_NAMESPACE)
        .releaseName(releaseName)
        .containerList(Collections.singletonList(getMockK8sContainer()))
        .build();
  }

  private K8sContainer getMockK8sContainer() {
    return K8sContainer.builder()
        .containerId(TEST_CONTAINER_ID)
        .name(TEST_CONTAINER_NAME)
        .image(TEST_CONTAINER_IMAGE)
        .build();
  }

  private DeploymentInfoDTO getMockDeploymentInfo(String releaseName) {
    return K8sDeploymentInfoDTO.builder()
        .releaseName(releaseName)
        .namespaces(new LinkedHashSet<>(Collections.singletonList(TEST_NAMESPACE)))
        .blueGreenStageColor(BLUE_GREEN_COLOR)
        .build();
  }

  private DeploymentSummaryDTO getMockDeploymentSummary(String infraKey, String podName) {
    InfrastructureMappingDTO infraMappingDTO = getMockInfraMapping(infraKey);
    DeploymentInfoDTO deploymentInfoDTO = getMockDeploymentInfo(TEST_RELEASE_NAME1);
    ServerInstanceInfo serverInstanceInfo = getMockServerInstanceInfo(podName, TEST_RELEASE_NAME1);
    return DeploymentSummaryDTO.builder()
        .infrastructureMappingId(TEST_INFRA_MAPPING_ID)
        .infrastructureMapping(infraMappingDTO)
        .deploymentInfoDTO(deploymentInfoDTO)
        .serverInstanceInfoList(Collections.singletonList(serverInstanceInfo))
        .instanceSyncKey(TEST_RELEASE_NAME1)
        .build();
  }

  private K8sDirectInfrastructureOutcome getMockInfraOutcome() {
    K8sDirectInfrastructureOutcome infrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
    infrastructureOutcome.setInfraIdentifier(TEST_INFRA_ID);
    infrastructureOutcome.setInfraName(TEST_INFRA_NAME);
    return infrastructureOutcome;
  }

  private InstanceSyncPerpetualTaskMappingDTO getMockInstanceSyncPerpetualTaskMapping() {
    return InstanceSyncPerpetualTaskMappingDTO.builder()
        .accountId("accountId")
        .orgId("orgId")
        .perpetualTaskId(PERPETUAL_TASK_ID)
        .connectorIdentifier(TEST_CONNECTOR_REF)
        .build();
  }
  private InstanceSyncPerpetualTaskInfoDTO getMockInstanceSyncPerpetualTaskInfo(String releaseName) {
    DeploymentInfoDTO deploymentInfoDTOOld = getMockDeploymentInfo(releaseName);
    DeploymentInfoDetailsDTO deploymentInfoDetailsDTO =
        DeploymentInfoDetailsDTO.builder().deploymentInfoDTO(deploymentInfoDTOOld).build();
    return InstanceSyncPerpetualTaskInfoDTO.builder()
        .deploymentInfoDetailsDTOList(new ArrayList(Collections.singletonList(deploymentInfoDetailsDTO)))
        .perpetualTaskId(PERPETUAL_TASK_ID)
        .infrastructureMappingId(TEST_INFRA_MAPPING_ID)
        .build();
  }
}
