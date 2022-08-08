/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesync;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.K8sInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.dtos.DeploymentSummaryDTO;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.dtos.deploymentinfo.K8sDeploymentInfoDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.helper.InstanceSyncHelper;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.models.DeploymentEvent;
import io.harness.models.RollbackInfo;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.rule.Owner;
import io.harness.service.deploymentsummary.DeploymentSummaryService;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesynchandlerfactory.InstanceSyncHandlerFactoryService;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

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
  @InjectMocks InstanceSyncServiceImpl instanceSyncService;

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

  enum OperationsOnInstances { ADD, DELETE, UPDATE }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void processInstanceSyncForNewDeploymentTest() {
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
}
