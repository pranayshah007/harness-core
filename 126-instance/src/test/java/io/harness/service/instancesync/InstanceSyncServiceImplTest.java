/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.account.AccountClient;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.cdng.infra.beans.SshWinRmAzureInfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.K8sInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AzureSshWinrmServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.InstanceDTO;
import io.harness.dtos.deploymentinfo.AzureSshWinrmDeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.instanceinfo.AzureSshWinrmInstanceInfoDTO;
import io.harness.dtos.instanceinfo.InstanceInfoDTO;
import io.harness.dtos.instanceinfo.K8sInstanceInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.DeploymentInfoDetailsDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.InstanceSyncPerpetualTaskMappingService;
import io.harness.exception.EntityNotFoundException;
import io.harness.helper.InstanceSyncHelper;
import io.harness.instancesyncmonitoring.service.InstanceSyncMonitoringService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.logging.CommandExecutionStatus;
import io.harness.models.DeploymentEvent;
import io.harness.models.RollbackInfo;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.InstanceSyncData;
import io.harness.perpetualtask.instancesync.InstanceSyncResponseV2;
import io.harness.perpetualtask.instancesync.InstanceSyncStatus;
import io.harness.perpetualtask.instancesync.InstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesync.k8s.K8sDeploymentReleaseDetails;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.PageImpl;
import retrofit2.Call;
import retrofit2.Response;

public class InstanceSyncServiceImplTest extends InstancesTestBase {
  @Mock AbstractInstanceSyncHandler abstractInstanceSyncHandler;
  @Mock AcquiredLock<?> acquiredLock;
  @Mock PersistentLocker persistentLocker;
  @Mock InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  @Mock InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  @Mock InstanceSyncHandlerFactoryService instanceSyncHandlerFactoryService;
  @Mock InfrastructureMappingService infrastructureMappingService;
  @Mock InstanceService instanceService;
  @Mock DeploymentSummaryService deploymentSummaryService;
  @Mock InstanceSyncHelper instanceSyncHelper;
  @Spy @InjectMocks InstanceSyncServiceUtils instanceSyncServiceUtils;
  @InjectMocks InstanceSyncServiceImpl instanceSyncService;
  @Mock private InstanceSyncMonitoringService instanceSyncMonitoringService;
  @Mock private AccountClient accountClient;
  @Mock private InstanceSyncPerpetualTaskMappingService instanceSyncPerpetualTaskMappingService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private ConnectorService connectorService;

  private final String ACCOUNT_IDENTIFIER = "acc";
  private final String PERPETUAL_TASK = "perp";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String ORG_IDENTIFIER = "org";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENV_IDENTIFIER = "env";
  private final String CONNECTOR_REF = "conn";
  private final String INFRASTRUCTURE_KEY = "key";
  private final String INFRASTRUCTURE_MAPPING_ID = "inframappingid";
  private final String ID = "id";
  private final String HOST1 = "host1";
  private final String HOST2 = "host2";
  private final String HOST3 = "host3";

