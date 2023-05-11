/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservice;

import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.JELENA;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.TaskType.AZURE_APP_SERVICE_TASK;
import static software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement.SWEEPING_OUTPUT_APP_SERVICE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.InstanceElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.sm.states.azure.AzureSweepingOutputServiceHelper;
import software.wings.sm.states.azure.AzureVMSSStateHelper;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupExecutionData;
import software.wings.sm.states.azure.appservices.AzureAppServiceStateData;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotRollback;
import software.wings.sm.states.azure.appservices.manifest.AzureAppServiceManifestUtils;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

public class AzureWebAppSlotRollbackTest extends WingsBaseTest {
  @Mock protected transient DelegateService delegateService;
  @Mock protected transient AzureVMSSStateHelper azureVMSSStateHelper;
  @Mock protected transient AzureSweepingOutputServiceHelper azureSweepingOutputServiceHelper;
  @Mock protected AzureAppServiceManifestUtils azureAppServiceManifestUtils;
  @Mock protected ActivityService activityService;
  @Mock protected StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Spy @InjectMocks private ServiceTemplateHelper serviceTemplateHelper;
  @Spy @InjectMocks AzureWebAppSlotRollback state = new AzureWebAppSlotRollback("Web app slot rollback state");

  private final String ACTIVITY_ID = "activityId";
  private final String INFRA_ID = "infraId";

