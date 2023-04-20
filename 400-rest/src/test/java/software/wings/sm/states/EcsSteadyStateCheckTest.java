/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.context.ContextElementType.INSTANCE;
import static io.harness.context.ContextElementType.STANDARD;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.EcsInfrastructureMapping.Builder.anEcsInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.sm.InstanceStatusSummary.InstanceStatusSummaryBuilder.anInstanceStatusSummary;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.APP_NAME;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.COMPUTE_PROVIDER_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.ENV_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.container.ContainerInfo;
import io.harness.context.ContextElementType;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ScriptStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.SettingAttribute;
import software.wings.beans.container.EcsSteadyStateCheckParams;
import software.wings.beans.container.EcsSteadyStateCheckResponse;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;

import com.amazonaws.regions.Regions;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class EcsSteadyStateCheckTest extends WingsBaseTest {
  @Mock private AppService mockAppService;
  @Mock private SecretManager mockSecretManager;
  @Mock private ActivityService mockActivityService;
  @Mock private SettingsService mockSettingsService;
  @Mock private DelegateService mockDelegateService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private ContainerDeploymentManagerHelper mockContainerDeploymentManagerHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @InjectMocks private EcsSteadyStateCheck check = new EcsSteadyStateCheck("stateName");

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testExecute() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    PhaseElement mockPhaseElement = mock(PhaseElement.class);
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    EmbeddedUser currentUser = EmbeddedUser.builder().name("test").email("test@harness.io").build();
    mockParams.setCurrentUser(currentUser);
    when(mockParams.getCurrentUser()).thenReturn(currentUser);
    doReturn(mockPhaseElement).when(mockContext).getContextElement(any(), any());
    doReturn(mockParams).when(mockContext).getContextElement(eq(STANDARD));
    doReturn(null).when(mockContext).getContextElement(eq(INSTANCE));
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(app).when(mockAppService).get(any());
    doReturn(app).when(mockContext).getApp();
    Environment env = anEnvironment().appId(APP_ID).uuid(ENV_ID).name(ENV_NAME).build();
    doReturn(env).when(mockContext).getEnv();
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(mockParams);
    ContainerInfrastructureMapping containerInfrastructureMapping =
        anEcsInfrastructureMapping()
            .withUuid(INFRA_MAPPING_ID)
            .withRegion(Regions.US_EAST_1.getName())
            .withClusterName(CLUSTER_NAME)
            .withComputeProviderSettingId(COMPUTE_PROVIDER_ID)
            .withDeploymentType("ECS")
            .build();
    doReturn(false).when(featureFlagService).isEnabled(TIMEOUT_FAILURE_SUPPORT, app.getAccountId());
    doReturn(containerInfrastructureMapping).when(mockInfrastructureMappingService).get(any(), any());
    Activity activity = Activity.builder().build();
    activity.setUuid(ACTIVITY_ID);
    doReturn(activity).when(mockActivityService).save(any());
    SettingAttribute awsConfig = aSettingAttribute().withValue(AwsConfig.builder().build()).build();
    doReturn(awsConfig).when(mockSettingsService).get(any());
    ExecutionResponse response = check.execute(mockContext);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getParameters()).isNotNull();
    assertThat(1).isEqualTo(delegateTask.getData().getParameters().length);
    assertThat(delegateTask.getData().getParameters()[0] instanceof EcsSteadyStateCheckParams).isTrue();
    EcsSteadyStateCheckParams params = (EcsSteadyStateCheckParams) delegateTask.getData().getParameters()[0];
    assertThat("ECS Steady State Check").isEqualTo(params.getCommandName());
    assertThat(APP_ID).isEqualTo(params.getAppId());
    assertThat(ACCOUNT_ID).isEqualTo(params.getAccountId());
    assertThat(ACTIVITY_ID).isEqualTo(params.getActivityId());
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInvalidInfrastructureMapping() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    PhaseElement mockPhaseElement = mock(PhaseElement.class);
    doReturn(mockPhaseElement).when(mockContext).getContextElement(any(), any());
    Application app = anApplication().uuid(APP_ID).name(APP_NAME).accountId(ACCOUNT_ID).build();
    doReturn(app).when(mockContext).getApp();
    doReturn(new AwsAmiInfrastructureMapping()).when(mockInfrastructureMappingService).get(any(), any());
    check.execute(mockContext);
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> check.execute(mockContext))
        .withMessageContaining("is not Ecs inframapping");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    WorkflowStandardParams mockParams = mock(WorkflowStandardParams.class);
    doReturn(mockParams).when(mockContext).getContextElement(eq(STANDARD));
    doReturn(APP_ID).when(mockParams).getAppId();
    ScriptStateExecutionData mockData = mock(ScriptStateExecutionData.class);
    doReturn(mockData).when(mockContext).getStateExecutionData();
    Map<String, ResponseData> delegateResponse = ImmutableMap.of(ACTIVITY_ID,
        EcsSteadyStateCheckResponse.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .containerInfoList(singletonList(ContainerInfo.builder().hostName("host").containerId("cid").build()))
            .build());
    doReturn(singletonList(anInstanceStatusSummary()
                               .withInstanceElement(anInstanceElement().hostName("host").displayName("disp").build())
                               .build()))
        .when(mockContainerDeploymentManagerHelper)
        .getInstanceStatusSummaries(any(), anyList());
    ExecutionResponse response = check.handleAsyncResponse(mockContext, delegateResponse);
    verify(mockActivityService).updateStatus(any(), any(), any());
    verify(mockData).setStatus(any());
  }

  @Test(expected = WingsException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseThrowWingsException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> delegateResponse = new HashMap<>();
    doThrow(new WingsException("test")).when(mockContext).getContextElement(ContextElementType.STANDARD);
    check.handleAsyncResponse(mockContext, delegateResponse);
    assertThatExceptionOfType(WingsException.class).isThrownBy(() -> check.execute(mockContext));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseThrowException() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    Map<String, ResponseData> delegateResponse = new HashMap<>();
    doThrow(new NullPointerException("test")).when(mockContext).getContextElement(ContextElementType.STANDARD);
    check.handleAsyncResponse(mockContext, delegateResponse);
    assertThatExceptionOfType(InvalidRequestException.class).isThrownBy(() -> check.execute(mockContext));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    check.setTimeoutMillis(10);
    assertThat(check.getTimeoutMillis()).isEqualTo(10);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAbortEvent() {
    EcsSteadyStateCheck ecsSteadyStateCheck = spy(check);
    ecsSteadyStateCheck.handleAbortEvent(mock(ExecutionContextImpl.class));
    verify(ecsSteadyStateCheck, times(1)).handleAbortEvent(any(ExecutionContext.class));
  }
}
