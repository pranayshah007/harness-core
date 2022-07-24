/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.K8sDirectInfrastructureOutcome;
import io.harness.delegate.AccountId;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.deploymentinfo.DeploymentInfoDTO;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.perpetualtask.PerpetualTaskClientContextDetails;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskId;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.rule.Owner;
import io.harness.service.instancesynchandler.AbstractInstanceSyncHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.helm.NativeHelmInstanceSyncPerpetualTaskHandler;
import io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.k8s.K8SInstanceSyncPerpetualTaskHandler;

import com.google.protobuf.util.Durations;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceSyncPerpetualTaskServiceImplTest extends InstancesTestBase {
  private static final String ACCOUNT_IDENTIFIER = "account_identifier";
  private static final String ORG_IDENTIFIER = "org_identifier";
  private static final String PROJECT_IDENTIFIER = "project_identifier";
  private static final String SERVICE_IDENTIFIER = "service_identifier";
  private static final String ENVIRONMENT_IDENTIFIER = "environment_identifier";
  private static final String INFRASTRUCTURE_IDENTIFIER = "infrastructure_identifier";
  private static final String INFRASTRUCTURE_KIND = "infrastructure_kind";
  private static final String CONNECTOR_REF = "connector_ref";

  @Mock DelegateServiceGrpcClient delegateServiceGrpcClient;
  @Mock AbstractInstanceSyncHandler abstractInstanceSyncHandler;
  @Mock NativeHelmInstanceSyncPerpetualTaskHandler nativeHelmInstanceSyncPerpetualTaskHandler;
  @Mock K8SInstanceSyncPerpetualTaskHandler k8SInstanceSyncPerpetualTaskHandler;
  @InjectMocks InstanceSyncPerpetualTaskServiceRegister perpetualTaskServiceRegister;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void createPerpetualTaskTest() {
    InstanceSyncPerpetualTaskServiceImpl instanceSyncPerpetualTaskService =
        new InstanceSyncPerpetualTaskServiceImpl(delegateServiceGrpcClient, perpetualTaskServiceRegister);
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                                            .infrastructureKey(INFRASTRUCTURE_IDENTIFIER)
                                                            .infrastructureKind(INFRASTRUCTURE_KIND)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .build();
    List<DeploymentInfoDTO> deploymentInfoDTOList = Arrays.asList();
    InfrastructureOutcome infrastructureOutcome = K8sDirectInfrastructureOutcome.builder().build();
    PerpetualTaskSchedule perpetualTaskSchedule =
        PerpetualTaskSchedule.newBuilder()
            .setInterval(Durations.fromMinutes(InstanceSyncConstants.INTERVAL_MINUTES))
            .setTimeout(Durations.fromSeconds(InstanceSyncConstants.TIMEOUT_SECONDS))
            .build();
    when(abstractInstanceSyncHandler.getPerpetualTaskType()).thenReturn(PerpetualTaskType.K8S_INSTANCE_SYNC);
    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle = PerpetualTaskExecutionBundle.newBuilder().build();
    when(k8SInstanceSyncPerpetualTaskHandler.getExecutionBundle(
             infrastructureMappingDTO, deploymentInfoDTOList, infrastructureOutcome))
        .thenReturn(perpetualTaskExecutionBundle);
    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId("id").build();
    when(
        delegateServiceGrpcClient.createPerpetualTask(
            AccountId.newBuilder().setId(infrastructureMappingDTO.getAccountIdentifier()).build(),
            abstractInstanceSyncHandler.getPerpetualTaskType(), perpetualTaskSchedule,
            PerpetualTaskClientContextDetails.newBuilder().setExecutionBundle(perpetualTaskExecutionBundle).build(),
            true,
            String.format(
                "OrgIdentifier: [%s], ProjectIdentifier: [%s], ServiceIdentifier: [%s], EnvironmentIdentifier: [%s], InfrastructureKey: [%s]",
                infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
                infrastructureMappingDTO.getServiceIdentifier(), infrastructureMappingDTO.getEnvIdentifier(),
                infrastructureMappingDTO.getInfrastructureKey())))
        .thenReturn(perpetualTaskId);
    assertThat(instanceSyncPerpetualTaskService.createPerpetualTask(
                   infrastructureMappingDTO, abstractInstanceSyncHandler, deploymentInfoDTOList, infrastructureOutcome))
        .isEqualTo(perpetualTaskId.getId());
  }
}
