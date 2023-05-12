/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.api.DeploymentSummary;
import software.wings.beans.Application;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.AzureVMSSInstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.AzureVMSSDeploymentKey;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class AzureVMSSInstanceSyncPerpetualTaskCreatorTest extends CategoryTest {
  @Mock private InstanceService mockInstanceService;
  @Mock private PerpetualTaskService mockPerpetualTaskService;

  @InjectMocks AzureVMSSInstanceSyncPerpetualTaskCreator creator;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private AppService appService;
  @Mock private EnvironmentService environmentService;
  @Mock private ServiceResourceService serviceResourceService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    PcfInfrastructureMapping pcfInfrastructureMapping = PcfInfrastructureMapping.builder()
                                                            .uuid(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                                                            .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                                                            .name(INFRA_MAPPING_NAME)
                                                            .build();
    pcfInfrastructureMapping.setDisplayName("infraName");
    when(infrastructureMappingService.get(any(), any())).thenReturn(pcfInfrastructureMapping);
    when(environmentService.get(any(), any()))
        .thenReturn(Environment.Builder.anEnvironment()
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .name(ENV_NAME)
                        .build());
    when(serviceResourceService.get(any(), any()))
        .thenReturn(Service.builder()
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .name(SERVICE_NAME)
                        .build());
    when(appService.get(any()))
        .thenReturn(Application.Builder.anApplication()
                        .appId(InstanceSyncTestConstants.APP_ID)
                        .accountId(InstanceSyncTestConstants.ACCOUNT_ID)
                        .name(APP_NAME)
                        .build());
  }

  @Test
  @Owner(developers = OwnerRule.SATYAM)
  @Category(UnitTests.class)
  public void testCreatePerpetualTasks() {
    doReturn(singletonList(Instance.builder()
                               .instanceInfo(AzureVMSSInstanceInfo.builder()
                                                 .vmssId("vmss-id")
                                                 .azureVMId("azure-vm-id")
                                                 .instanceType("instance-type")
                                                 .host("host-ip")
                                                 .state("state")
                                                 .build())
                               .build()))
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn("perp-tesk-id")
        .when(mockPerpetualTaskService)
        .createTask(anyString(), anyString(), any(), any(), anyBoolean(), anyString());
    ArgumentCaptor<PerpetualTaskClientContext> clientContextCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    AzureVMSSInfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();
    infrastructureMapping.setAppId(APP_ID);
    infrastructureMapping.setAccountId(ACCOUNT_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    creator.createPerpetualTasks(infrastructureMapping);
    verify(mockPerpetualTaskService)
        .createTask(anyString(), anyString(), clientContextCaptor.capture(), any(), anyBoolean(), anyString());
    PerpetualTaskClientContext context = clientContextCaptor.getValue();
    assertThat(context).isNotNull();
    Map<String, String> clientParams = context.getClientParams();
    assertThat(clientParams).isNotNull();
    assertThat(clientParams.get("vmssId")).isEqualTo("vmss-id");
  }

  @Test
  @Owner(developers = OwnerRule.SATYAM)
  @Category(UnitTests.class)
  public void testCreatePerpetualTasksForNewDeployment() {
    List<DeploymentSummary> deploymentSummaries =
        singletonList(DeploymentSummary.builder()
                          .azureVMSSDeploymentKey(AzureVMSSDeploymentKey.builder().vmssId("vmss-id-new").build())
                          .build());
    List<PerpetualTaskRecord> existingPerpetualTasks = singletonList(
        PerpetualTaskRecord.builder()
            .clientContext(
                PerpetualTaskClientContext.builder().clientParams(ImmutableMap.of("vmssId", "vmss-id-old")).build())
            .build());
    AzureVMSSInfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();
    infrastructureMapping.setAppId(APP_ID);
    infrastructureMapping.setAccountId(ACCOUNT_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    ArgumentCaptor<PerpetualTaskClientContext> clientContextCaptor =
        ArgumentCaptor.forClass(PerpetualTaskClientContext.class);
    creator.createPerpetualTasksForNewDeployment(deploymentSummaries, existingPerpetualTasks, infrastructureMapping);
    verify(mockPerpetualTaskService)
        .createTask(anyString(), anyString(), clientContextCaptor.capture(), any(), anyBoolean(), anyString());
    PerpetualTaskClientContext context = clientContextCaptor.getValue();
    assertThat(context).isNotNull();
    Map<String, String> clientParams = context.getClientParams();
    assertThat(clientParams).isNotNull();
    assertThat(clientParams.get("vmssId")).isEqualTo("vmss-id-new");
  }
}
