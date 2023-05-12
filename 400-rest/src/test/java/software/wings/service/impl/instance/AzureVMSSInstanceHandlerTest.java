/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.api.PhaseStepExecutionData.PhaseStepExecutionDataBuilder.aPhaseStepExecutionData;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.azure.model.AzureVMData;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.response.AzureVMSSListVMDataResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.AzureVMSSInstanceInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.AzureVMSSDeploymentKey;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.azure.manager.AzureVMSSHelperServiceManager;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.states.azure.AzureVMSSDeployExecutionSummary;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureVMSSInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService mockInfraMappingService;
  @Mock private InstanceService mockInstanceService;
  @Mock private AppService mockAppService;
  @Mock private EnvironmentService mockEnvironmentService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private SettingsService mockSettingService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private AzureVMSSHelperServiceManager mockAzureVMSSHelperServiceManager;
  @Mock private SecretManager mockSecretManager;

  @InjectMocks @Inject AzureVMSSInstanceHandler azureVMSSInstanceHandler;

  @Before
  public void setup() {
    doReturn(anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build())
        .when(mockAppService)
        .get(anyString());
    doReturn(anEnvironment().environmentType(PROD).name(ENV_NAME).build())
        .when(mockEnvironmentService)
        .get(any(), any(), anyBoolean());
    doReturn(Service.builder().name(SERVICE_NAME).build())
        .when(mockServiceResourceService)
        .getWithDetails(any(), any());
    doReturn(getInfraMapping()).when(mockInfraMappingService).get(anyString(), anyString());
    doReturn(aSettingAttribute().withValue(AzureConfig.builder().build()).build())
        .when(mockSettingService)
        .get(anyString());
    doReturn(emptyList()).when(mockSecretManager).getEncryptionDetails(any(), anyString(), anyString());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleNewDeployment() {
    String vmssId = "vmss-id";
    String instance1Id = "id1";
    String instance2Id = "id2";
    List<DeploymentSummary> deploymentSummaries =
        singletonList(DeploymentSummary.builder()
                          .appId(WingsTestConstants.APP_ID)
                          .infraMappingId(INFRA_MAPPING_ID)
                          .azureVMSSDeploymentKey(AzureVMSSDeploymentKey.builder().vmssId(vmssId).build())
                          .build());

    List<Instance> currentInstancesInDb =
        asList(newInstance(instance1Id, vmssId, "vm1"), newInstance(instance2Id, vmssId, "vm2"));
    doReturn(currentInstancesInDb)
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn(asList(AzureVMData.builder().id("vm2").build(), AzureVMData.builder().id("vm3").build()))
        .when(mockAzureVMSSHelperServiceManager)
        .listVMSSVirtualMachines(any(), anyString(), anyString(), anyString(), anyList(), anyString());
    azureVMSSInstanceHandler.handleNewDeployment(deploymentSummaries, false, OnDemandRollbackInfo.builder().build());
    validateInstanceSync(instance1Id, "vm3");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    PhaseStepExecutionSummary stepExecutionSummary = new PhaseStepExecutionSummary();
    stepExecutionSummary.setStepExecutionSummaryList(singletonList(AzureVMSSDeployExecutionSummary.builder()
                                                                       .oldVirtualMachineScaleSetId("old-id")
                                                                       .oldVirtualMachineScaleSetName("old-name")
                                                                       .newVirtualMachineScaleSetId("new-id")
                                                                       .newVirtualMachineScaleSetName("new-name")
                                                                       .build()));
    PhaseStepExecutionData stepExecutionData =
        aPhaseStepExecutionData().withPhaseStepExecutionSummary(stepExecutionSummary).build();
    Optional<List<DeploymentInfo>> deploymentInfo =
        azureVMSSInstanceHandler.getDeploymentInfo(null, stepExecutionData, null, getInfraMapping(), "", null);
    assertThat(deploymentInfo.isPresent()).isTrue();
    assertThat(deploymentInfo.get().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testGetStatus() {
    String vmssId = "vmss-id";
    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        AzureVMSSTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .azureVMSSTaskResponse(AzureVMSSListVMDataResponse.builder().vmssId(vmssId).vmData(emptyList()).build())
            .build();
    Status status = azureVMSSInstanceHandler.getStatus(getInfraMapping(), azureVMSSTaskExecutionResponse);
    assertThat(status).isNotNull();
    assertThat(status.isSuccess()).isTrue();
    assertThat(status.isRetryable()).isFalse();
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncResponseFromPerpetualTask() {
    String vmssId = "vmss-id";
    String instance1Id = "id1";
    String instance2Id = "id2";
    List<Instance> currentInstancesInDb =
        asList(newInstance(instance1Id, vmssId, "vm1"), newInstance(instance2Id, vmssId, "vm2"));
    doReturn(currentInstancesInDb)
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    AzureVMSSTaskExecutionResponse azureVMSSTaskExecutionResponse =
        AzureVMSSTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .azureVMSSTaskResponse(
                AzureVMSSListVMDataResponse.builder()
                    .vmssId(vmssId)
                    .vmData(asList(AzureVMData.builder().id("vm2").build(), AzureVMData.builder().id("vm3").build()))
                    .build())
            .build();
    doReturn(true).when(mockFeatureFlagService).isEnabled(any(), anyString());
    azureVMSSInstanceHandler.processInstanceSyncResponseFromPerpetualTask(
        getInfraMapping(), azureVMSSTaskExecutionResponse);
    validateInstanceSync(instance1Id, "vm3");
  }

  private AzureVMSSInfrastructureMapping getInfraMapping() {
    AzureVMSSInfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();
    infrastructureMapping.setAppId(WingsTestConstants.APP_ID);
    infrastructureMapping.setAccountId(WingsTestConstants.ACCOUNT_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    infrastructureMapping.setSubscriptionId("subs-id");
    infrastructureMapping.setResourceGroupName("res-name");
    infrastructureMapping.setComputeProviderSettingId(SETTING_ID);
    return infrastructureMapping;
  }

  private void validateInstanceSync(String idToBeDeleted, String vmidToBeAdded) {
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(mockInstanceService).delete(captor.capture());
    Set idsTobeDeleted = captor.getValue();
    assertThat(idsTobeDeleted.size()).isEqualTo(1);
    assertThat(idsTobeDeleted.contains(idToBeDeleted)).isTrue();

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(mockInstanceService).save(captorInstance.capture());
    Instance instance = captorInstance.getValue();
    assertThat(instance).isNotNull();
    InstanceInfo instanceInfo = instance.getInstanceInfo();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo instanceof AzureVMSSInstanceInfo).isTrue();
    AzureVMSSInstanceInfo azureVMSSInstanceInfo = (AzureVMSSInstanceInfo) instanceInfo;
    assertThat(azureVMSSInstanceInfo.getAzureVMId()).isEqualTo(vmidToBeAdded);
  }

  private Instance newInstance(String instanceId, String vmssId, String vmId) {
    return Instance.builder()
        .uuid(instanceId)
        .instanceInfo(AzureVMSSInstanceInfo.builder().vmssId(vmssId).azureVMId(vmId).build())
        .lastWorkflowExecutionId("wf-ex-id")
        .build();
  }
}
