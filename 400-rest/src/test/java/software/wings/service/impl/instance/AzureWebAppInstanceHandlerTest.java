/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.beans.EnvironmentType.PROD;
import static io.harness.rule.OwnerRule.IVAN;

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

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppInstancesResponse;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.PhaseStepExecutionData;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.info.AzureWebAppInstanceInfo;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.key.deployment.AzureWebAppDeploymentKey;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.azure.manager.AzureAppServiceManager;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.PhaseStepExecutionSummary;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupExecutionSummary;
import software.wings.utils.WingsTestConstants;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AzureWebAppInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService mockInfraMappingService;
  @Mock private InstanceService mockInstanceService;
  @Mock private AppService mockAppService;
  @Mock private EnvironmentService mockEnvironmentService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private SettingsService mockSettingService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private AzureAppServiceManager mockAzureAppServiceManager;
  @Mock private SecretManager mockSecretManager;

  @InjectMocks @Inject AzureWebAppInstanceHandler azureWebAppInstanceHandler;

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
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testHandleNewDeployment() {
    List<DeploymentSummary> deploymentSummaries = singletonList(DeploymentSummary.builder()
                                                                    .appId(WingsTestConstants.APP_ID)
                                                                    .infraMappingId(INFRA_MAPPING_ID)
                                                                    .azureWebAppDeploymentKey(getWebAppDeploymentKey())
                                                                    .build());

    // in DB, already deployed in previous deployment -> webAppInstanceId1 & webAppInstanceId2
    List<Instance> currentInstancesInDb = getCurrentInstancesInDb();

    // latest deployed in new deployment-> webAppInstanceId2 & webAppInstanceId3
    List<AzureAppDeploymentData> latestDeployedInNewDeployment = getLatestDeployedInNewDeployment();

    doReturn(currentInstancesInDb)
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn(latestDeployedInNewDeployment)
        .when(mockAzureAppServiceManager)
        .listWebAppInstances(any(), anyList(), anyString(), anyString(), anyString(), any(), anyString(), anyString());

    azureWebAppInstanceHandler.handleNewDeployment(deploymentSummaries, false, OnDemandRollbackInfo.builder().build());

    // webAppInstanceId3 -> needs be added to DB
    // webAppInstanceId2 -> needs be deleted from DB
    String instance1Id = "id1";
    String webAppInstanceId3 = "webAppInstanceId3";
    validateInstanceSync(instance1Id, webAppInstanceId3);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetDeploymentInfo() {
    PhaseStepExecutionSummary stepExecutionSummary = new PhaseStepExecutionSummary();
    stepExecutionSummary.setStepExecutionSummaryList(singletonList(AzureAppServiceSlotSetupExecutionSummary.builder()
                                                                       .oldAppName("oldAppName")
                                                                       .oldSlotName("oldSlotName")
                                                                       .newAppName("newAppName")
                                                                       .newSlotName("newSlotName")
                                                                       .build()));
    PhaseStepExecutionData stepExecutionData =
        aPhaseStepExecutionData().withPhaseStepExecutionSummary(stepExecutionSummary).build();

    Optional<List<DeploymentInfo>> deploymentInfo =
        azureWebAppInstanceHandler.getDeploymentInfo(null, stepExecutionData, null, getInfraMapping(), "", null);

    assertThat(deploymentInfo.isPresent()).isTrue();
    assertThat(deploymentInfo.get().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetStatus() {
    AzureTaskExecutionResponse azureTaskExecutionResponse =
        AzureTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .azureTaskResponse(
                AzureWebAppListWebAppInstancesResponse.builder().deploymentData(Collections.emptyList()).build())
            .build();

    Status status = azureWebAppInstanceHandler.getStatus(getInfraMapping(), azureTaskExecutionResponse);

    assertThat(status).isNotNull();
    assertThat(status.isSuccess()).isTrue();
    assertThat(status.isRetryable()).isFalse();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testProcessInstanceSyncResponseFromPerpetualTask() {
    // in DB, already deployed in previous deployment -> webAppInstanceId1 & webAppInstanceId2
    List<Instance> currentInstancesInDb = getCurrentInstancesInDb();

    // latest deployed in new deployment-> webAppInstanceId2 & webAppInstanceId3
    List<AzureAppDeploymentData> latestDeployedInNewDeployment = getLatestDeployedInNewDeployment();

    doReturn(currentInstancesInDb)
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(anyString(), anyString());
    AzureTaskExecutionResponse azureTaskExecutionResponse =
        AzureTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .azureTaskResponse(
                AzureWebAppListWebAppInstancesResponse.builder().deploymentData(latestDeployedInNewDeployment).build())
            .build();
    doReturn(true).when(mockFeatureFlagService).isEnabled(any(), anyString());

    azureWebAppInstanceHandler.processInstanceSyncResponseFromPerpetualTask(
        getInfraMapping(), azureTaskExecutionResponse);

    // webAppInstanceId3 -> needs be added to DB
    // webAppInstanceId2 -> needs be deleted from DB
    String instance1Id = "id1";
    String webAppInstanceId3 = "webAppInstanceId3";
    validateInstanceSync(instance1Id, webAppInstanceId3);
  }

  private AzureWebAppDeploymentKey getWebAppDeploymentKey() {
    String appName = "appName";
    String slotName = "slotName";
    return AzureWebAppDeploymentKey.builder().appName(appName).slotName(slotName).build();
  }

  public List<Instance> getCurrentInstancesInDb() {
    String appName = "appName";
    String slotName = "slotName";

    String instance1Id = "id1";
    String webAppInstanceId1 = "webAppInstanceId1";

    String instance2Id = "id2";
    String webAppInstanceId2 = "webAppInstanceId2";

    return asList(newInstance(instance1Id, appName, slotName, webAppInstanceId1),
        newInstance(instance2Id, appName, slotName, webAppInstanceId2));
  }

  public List<AzureAppDeploymentData> getLatestDeployedInNewDeployment() {
    String appName = "appName";
    String slotName = "slotName";

    String webAppInstanceId2 = "webAppInstanceId2";
    String webAppInstanceId3 = "webAppInstanceId3";

    return asList(
        AzureAppDeploymentData.builder().appName(appName).deploySlot(slotName).instanceId(webAppInstanceId2).build(),
        AzureAppDeploymentData.builder().appName(appName).deploySlot(slotName).instanceId(webAppInstanceId3).build());
  }

  private AzureWebAppInfrastructureMapping getInfraMapping() {
    AzureWebAppInfrastructureMapping infrastructureMapping = AzureWebAppInfrastructureMapping.builder().build();
    infrastructureMapping.setAppId(WingsTestConstants.APP_ID);
    infrastructureMapping.setAccountId(WingsTestConstants.ACCOUNT_ID);
    infrastructureMapping.setUuid(INFRA_MAPPING_ID);
    infrastructureMapping.setSubscriptionId("subscriptionId");
    infrastructureMapping.setResourceGroup("resourceGroupName");
    infrastructureMapping.setComputeProviderSettingId(SETTING_ID);
    return infrastructureMapping;
  }

  private void validateInstanceSync(String idToBeDeleted, String idToBeAded) {
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
    assertThat(instanceInfo instanceof AzureWebAppInstanceInfo).isTrue();
    AzureWebAppInstanceInfo azureWebAppInstanceInfo = (AzureWebAppInstanceInfo) instanceInfo;
    assertThat(azureWebAppInstanceInfo.getInstanceId()).isEqualTo(idToBeAded);
  }

  private Instance newInstance(String instanceId, String appName, String slotName, String webAppInstanceId) {
    return Instance.builder()
        .uuid(instanceId)
        .instanceInfo(
            AzureWebAppInstanceInfo.builder().appName(appName).slotName(slotName).instanceId(webAppInstanceId).build())
        .lastWorkflowExecutionId("wf-ex-id")
        .build();
  }
}
