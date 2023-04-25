/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservice;

import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;

import static software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement.SWEEPING_OUTPUT_APP_SERVICE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.azure.model.AzureConstants;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotShiftTrafficResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
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
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotShiftTrafficExecutionData;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotShiftTrafficExecutionSummary;
import software.wings.sm.states.azure.appservices.AzureAppServiceStateData;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotShiftTraffic;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;

public class AzureWebAppSlotShiftTrafficTest extends WingsBaseTest {
  @Mock protected transient DelegateService delegateService;
  @Mock protected transient AzureVMSSStateHelper azureVMSSStateHelper;
  @Mock protected ActivityService activityService;
  @Mock AzureSweepingOutputServiceHelper azureSweepingOutputServiceHelper;
  @Mock protected StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Spy @InjectMocks private ServiceTemplateHelper serviceTemplateHelper;
  @Spy @InjectMocks AzureWebAppSlotShiftTraffic state = new AzureWebAppSlotShiftTraffic("Slot Traffic shift state");

  private final String ACTIVITY_ID = "activityId";
  private final String trafficWeight = "10";

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotTrafficShiftExecuteSuccess() {
    ExecutionContextImpl mockContext = initializeMockSetup(true, true);
    state.setTrafficWeightExpr(trafficWeight);
    ExecutionResponse result = state.execute(mockContext);
    assertSuccessExecution(result);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotTrafficShiftExecuteSkip() {
    ExecutionContextImpl mockContext = initializeMockSetup(true, true);
    state.setTrafficWeightExpr("junk");
    doReturn(AzureConstants.INVALID_TRAFFIC)
        .when(azureVMSSStateHelper)
        .renderDoubleExpression(eq("junk"), eq(mockContext), eq(AzureConstants.INVALID_TRAFFIC));

    ExecutionResponse result = state.execute(mockContext);

    assertThat(result).isNotNull();
    assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.SKIPPED);
    assertThat(result.getErrorMessage())
        .contains("Invalid traffic percent - [junk] specified. Skipping traffic shift step");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotTrafficShiftExecuteFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(false, true);
    assertThatThrownBy(() -> state.execute(mockContext)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotTrafficShiftAbsenceOfContextElement() {
    ExecutionContextImpl mockContext = initializeMockSetup(true, false);
    assertThatThrownBy(() -> state.execute(mockContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Did not find Setup element of class AzureAppServiceSlotSetupContextElement");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotTrafficShiftHandleAsyncResponse() {
    ExecutionContextImpl mockContext = initializeMockSetup(true, true);
    AzureTaskExecutionResponse delegateExecutionResponse = initializeDelegateResponse(true, mockContext);
    ExecutionResponse response =
        state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateExecutionResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotTrafficShiftAsyncResponseFailure() {
    ExecutionContextImpl mockContext = initializeMockSetup(true, true);
    AzureTaskExecutionResponse delegateExecutionResponse = initializeDelegateResponse(false, mockContext);
    ExecutionResponse response =
        state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, delegateExecutionResponse));
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(FAILED);
  }

  private ExecutionContextImpl initializeMockSetup(boolean isSuccess, boolean contextElement) {
    String appId = "appId";
    String serviceId = "serviceId";
    String envId = "envId";
    String delegateResult = "Done";

    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    Application app = Application.Builder.anApplication().uuid(appId).build();
    Environment env = Environment.Builder.anEnvironment().uuid(envId).build();
    Service service = Service.builder().uuid(serviceId).build();
    AzureAppServicePreDeploymentData preDeploymentData = AzureAppServicePreDeploymentData.builder().build();
    AzureAppServiceSlotSetupContextElement setupContextElement = AzureAppServiceSlotSetupContextElement.builder()
                                                                     .preDeploymentData(preDeploymentData)
                                                                     .appServiceSlotSetupTimeOut(10)
                                                                     .webApp("app-service")
                                                                     .deploymentSlot("stage")
                                                                     .build();

    AzureConfig azureConfig = AzureConfig.builder().build();
    Artifact artifact = Artifact.Builder.anArtifact().build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping =
        AzureWebAppInfrastructureMapping.builder().resourceGroup("resourceGroup").subscriptionId("subId").build();
    azureWebAppInfrastructureMapping.setUuid("infraMappingId");

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

    doReturn(activity).when(azureVMSSStateHelper).createAndSaveActivity(any(), any(), any(), any(), any(), any());
    doReturn(managerExecutionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(activity);

    if (contextElement) {
      doReturn(setupContextElement).when(mockContext).getContextElement(eq(ContextElementType.AZURE_WEBAPP_SETUP));
      doReturn(setupContextElement)
          .when(azureSweepingOutputServiceHelper)
          .getInfoFromSweepingOutput(eq(mockContext), eq(SWEEPING_OUTPUT_APP_SERVICE));
    }

    doReturn(appServiceStateData).when(azureVMSSStateHelper).populateAzureAppServiceData(eq(mockContext), any());
    doReturn(delegateResult).when(delegateService).queueTaskV2(any());
    doReturn(Integer.valueOf(trafficWeight))
        .when(azureVMSSStateHelper)
        .renderExpressionOrGetDefault(any(), eq(mockContext), anyInt());
    doReturn(Double.valueOf(trafficWeight))
        .when(azureVMSSStateHelper)
        .renderDoubleExpression(any(), eq(mockContext), anyDouble());
    doReturn(20)
        .when(azureVMSSStateHelper)
        .getStateTimeOutFromContext(eq(mockContext), eq(ContextElementType.AZURE_WEBAPP_SETUP));
    doReturn("service-template-id").when(serviceTemplateHelper).fetchServiceTemplateId(any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());

    when(mockContext.renderExpression(any())).thenAnswer((Answer<String>) invocation -> {
      Object[] args = invocation.getArguments();
      return (String) args[0];
    });
    if (!isSuccess) {
      doAnswer(invocation -> { throw new Exception(); }).when(delegateService).queueTaskV2(any());
    }
    state.setTrafficWeightExpr("20");
    return mockContext;
  }

  private AzureTaskExecutionResponse initializeDelegateResponse(boolean isSuccess, ExecutionContextImpl mockContext) {
    AzureWebAppSlotShiftTrafficResponse appSlotResizeResponse =
        AzureWebAppSlotShiftTrafficResponse.builder()
            .preDeploymentData(AzureAppServicePreDeploymentData.builder().build())
            .build();
    AzureTaskExecutionResponse taskExecutionResponse =
        AzureTaskExecutionResponse.builder()
            .delegateMetaInfo(DelegateMetaInfo.builder().build())
            .commandExecutionStatus(isSuccess ? CommandExecutionStatus.SUCCESS : CommandExecutionStatus.FAILURE)
            .azureTaskResponse(appSlotResizeResponse)
            .build();
    doReturn(isSuccess ? SUCCESS : FAILED)
        .when(azureVMSSStateHelper)
        .getAppServiceExecutionStatus(eq(taskExecutionResponse));
    doReturn(AzureAppServiceSlotShiftTrafficExecutionData.builder().build()).when(mockContext).getStateExecutionData();
    return taskExecutionResponse;
  }

  private void assertSuccessExecution(ExecutionResponse result) {
    assertThat(result).isNotNull();
    assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(result.getErrorMessage()).isNull();
    assertThat(result.getStateExecutionData()).isNotNull();
    assertThat(result.getStateExecutionData()).isInstanceOf(AzureAppServiceSlotShiftTrafficExecutionData.class);

    AzureAppServiceSlotShiftTrafficExecutionData stateExecutionData =
        (AzureAppServiceSlotShiftTrafficExecutionData) result.getStateExecutionData();

    assertThat(stateExecutionData.equals(new AzureAppServiceSlotShiftTrafficExecutionData())).isFalse();
    assertThat(stateExecutionData.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(stateExecutionData.getAppServiceSlotSetupTimeOut()).isNotNull();
    assertThat(stateExecutionData.getAppServiceName()).isEqualTo("app-service");
    assertThat(stateExecutionData.getDeploySlotName()).isEqualTo("stage");
    assertThat(Float.parseFloat(stateExecutionData.getTrafficWeight())).isEqualTo(Float.parseFloat(trafficWeight));
    assertThat(stateExecutionData.getInfrastructureMappingId()).isEqualTo("infraMappingId");

    AzureAppServiceSlotShiftTrafficExecutionSummary stepExecutionSummary = stateExecutionData.getStepExecutionSummary();
    assertThat(stepExecutionSummary.equals(AzureAppServiceSlotShiftTrafficExecutionSummary.builder().build()))
        .isFalse();
    assertThat(stateExecutionData.getStepExecutionSummary().toString()).isNotNull();

    assertThat(stateExecutionData.getExecutionDetails()).isNotEmpty();
    assertThat(stateExecutionData.getExecutionSummary()).isNotEmpty();
    assertThat(stateExecutionData.getStepExecutionSummary()).isNotNull();
  }
}