  @Before
  public void setup() {
    doNothing().when(instanceSyncMonitoringService).recordMetrics(any(), eq(true), anyBoolean(), anyLong());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void processInstanceSyncForNewDeploymentTestWithFailure() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    DeploymentInfoDTO deploymentInfoDTO = K8sDeploymentInfoDTO.builder().build();
    DeploymentSummaryDTO deploymentSummaryDTO = DeploymentSummaryDTO.builder()
                                                    .instanceSyncKey("sunc")
                                                    .infrastructureMapping(infrastructureMappingDTO)
                                                    .deploymentInfoDTO(deploymentInfoDTO)
                                                    .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
                                                    .serverInstanceInfoList(emptyList())
                                                    .build();
    RollbackInfo rollbackInfo = RollbackInfo.builder().build();
    InfrastructureOutcome infrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, rollbackInfo, infrastructureOutcome);
    when(persistentLocker.waitToAcquireLock(
             InstanceSyncConstants.INSTANCE_SYNC_PREFIX + deploymentSummaryDTO.getInfrastructureMappingId(),
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder().build();
    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(infrastructureMappingDTO.getId()))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(instanceSyncPerpetualTaskService.createPerpetualTask(infrastructureMappingDTO, abstractInstanceSyncHandler,
             Collections.singletonList(deploymentSummaryDTO.getDeploymentInfoDTO()),
             deploymentEvent.getInfrastructureOutcome()))
        .thenReturn(PERPETUAL_TASK);
    when(instanceSyncPerpetualTaskInfoService.save(any())).thenReturn(instanceSyncPerpetualTaskInfoDTO);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             deploymentSummaryDTO.getDeploymentInfoDTO().getType(), infrastructureOutcome.getKind()))
        .thenReturn(abstractInstanceSyncHandler);
    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);
    verify(instanceSyncHandlerFactoryService, times(3))
        .getInstanceSyncHandler(deploymentSummaryDTO.getDeploymentInfoDTO().getType(), infrastructureOutcome.getKind());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void processInstanceSyncForNewDeploymentTestWithSuccessAdd() throws IOException {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(INFRASTRUCTURE_MAPPING_ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.SSH_WINRM_AZURE)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    DeploymentInfoDTO deploymentInfoDTO =
        AzureSshWinrmDeploymentInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST3).build();
    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder()
            .instanceSyncKey("AzureSshWinrmInstanceInfoDTO_host3_key")
            .infrastructureMapping(infrastructureMappingDTO)
            .deploymentInfoDTO(deploymentInfoDTO)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .serverInstanceInfoList(List.of(
                AzureSshWinrmServerInstanceInfo.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST3).build()))
            .build();
    doReturn(Optional.of(deploymentSummaryDTO))
        .when(deploymentSummaryService)
        .getLatestByInstanceKey(anyString(), any());

    RollbackInfo rollbackInfo = RollbackInfo.builder().build();
    InfrastructureOutcome infrastructureOutcome = SshWinRmAzureInfrastructureOutcome.builder().build();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, rollbackInfo, infrastructureOutcome);

    when(persistentLocker.waitToAcquireLock(
             InstanceSyncConstants.INSTANCE_SYNC_PREFIX + deploymentSummaryDTO.getInfrastructureMappingId(),
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOS = new ArrayList<>();

    deploymentInfoDetailsDTOS.add(
        DeploymentInfoDetailsDTO.builder()
            .deploymentInfoDTO(
                AzureSshWinrmDeploymentInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST1).build())
            .build());
    deploymentInfoDetailsDTOS.add(
        DeploymentInfoDetailsDTO.builder()
            .deploymentInfoDTO(
                AzureSshWinrmDeploymentInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST2).build())
            .build());

    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder().deploymentInfoDetailsDTOList(deploymentInfoDetailsDTOS).build();

    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(infrastructureMappingDTO.getId()))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(instanceSyncPerpetualTaskService.createPerpetualTask(infrastructureMappingDTO, abstractInstanceSyncHandler,
             Collections.singletonList(deploymentSummaryDTO.getDeploymentInfoDTO()),
             deploymentEvent.getInfrastructureOutcome()))
        .thenReturn(PERPETUAL_TASK);
    when(instanceSyncPerpetualTaskInfoService.save(any())).thenReturn(instanceSyncPerpetualTaskInfoDTO);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             deploymentSummaryDTO.getDeploymentInfoDTO().getType(), infrastructureOutcome.getKind()))
        .thenReturn(abstractInstanceSyncHandler);
    Call<RestResponse<Boolean>> request = mock(Call.class);
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    List<InstanceDTO> instanceDTOS = new ArrayList<>();
    instanceDTOS.add(
        InstanceDTO.builder()
            .instanceInfoDTO(
                AzureSshWinrmInstanceInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST1).build())
            .build());
    instanceDTOS.add(
        InstanceDTO.builder()
            .instanceInfoDTO(
                AzureSshWinrmInstanceInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST2).build())
            .build());

    doReturn(instanceDTOS)
        .when(instanceService)
        .getActiveInstancesByInfrastructureMappingId(infrastructureMappingDTO.getAccountIdentifier(),
            infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
            infrastructureMappingDTO.getId());
    when(abstractInstanceSyncHandler.getInstanceSyncHandlerKey(any(InstanceInfoDTO.class))).thenAnswer(invocation -> {
      InstanceInfoDTO instanceInfoDTO = invocation.getArgument(0);
      return instanceInfoDTO.prepareInstanceSyncHandlerKey();
    });

    when(abstractInstanceSyncHandler.getInstanceKey(any(InstanceInfoDTO.class))).thenAnswer(invocation -> {
      InstanceInfoDTO instanceInfoDTO = invocation.getArgument(0);
      return instanceInfoDTO.prepareInstanceKey();
    });

    doAnswer(invocation -> {
      List<ServerInstanceInfo> instanceInfoList = invocation.getArgument(0, List.class);
      return instanceInfoList.stream()
          .map(instanceInfo -> {
            AzureSshWinrmServerInstanceInfo s = (AzureSshWinrmServerInstanceInfo) instanceInfo;
            return AzureSshWinrmInstanceInfoDTO.builder()
                .host(s.getHost())
                .infrastructureKey(s.getInfrastructureKey())
                .build();
          })
          .collect(Collectors.toCollection(ArrayList::new));
    })
        .when(abstractInstanceSyncHandler)
        .getInstanceDetailsFromServerInstances(deploymentSummaryDTO.getServerInstanceInfoList());

    doReturn(Environment.builder().build()).when(instanceSyncHelper).fetchEnvironment(any());
    doReturn(ServiceEntity.builder().build()).when(instanceSyncHelper).fetchService(any());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);
    ArgumentCaptor<Map<OperationsOnInstances, List<InstanceDTO>>> captor = ArgumentCaptor.forClass(Map.class);
    verify(instanceSyncServiceUtils).processInstances(captor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> value = captor.getValue();
    assertThat(value).hasSize(3);
    assertThat(value.get(OperationsOnInstances.ADD)).hasSize(1);
    assertThat(value.get(OperationsOnInstances.DELETE)).hasSize(0);
    assertThat(value.get(OperationsOnInstances.UPDATE)).hasSize(0);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void processInstanceSyncForNewDeploymentInstanceSyncV2() throws IOException {
    when(instanceSyncPerpetualTaskService.isInstanceSyncV2Enabled()).thenReturn(true);
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(INFRASTRUCTURE_MAPPING_ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    LinkedHashSet<String> namespaces = new LinkedHashSet<>();
    namespaces.add("namespace");
    DeploymentInfoDTO deploymentInfoDTO =
        K8sDeploymentInfoDTO.builder().releaseName("releaseName").namespaces(namespaces).build();
    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder()
            .instanceSyncKey("K8sDeploymentInfoDTO_namespace_key")
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJECT_IDENTIFIER)
            .infrastructureMapping(infrastructureMappingDTO)
            .deploymentInfoDTO(deploymentInfoDTO)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .serverInstanceInfoList(List.of(
                K8sServerInstanceInfo.builder().namespace("namespace").name("pod1").releaseName("releaseName").build()))
            .build();
    doReturn(Optional.of(deploymentSummaryDTO))
        .when(deploymentSummaryService)
        .getLatestByInstanceKey(anyString(), any());

    RollbackInfo rollbackInfo = RollbackInfo.builder().build();
    InfrastructureOutcome infrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, rollbackInfo, infrastructureOutcome);

    when(persistentLocker.waitToAcquireLock(
             InstanceSyncConstants.INSTANCE_SYNC_PREFIX + deploymentSummaryDTO.getInfrastructureMappingId(),
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOS = new ArrayList<>();

    deploymentInfoDetailsDTOS.add(
        DeploymentInfoDetailsDTO.builder()
            .deploymentInfoDTO(K8sDeploymentInfoDTO.builder().releaseName("releaseName").namespaces(namespaces).build())
            .build());

    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder().deploymentInfoDetailsDTOList(deploymentInfoDetailsDTOS).build();

    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(infrastructureMappingDTO.getId()))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .name(CONNECTOR_REF)
                                            .identifier(CONNECTOR_REF)
                                            .description("description")
                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                            .orgIdentifier(ORG_IDENTIFIER)
                                            .connectorType(KUBERNETES_CLUSTER)
                                            .connectorConfig(KubernetesClusterConfigDTO.builder().build())
                                            .build();
    when(instanceSyncPerpetualTaskMappingService.findByConnectorRef(infrastructureMappingDTO.getAccountIdentifier(),
             connectorInfoDTO.getOrgIdentifier(), connectorInfoDTO.getProjectIdentifier(),
             infrastructureMappingDTO.getConnectorRef()))
        .thenReturn(Optional.empty());
    when(connectorService.getByRef(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder().connector(connectorInfoDTO).build()));
    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(anyString()))
        .thenReturn(Optional.of(InstanceSyncPerpetualTaskInfoDTO.builder()
                                    .perpetualTaskId(PERPETUAL_TASK)
                                    .connectorIdentifier(CONNECTOR_REF)
                                    .build()));
    when(instanceSyncPerpetualTaskInfoService.save(any())).thenReturn(instanceSyncPerpetualTaskInfoDTO);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             deploymentSummaryDTO.getDeploymentInfoDTO().getType(), infrastructureOutcome.getKind()))
        .thenReturn(abstractInstanceSyncHandler);
    when(instanceSyncPerpetualTaskInfoService.updateDeploymentInfoListAndConnectorId(any(), any()))
        .thenReturn(InstanceSyncPerpetualTaskInfoDTO.builder()
                        .perpetualTaskId(PERPETUAL_TASK)
                        .connectorIdentifier(CONNECTOR_REF)
                        .build());
    Call<RestResponse<Boolean>> request = mock(Call.class);
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    List<InstanceDTO> instanceDTOS = new ArrayList<>();
    instanceDTOS.add(
        InstanceDTO.builder()
            .instanceInfoDTO(K8sInstanceInfoDTO.builder().namespace("namespace").releaseName("releaseName").build())
            .build());

    doReturn(instanceDTOS)
        .when(instanceService)
        .getActiveInstancesByInfrastructureMappingId(infrastructureMappingDTO.getAccountIdentifier(),
            infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
            infrastructureMappingDTO.getId());
    when(abstractInstanceSyncHandler.getInstanceSyncHandlerKey(any(InstanceInfoDTO.class))).thenAnswer(invocation -> {
      InstanceInfoDTO instanceInfoDTO = invocation.getArgument(0);
      return instanceInfoDTO.prepareInstanceSyncHandlerKey();
    });

    when(abstractInstanceSyncHandler.getInstanceKey(any(InstanceInfoDTO.class))).thenAnswer(invocation -> {
      InstanceInfoDTO instanceInfoDTO = invocation.getArgument(0);
      return instanceInfoDTO.prepareInstanceKey();
    });

    doAnswer(invocation -> {
      List<ServerInstanceInfo> instanceInfoList = invocation.getArgument(0, List.class);
      return instanceInfoList.stream()
          .map(instanceInfo -> {
            K8sServerInstanceInfo s = (K8sServerInstanceInfo) instanceInfo;
            return K8sServerInstanceInfo.builder()
                .namespace("namespace")
                .name("Pod1")
                .releaseName("releaseName")
                .build();
          })
          .collect(Collectors.toCollection(ArrayList::new));
    })
        .when(abstractInstanceSyncHandler)
        .getInstanceDetailsFromServerInstances(deploymentSummaryDTO.getServerInstanceInfoList());

    doReturn(Environment.builder().build()).when(instanceSyncHelper).fetchEnvironment(any());
    doReturn(ServiceEntity.builder().build()).when(instanceSyncHelper).fetchService(any());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);
    ArgumentCaptor<Map<OperationsOnInstances, List<InstanceDTO>>> captor = ArgumentCaptor.forClass(Map.class);
    verify(instanceSyncPerpetualTaskService, times(3)).createPerpetualTaskV2(any(), any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void processInstanceSyncForNewDeploymentTestWithSuccessUpdate() throws IOException {
    ArtifactDetails artifactDetails = ArtifactDetails.builder().artifactId(ID).build();
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(INFRASTRUCTURE_MAPPING_ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.SSH_WINRM_AZURE)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    DeploymentInfoDTO deploymentInfoDTO =
        AzureSshWinrmDeploymentInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST2).build();
    DeploymentSummaryDTO deploymentSummaryDTO =
        DeploymentSummaryDTO.builder()
            .instanceSyncKey("AzureSshWinrmInstanceInfoDTO_host3_key")
            .infrastructureMapping(infrastructureMappingDTO)
            .deploymentInfoDTO(deploymentInfoDTO)
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .artifactDetails(artifactDetails)
            .serverInstanceInfoList(Arrays.asList(
                AzureSshWinrmServerInstanceInfo.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST2).build()))
            .build();
    doReturn(Optional.of(deploymentSummaryDTO))
        .when(deploymentSummaryService)
        .getLatestByInstanceKey(anyString(), any());

    RollbackInfo rollbackInfo = RollbackInfo.builder().build();
    InfrastructureOutcome infrastructureOutcome = SshWinRmAzureInfrastructureOutcome.builder().build();
    DeploymentEvent deploymentEvent = new DeploymentEvent(deploymentSummaryDTO, rollbackInfo, infrastructureOutcome);

    when(persistentLocker.waitToAcquireLock(
             InstanceSyncConstants.INSTANCE_SYNC_PREFIX + deploymentSummaryDTO.getInfrastructureMappingId(),
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    List<DeploymentInfoDetailsDTO> deploymentInfoDetailsDTOS = new ArrayList<>();

    deploymentInfoDetailsDTOS.add(
        DeploymentInfoDetailsDTO.builder()
            .deploymentInfoDTO(
                AzureSshWinrmDeploymentInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST1).build())
            .build());
    deploymentInfoDetailsDTOS.add(
        DeploymentInfoDetailsDTO.builder()
            .deploymentInfoDTO(
                AzureSshWinrmDeploymentInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST2).build())
            .build());

    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder().deploymentInfoDetailsDTOList(deploymentInfoDetailsDTOS).build();

    when(instanceSyncPerpetualTaskInfoService.findByInfrastructureMappingId(infrastructureMappingDTO.getId()))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(instanceSyncPerpetualTaskService.createPerpetualTask(infrastructureMappingDTO, abstractInstanceSyncHandler,
             Collections.singletonList(deploymentSummaryDTO.getDeploymentInfoDTO()),
             deploymentEvent.getInfrastructureOutcome()))
        .thenReturn(PERPETUAL_TASK);
    when(instanceSyncPerpetualTaskInfoService.save(any())).thenReturn(instanceSyncPerpetualTaskInfoDTO);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             deploymentSummaryDTO.getDeploymentInfoDTO().getType(), infrastructureOutcome.getKind()))
        .thenReturn(abstractInstanceSyncHandler);
    Call<RestResponse<Boolean>> request = mock(Call.class);
    when(request.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    List<InstanceDTO> instanceDTOS = new ArrayList<>();
    instanceDTOS.add(
        InstanceDTO.builder()
            .instanceInfoDTO(
                AzureSshWinrmInstanceInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST1).build())
            .build());
    instanceDTOS.add(
        InstanceDTO.builder()
            .instanceInfoDTO(
                AzureSshWinrmInstanceInfoDTO.builder().infrastructureKey(INFRASTRUCTURE_KEY).host(HOST2).build())
            .build());

    doReturn(instanceDTOS)
        .when(instanceService)
        .getActiveInstancesByInfrastructureMappingId(infrastructureMappingDTO.getAccountIdentifier(),
            infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
            infrastructureMappingDTO.getId());
    when(abstractInstanceSyncHandler.getInstanceSyncHandlerKey(any(InstanceInfoDTO.class))).thenAnswer(invocation -> {
      InstanceInfoDTO instanceInfoDTO = invocation.getArgument(0);
      return instanceInfoDTO.prepareInstanceSyncHandlerKey();
    });

    when(abstractInstanceSyncHandler.getInstanceKey(any(InstanceInfoDTO.class))).thenAnswer(invocation -> {
      InstanceInfoDTO instanceInfoDTO = invocation.getArgument(0);
      return instanceInfoDTO.prepareInstanceKey();
    });

    doAnswer(invocation -> {
      List<ServerInstanceInfo> instanceInfoList = invocation.getArgument(0, List.class);
      return instanceInfoList.stream()
          .map(instanceInfo -> {
            AzureSshWinrmServerInstanceInfo s = (AzureSshWinrmServerInstanceInfo) instanceInfo;
            return AzureSshWinrmInstanceInfoDTO.builder()
                .host(s.getHost())
                .infrastructureKey(s.getInfrastructureKey())
                .build();
          })
          .collect(Collectors.toCollection(ArrayList::new));
    })
        .when(abstractInstanceSyncHandler)
        .getInstanceDetailsFromServerInstances(deploymentSummaryDTO.getServerInstanceInfoList());

    doReturn(Environment.builder().build()).when(instanceSyncHelper).fetchEnvironment(any());
    doReturn(ServiceEntity.builder().build()).when(instanceSyncHelper).fetchService(any());
    doAnswer(invocation -> {
      Object[] args = invocation.getArguments();
      return args[0];
    })
        .when(abstractInstanceSyncHandler)
        .updateInstance(any(InstanceDTO.class), any());

    instanceSyncService.processInstanceSyncForNewDeployment(deploymentEvent);
    ArgumentCaptor<Map<OperationsOnInstances, List<InstanceDTO>>> captor = ArgumentCaptor.forClass(Map.class);
    verify(instanceSyncServiceUtils).processInstances(captor.capture());
    Map<OperationsOnInstances, List<InstanceDTO>> value = captor.getValue();
    assertThat(value).hasSize(3);
    assertThat(value.get(OperationsOnInstances.ADD)).hasSize(0);
    assertThat(value.get(OperationsOnInstances.DELETE)).hasSize(0);
    assertThat(value.get(OperationsOnInstances.UPDATE)).hasSize(1);
    assertThat(value.get(OperationsOnInstances.UPDATE).get(0).getPrimaryArtifact()).isEqualTo(artifactDetails);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void processInstanceSyncByPerpetualTaskTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    ServerInstanceInfo serverInstanceInfo = K8sServerInstanceInfo.builder().build();
    InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse =
        K8sInstanceSyncPerpetualTaskResponse.builder().serverInstanceDetails(Arrays.asList(serverInstanceInfo)).build();
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder().infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID).build();
    when(instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(ACCOUNT_IDENTIFIER, PERPETUAL_TASK))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(infrastructureMappingService.getByInfrastructureMappingId(
             instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId()))
        .thenReturn(Optional.of(infrastructureMappingDTO));
    when(persistentLocker.waitToAcquireLock(
             InstanceSyncConstants.INSTANCE_SYNC_PREFIX + instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId(),
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             instanceSyncPerpetualTaskResponse.getDeploymentType(), InfrastructureKind.KUBERNETES_DIRECT))
        .thenReturn(abstractInstanceSyncHandler);
    instanceSyncService.processInstanceSyncByPerpetualTask(
        ACCOUNT_IDENTIFIER, PERPETUAL_TASK, instanceSyncPerpetualTaskResponse);
    verify(instanceSyncHandlerFactoryService, times(1))
        .getInstanceSyncHandler(
            instanceSyncPerpetualTaskResponse.getDeploymentType(), InfrastructureKind.KUBERNETES_DIRECT);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void processInstanceSyncByPerpetualTaskV2Test() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    InstanceSyncResponseV2 instanceSyncResponseV2 =
        InstanceSyncResponseV2.newBuilder()
            .setPerpetualTaskId(PERPETUAL_TASK)
            .setAccountId(ACCOUNT_IDENTIFIER)
            .setStatus(InstanceSyncStatus.newBuilder()
                           .setIsSuccessful(true)
                           .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                           .build())
            .addInstanceData(InstanceSyncData.newBuilder()
                                 .setTaskInfoId("taskInfoId")
                                 .setStatus(InstanceSyncStatus.newBuilder()
                                                .setIsSuccessful(true)
                                                .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                                                .build())
                                 .setDeploymentType(ServiceSpecType.KUBERNETES)
                                 .build())
            .build();
    List<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOList =
        Arrays.asList(InstanceSyncPerpetualTaskInfoDTO.builder()
                          .id("taskInfoId")
                          .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
                          .build());
    when(instanceSyncPerpetualTaskInfoService.findAll(ACCOUNT_IDENTIFIER, PERPETUAL_TASK))
        .thenReturn(instanceSyncPerpetualTaskInfoDTOList);
    when(infrastructureMappingService.getByInfrastructureMappingId(INFRASTRUCTURE_MAPPING_ID))
        .thenReturn(Optional.of(infrastructureMappingDTO));
    when(kryoSerializer.asObject(nullable(byte[].class))).thenReturn(emptyList());
    when(persistentLocker.waitToAcquireLock(InstanceSyncConstants.INSTANCE_SYNC_PREFIX + INFRASTRUCTURE_MAPPING_ID,
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             ServiceSpecType.KUBERNETES, InfrastructureKind.KUBERNETES_DIRECT))
        .thenReturn(abstractInstanceSyncHandler);

    instanceSyncService.processInstanceSyncByPerpetualTaskV2(
        ACCOUNT_IDENTIFIER, PERPETUAL_TASK, instanceSyncResponseV2);

    verify(instanceSyncHandlerFactoryService, times(1))
        .getInstanceSyncHandler(ServiceSpecType.KUBERNETES, InfrastructureKind.KUBERNETES_DIRECT);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void processInstanceSyncByPerpetualTaskV2FailedStatusTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    InstanceSyncResponseV2 instanceSyncResponseV2 =
        InstanceSyncResponseV2.newBuilder()
            .setPerpetualTaskId(PERPETUAL_TASK)
            .setAccountId(ACCOUNT_IDENTIFIER)
            .setStatus(InstanceSyncStatus.newBuilder()
                           .setIsSuccessful(true)
                           .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                           .build())
            .addInstanceData(InstanceSyncData.newBuilder()
                                 .setTaskInfoId("taskInfoId")
                                 .setStatus(InstanceSyncStatus.newBuilder()
                                                .setIsSuccessful(false)
                                                .setExecutionStatus(CommandExecutionStatus.FAILURE.name())
                                                .build())
                                 .setDeploymentType(ServiceSpecType.KUBERNETES)
                                 .build())
            .build();
    List<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOList =
        Arrays.asList(InstanceSyncPerpetualTaskInfoDTO.builder()
                          .id("taskInfoId")
                          .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
                          .build());
    when(instanceSyncPerpetualTaskInfoService.findAll(ACCOUNT_IDENTIFIER, PERPETUAL_TASK))
        .thenReturn(instanceSyncPerpetualTaskInfoDTOList);
    when(infrastructureMappingService.getByInfrastructureMappingId(INFRASTRUCTURE_MAPPING_ID))
        .thenReturn(Optional.of(infrastructureMappingDTO));
    when(kryoSerializer.asObject(nullable(byte[].class))).thenReturn(emptyList());
    when(persistentLocker.waitToAcquireLock(InstanceSyncConstants.INSTANCE_SYNC_PREFIX + INFRASTRUCTURE_MAPPING_ID,
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             ServiceSpecType.KUBERNETES, InfrastructureKind.KUBERNETES_DIRECT))
        .thenReturn(abstractInstanceSyncHandler);

    instanceSyncService.processInstanceSyncByPerpetualTaskV2(
        ACCOUNT_IDENTIFIER, PERPETUAL_TASK, instanceSyncResponseV2);

    verify(instanceSyncHandlerFactoryService, times(0))
        .getInstanceSyncHandler(ServiceSpecType.KUBERNETES, InfrastructureKind.KUBERNETES_DIRECT);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void processInstanceSyncByPerpetualTaskV2SkippedStatusTest() {
    InstanceSyncResponseV2 instanceSyncResponseV2 =
        InstanceSyncResponseV2.newBuilder()
            .setPerpetualTaskId(PERPETUAL_TASK)
            .setAccountId(ACCOUNT_IDENTIFIER)
            .setStatus(InstanceSyncStatus.newBuilder()
                           .setIsSuccessful(true)
                           .setExecutionStatus(CommandExecutionStatus.SKIPPED.name())
                           .build())
            .build();
    List<InstanceSyncPerpetualTaskInfoDTO> instanceSyncPerpetualTaskInfoDTOList =
        Arrays.asList(InstanceSyncPerpetualTaskInfoDTO.builder()
                          .id("taskInfoId")
                          .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
                          .build());
    when(instanceSyncPerpetualTaskInfoService.findAll(ACCOUNT_IDENTIFIER, PERPETUAL_TASK))
        .thenReturn(instanceSyncPerpetualTaskInfoDTOList);

    when(kryoSerializer.asObject(nullable(byte[].class))).thenReturn(emptyList());

    instanceSyncService.processInstanceSyncByPerpetualTaskV2(
        ACCOUNT_IDENTIFIER, PERPETUAL_TASK, instanceSyncResponseV2);

    verify(instanceSyncHandlerFactoryService, times(0))
        .getInstanceSyncHandler(ServiceSpecType.KUBERNETES, InfrastructureKind.KUBERNETES_DIRECT);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void fetchTaskDetailsTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    LinkedHashSet<String> namespaces = new LinkedHashSet<>();
    namespaces.add("namespace1");
    ServerInstanceInfo serverInstanceInfo = K8sServerInstanceInfo.builder().build();
    InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse =
        K8sInstanceSyncPerpetualTaskResponse.builder().serverInstanceDetails(Arrays.asList(serverInstanceInfo)).build();

    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder()
            .id("taskInfoId")
            .infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID)
            .deploymentInfoDetailsDTOList(List.of(
                DeploymentInfoDetailsDTO.builder()
                    .deploymentInfoDTO(
                        K8sDeploymentInfoDTO.builder().releaseName("releaseName").namespaces(namespaces).build())
                    .build()))
            .build();
    when(instanceSyncPerpetualTaskInfoService.findAllInPages(any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(instanceSyncPerpetualTaskInfoDTO)));

    when(infrastructureMappingService.getByInfrastructureMappingId(
             instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId()))
        .thenReturn(Optional.of(infrastructureMappingDTO));
    when(persistentLocker.waitToAcquireLock(
             InstanceSyncConstants.INSTANCE_SYNC_PREFIX + instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId(),
             InstanceSyncConstants.INSTANCE_SYNC_LOCK_TIMEOUT, InstanceSyncConstants.INSTANCE_SYNC_WAIT_TIMEOUT))
        .thenReturn(acquiredLock);
    when(instanceSyncHandlerFactoryService.getInstanceSyncHandler(
             instanceSyncPerpetualTaskResponse.getDeploymentType(), InfrastructureKind.KUBERNETES_DIRECT))
        .thenReturn(abstractInstanceSyncHandler);

    when(abstractInstanceSyncHandler.getDeploymentReleaseDetails(instanceSyncPerpetualTaskInfoDTO))
        .thenReturn(
            DeploymentReleaseDetails.builder()
                .taskInfoId("taskInfoId")
                .deploymentDetails(List.of(
                    K8sDeploymentReleaseDetails.builder().releaseName("releaseName").namespaces(namespaces).build()))
                .build());
    InstanceSyncTaskDetails instanceSyncTaskDetails =
        instanceSyncService.fetchTaskDetails(PERPETUAL_TASK, ACCOUNT_IDENTIFIER, 0, 10);

    assertThat(instanceSyncTaskDetails).isNotNull();
    assertThat(instanceSyncTaskDetails.getDetails().getContent().size()).isEqualTo(1);
    assertThat(instanceSyncTaskDetails.getDetails().getContent().get(0).getTaskInfoId()).isEqualTo("taskInfoId");
    assertThat(instanceSyncTaskDetails.getDetails().getContent().get(0).getDeploymentDetails().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void processInstanceSyncByPerpetualTaskDeleteTest() {
    EntityNotFoundException entityNotFoundException =
        new EntityNotFoundException("Service not found for serviceId : " + SERVICE_IDENTIFIER);
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .id(ID)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENV_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .build();
    ServerInstanceInfo serverInstanceInfo = K8sServerInstanceInfo.builder().build();
    InstanceSyncPerpetualTaskResponse instanceSyncPerpetualTaskResponse =
        K8sInstanceSyncPerpetualTaskResponse.builder().serverInstanceDetails(Arrays.asList(serverInstanceInfo)).build();
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO =
        InstanceSyncPerpetualTaskInfoDTO.builder().infrastructureMappingId(INFRASTRUCTURE_MAPPING_ID).build();
    List<InstanceDTO> instancesList =
        Arrays.asList(InstanceDTO.builder().serviceName(SERVICE_IDENTIFIER).envName(ENV_IDENTIFIER).build());

    when(instanceSyncPerpetualTaskInfoService.findByPerpetualTaskId(ACCOUNT_IDENTIFIER, PERPETUAL_TASK))
        .thenReturn(Optional.of(instanceSyncPerpetualTaskInfoDTO));
    when(infrastructureMappingService.getByInfrastructureMappingId(
             instanceSyncPerpetualTaskInfoDTO.getInfrastructureMappingId()))
        .thenReturn(Optional.of(infrastructureMappingDTO));
    when(instanceSyncHelper.fetchService(infrastructureMappingDTO)).thenThrow(entityNotFoundException);
    when(instanceService.getActiveInstancesByInfrastructureMappingId(
             eq(ACCOUNT_IDENTIFIER), eq(ORG_IDENTIFIER), eq(PROJECT_IDENTIFIER), eq(ID)))
        .thenReturn(instancesList);

    doNothing().when(instanceService).deleteAll(instancesList);

    instanceSyncService.processInstanceSyncByPerpetualTask(
        ACCOUNT_IDENTIFIER, PERPETUAL_TASK, instanceSyncPerpetualTaskResponse);
    verify(instanceSyncHelper, times(1)).cleanUpInstanceSyncPerpetualTaskInfo(any(), anyBoolean());
    verify(instanceService, times(1)).deleteAll(eq(instancesList));
  }
}