  private final String SUBSCRIPTION_ID = "subscriptionId";
  private final String RESOURCE_GROUP = "resourceGroup";
  private final String APP_NAME = "testWebApp";
  private final String DEPLOYMENT_SLOT = "deploymentSlot";
  private final String DEPLOYMENT_SLOT_ID = "testWebApp/deploymentSlot";
  private final String APP_SERVICE_PLAN_ID = "appService-plan-id";
  private final String HOST_NAME = "hostname";

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotRollbackExecuteSuccess() {
    ExecutionContextImpl mockContext = initializeMockSetup(true, true);
    ExecutionResponse response = state.execute(mockContext);
    verifyStateExecutionData(response);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwapSlotAbsenceOfContextElement() {
    ExecutionContextImpl mockContext = initializeMockSetup(true, false);
    ExecutionResponse failedResponse = state.execute(mockContext);
    assertThat(failedResponse.getExecutionStatus()).isEqualTo(SKIPPED);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRollbackHandleAsyncResponseSuccess() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doNothing().when(azureVMSSStateHelper).updateActivityStatus(any(), any(), any());
    doReturn(SUCCESS).when(azureVMSSStateHelper).getAppServiceExecutionStatus(any());
    List<AzureAppDeploymentData> azureAppDeploymentData = getAzureAppDeploymentData();
    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping = getAzureWebAppInfraMapping();
    Map<String, ResponseData> responseMap = getResponseDataMap(azureAppDeploymentData);
    AzureAppServiceSlotSetupExecutionData stateExecutionData = getStateExecutionData();
    List<InstanceElement> instanceElements = Collections.singletonList(
        anInstanceElement().uuid("uuid").hostName(HOST_NAME).displayName(HOST_NAME).newInstance(true).build());

    doReturn(stateExecutionData).when(context).getStateExecutionData();
    doReturn(azureWebAppInfrastructureMapping)
        .when(azureVMSSStateHelper)
        .getAzureWebAppInfrastructureMapping(any(), any());

    doReturn(instanceElements)
        .when(azureSweepingOutputServiceHelper)
        .generateAzureAppInstanceElements(any(), any(), any());

    ExecutionResponse executionResponse = state.handleAsyncResponse(context, responseMap);
    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testRollbackAppServiceConfiguration() {
    String serviceId = "serviceId";
    String appId = "appId";
    String workflowId = "workflowId";
    String stateExecutionInstanceId = "stateExecutionInstanceId";

    Activity rollbackActivity = Activity.builder()
                                    .appId(appId)
                                    .workflowExecutionId(workflowId)
                                    .stateExecutionInstanceId(stateExecutionInstanceId)
                                    .build();

    ExecutionContextImpl mockContext = initializeMockSetup(true, true);
    ExecutionContextImpl rollbackContext = initializeMockSetup(true, true);
    doReturn(serviceId).when(azureVMSSStateHelper).getServiceId(mockContext);
    doReturn(false).when(azureVMSSStateHelper).isWebAppDockerDeployment(mockContext);
    doReturn(Optional.of(rollbackActivity))
        .when(azureVMSSStateHelper)
        .getWebAppPackageRollbackActivity(mockContext, serviceId);
    doReturn(rollbackContext)
        .when(azureVMSSStateHelper)
        .getExecutionContext(appId, workflowId, stateExecutionInstanceId);

    ExecutionResponse response = state.execute(mockContext);
    verifyStateExecutionData(response);
  }

  @NotNull
  private List<AzureAppDeploymentData> getAzureAppDeploymentData() {
    return Collections.singletonList(AzureAppDeploymentData.builder()
                                         .subscriptionId(SUBSCRIPTION_ID)
                                         .resourceGroup(RESOURCE_GROUP)
                                         .appName(APP_NAME)
                                         .deploySlot(DEPLOYMENT_SLOT)
                                         .deploySlotId(DEPLOYMENT_SLOT_ID)
                                         .appServicePlanId(APP_SERVICE_PLAN_ID)
                                         .hostName(HOST_NAME)
                                         .build());
  }

  @NotNull
  private ImmutableMap<String, ResponseData> getResponseDataMap(List<AzureAppDeploymentData> azureAppDeploymentData) {
    return ImmutableMap.of(ACTIVITY_ID,
        AzureTaskExecutionResponse.builder()
            .azureTaskResponse(AzureWebAppSlotSetupResponse.builder()
                                   .azureAppDeploymentData(azureAppDeploymentData)
                                   .preDeploymentData(AzureAppServicePreDeploymentData.builder()
                                                          .appName(APP_NAME)
                                                          .appSettingsToAdd(Collections.emptyMap())
                                                          .connStringsToAdd(Collections.emptyMap())
                                                          .slotName(DEPLOYMENT_SLOT)
                                                          .build())
                                   .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build());
  }

  private AzureAppServiceSlotSetupExecutionData getStateExecutionData() {
    return AzureAppServiceSlotSetupExecutionData.builder()
        .infrastructureMappingId(INFRA_ID)
        .deploySlotName(DEPLOYMENT_SLOT)
        .appServiceName(APP_NAME)
        .activityId(ACTIVITY_ID)
        .taskType(AZURE_APP_SERVICE_TASK)
        .build();
  }

  private AzureWebAppInfrastructureMapping getAzureWebAppInfraMapping() {
    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping = AzureWebAppInfrastructureMapping.builder()
                                                                            .resourceGroup(RESOURCE_GROUP)
                                                                            .subscriptionId(SUBSCRIPTION_ID)
                                                                            .build();
    azureWebAppInfrastructureMapping.setUuid(INFRA_ID);
    return azureWebAppInfrastructureMapping;
  }

  private ExecutionContextImpl initializeMockSetup(boolean isSuccess, boolean contextElement) {
    String appId = "appId";
    String serviceId = "serviceId";
    String envId = "envId";
    String delegateResult = "Done";

    String ACTIVITY_ID = "activityId";
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    Application app = Application.Builder.anApplication().uuid(appId).build();
    Environment env = Environment.Builder.anEnvironment().uuid(envId).build();
    Service service = Service.builder().uuid(serviceId).build();
    AzureAppServicePreDeploymentData preDeploymentData = AzureAppServicePreDeploymentData.builder().build();
    AzureAppServiceSlotSetupContextElement setupContextElement = AzureAppServiceSlotSetupContextElement.builder()
                                                                     .preDeploymentData(preDeploymentData)
                                                                     .deploymentSlot("dev-slot")
                                                                     .appServiceSlotSetupTimeOut(10)
                                                                     .build();

    AzureConfig azureConfig = AzureConfig.builder().build();
    Artifact artifact = Artifact.Builder.anArtifact().build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping =
        AzureWebAppInfrastructureMapping.builder().resourceGroup("resourceGroup").subscriptionId("subId").build();

    AzureAppServiceStateData appServiceStateData = AzureAppServiceStateData.builder()
                                                       .application(app)
                                                       .environment(env)
                                                       .service(service)
                                                       .infrastructureMapping(azureWebAppInfrastructureMapping)
                                                       .resourceGroup("rg")
                                                       .subscriptionId("subId")
                                                       .azureConfig(azureConfig)
                                                       .artifact(artifact)
                                                       .azureEncryptedDataDetails(encryptedDataDetails)
                                                       .build();

    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    ManagerExecutionLogCallback managerExecutionLogCallback = mock(ManagerExecutionLogCallback.class);

    if (contextElement) {
      doReturn(setupContextElement).when(mockContext).getContextElement(eq(ContextElementType.AZURE_WEBAPP_SETUP));
      doReturn(setupContextElement)
          .when(azureSweepingOutputServiceHelper)
          .getInfoFromSweepingOutput(eq(mockContext), eq(SWEEPING_OUTPUT_APP_SERVICE));
    }
    doReturn(activity).when(azureVMSSStateHelper).createAndSaveActivity(any(), any(), any(), any(), any(), any());
    doReturn(managerExecutionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(activity);
    doReturn(Optional.empty()).when(azureVMSSStateHelper).getUserDataSpecification(mockContext);
    doReturn(appServiceStateData).when(azureVMSSStateHelper).populateAzureAppServiceData(eq(mockContext), any());
    doReturn("service-template-id").when(serviceTemplateHelper).fetchServiceTemplateId(any());
    doReturn(delegateResult).when(delegateService).queueTaskV2(any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());

    when(mockContext.renderExpression(any())).thenAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      return (String) args[0];
    });
    if (!isSuccess) {
      doThrow(Exception.class).when(delegateService).queueTaskV2(any());
    }

    doReturn(Optional.of(artifact)).when(azureVMSSStateHelper).getWebAppPackageArtifactForRollback(any());

    String accountId = "accountId";
    ArtifactoryConfig artifactoryConfig = ArtifactoryConfig.builder()
                                              .encryptedPassword("secret")
                                              .artifactoryUrl("artifactory-url")
                                              .username("username")
                                              .accountId(accountId)
                                              .build();
    SettingAttribute serverSetting = SettingAttribute.Builder.aSettingAttribute().withValue(artifactoryConfig).build();

    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder().build();
    artifactStreamAttributes.setArtifactStreamType(ArtifactStreamType.ARTIFACTORY.name());
    artifactStreamAttributes.setServerSetting(serverSetting.toDTO());
    Map<String, String> metadata = new HashMap<>();
    metadata.put("buildNo", "artifact-builder-number");
    metadata.put("url", "artifact-job-name/random-guid/artifact-name");
    artifactStreamAttributes.setMetadata(metadata);
    artifactStreamAttributes.setJobName("artifact-job-name");

    ArtifactConnectorMapper artifactConnectorMapper =
        ArtifactConnectorMapper.getArtifactConnectorMapper(artifact, artifactStreamAttributes);
    doReturn(artifactConnectorMapper).when(azureVMSSStateHelper).getConnectorMapper(mockContext, artifact);

    doReturn(Collections.singletonList(EncryptedDataDetail.builder().build()))
        .when(azureVMSSStateHelper)
        .getEncryptedDataDetails(mockContext, artifactoryConfig);

    return mockContext;
  }

  private void verifyStateExecutionData(ExecutionResponse response) {
    assertThat(state.isRollback()).isTrue();
    assertThat(state.getSlotSteadyStateTimeout()).isNull();
    assertThat(state.getTargetSlot()).isNull();
    assertThat(state.getDeploymentSlot()).isNull();
    assertThat(state.getAppService()).isNull();
    assertThat(state.validateFields()).isEmpty();

    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response.getErrorMessage()).isNull();
    assertThat(response.getStateExecutionData()).isNotNull();
    assertThat(response.getStateExecutionData()).isInstanceOf(AzureAppServiceSlotSetupExecutionData.class);
  }
}
