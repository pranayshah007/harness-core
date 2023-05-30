/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helper;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.rule.Owner;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class InstanceSyncHelperTest extends InstancesTestBase {
  private final String ACCOUNT_ID = "acc";
  private final String ORG_IDENTIFIER = "org";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENVIRONMENT_IDENTIFIER = "env";
  private final String INFRASTRUCTURE_KEY = "infkey";
  private final String CONNECTOR_REF = "conn";
  private final String ID = "id";
  private final String PERPETUAL_TASK_ID = "perpe";
  @Mock InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  @Mock InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;
  @Mock ServiceEntityService serviceEntityService;
  @Mock EnvironmentService environmentService;
  @InjectMocks InstanceSyncHelper instanceSyncHelper;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void cleanUpInstanceSyncPerpetualTaskInfoTest() {
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO = InstanceSyncPerpetualTaskInfoDTO.builder()
                                                                            .accountIdentifier(ACCOUNT_ID)
                                                                            .id(ID)
                                                                            .perpetualTaskId(PERPETUAL_TASK_ID)
                                                                            .build();
    instanceSyncHelper.cleanUpInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfoDTO, false);
    verify(instanceSyncPerpetualTaskService, times(1))
        .deletePerpetualTask(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier(),
            instanceSyncPerpetualTaskInfoDTO.getPerpetualTaskId());
    verify(instanceSyncPerpetualTaskInfoService, times(1))
        .deleteById(instanceSyncPerpetualTaskInfoDTO.getAccountIdentifier(), instanceSyncPerpetualTaskInfoDTO.getId());
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchServiceTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_ID)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .build();
    ServiceEntity serviceEntity = ServiceEntity.builder().build();
    when(serviceEntityService.get(infrastructureMappingDTO.getAccountIdentifier(),
             infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
             infrastructureMappingDTO.getServiceIdentifier(), false))
        .thenReturn(Optional.of(serviceEntity));
    assertThat(instanceSyncHelper.fetchService(infrastructureMappingDTO)).isEqualTo(serviceEntity);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void fetchEnvironmentTest() {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(ACCOUNT_ID)
                                                            .serviceIdentifier(SERVICE_IDENTIFIER)
                                                            .orgIdentifier(ORG_IDENTIFIER)
                                                            .projectIdentifier(PROJECT_IDENTIFIER)
                                                            .envIdentifier(ENVIRONMENT_IDENTIFIER)
                                                            .infrastructureKey(INFRASTRUCTURE_KEY)
                                                            .connectorRef(CONNECTOR_REF)
                                                            .infrastructureKind(InfrastructureKind.KUBERNETES_DIRECT)
                                                            .build();
    Environment environment = Environment.builder().build();
    when(environmentService.get(infrastructureMappingDTO.getAccountIdentifier(),
             infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
             infrastructureMappingDTO.getEnvIdentifier(), false))
        .thenReturn(Optional.of(environment));
    assertThat(instanceSyncHelper.fetchEnvironment(infrastructureMappingDTO)).isEqualTo(environment);
  }
}
