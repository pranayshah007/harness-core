/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.exception.FailureType.TIMEOUT;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.RAGHVENDRA;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.TaskType.GIT_FETCH_FILES_TASK;
import static software.wings.sm.states.EcsRunTaskDeploy.ECS_RUN_TASK_COMMAND;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.DelegateTask;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ecs.EcsRunTaskStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFetchFilesTaskParams;
import software.wings.beans.GitFileConfig;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.ecs.request.EcsRunTaskDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsRunTaskDeployResponse;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EcsRunTaskDeployTest extends WingsBaseTest {
  private String runTaskFamilyName = "RunTaskFamilyName";
  private String taskDefJson = "{}";
  private String applicationAccountId = "APP_ACC_ID";
  private String runTaskClusterName = "RUN_TASK_CLUSTER";

  @Mock private EcsStateHelper mockEcsStateHelper;
  @Mock private AppService mockAppService;
  @Mock private SecretManager mockSecretManager;
  @Mock private DelegateService mockDelegateService;
  @Mock private ActivityService mockActivityService;
  @Mock private InfrastructureMappingService mockInfrastructureMappingService;
  @Mock private GitFileConfigHelperService mockGitFileConfigHelperService;
  @Mock private SettingsService mockSettingsService;
  @Mock private GitConfigHelperService mockGitConfigHelperService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private StateExecutionService stateExecutionService;

  @Inject private ServiceResourceService serviceResourceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ContainerDeploymentManagerHelper containerDeploymentHelper;

  @InjectMocks private EcsRunTaskDeploy state = new EcsRunTaskDeploy("stateName");

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteEcsRunTask() {
    state.setAddTaskDefinition("Inline");
    state.setGitFileConfig(null);
    state.setRunTaskFamilyName(runTaskFamilyName);
    state.setServiceSteadyStateTimeout(10L);
    state.setTaskDefinitionJson(taskDefJson);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(anyString())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
    EcsRunTaskDataBag bag = EcsRunTaskDataBag.builder()
                                .applicationUuid(APP_ID)
                                .applicationAppId(APP_ID)
                                .ecsRunTaskFamilyName(runTaskFamilyName)
                                .envUuid(ENV_ID)
                                .listTaskDefinitionJson(singletonList(taskDefJson))
                                .skipSteadyStateCheck(true)
                                .serviceSteadyStateTimeout(10L)
                                .applicationAccountId(applicationAccountId)
                                .awsConfig(AwsConfig.builder().build())
                                .build();

    doReturn(bag)
        .when(mockEcsStateHelper)
        .prepareBagForEcsRunTask(any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    doReturn(activity)
        .when(mockEcsStateHelper)
        .createActivity(any(), anyString(), anyString(), any(), anyString(), any());
    Application application = Application.Builder.anApplication().uuid(APP_ID).build();
    doReturn(application).when(mockEcsStateHelper).getApplicationFromExecutionContext(mockContext);

    EcsInfrastructureMapping ecsInfrastructureMapping = EcsInfrastructureMapping.builder()
                                                            .clusterName(runTaskClusterName)
                                                            .region("us-east-1")
                                                            .accountId(applicationAccountId)
                                                            .build();
    ecsInfrastructureMapping.setLaunchType("EC2");
    doReturn(ecsInfrastructureMapping)
        .when(mockEcsStateHelper)
        .getInfrastructureMappingFromInfraMappingService(any(), any(), any());
    doReturn(emptyList()).when(mockSecretManager).getEncryptionDetails(any(), any(), any());
    when(mockEcsStateHelper.createAndQueueDelegateTaskForEcsRunTaskDeploy(
             eq(bag), any(), any(), eq(application), eq(mockContext), any(), eq(ACTIVITY_ID), any(), eq(true)))
        .thenCallRealMethod();
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    doReturn(APP_ID).when(mockContext).getAppId();
    doReturn(application).when(mockAppService).get(APP_ID);
    doReturn("SUCCESS").when(mockDelegateService).queueTask(any());
    doReturn(anEnvironment().environmentType(EnvironmentType.PROD).build())
        .when(mockContext)
        .fetchRequiredEnvironment();
    ExecutionResponse response = state.execute(mockContext);

    ArgumentCaptor<EcsRunTaskDeployRequest> captor = ArgumentCaptor.forClass(EcsRunTaskDeployRequest.class);

    verify(mockEcsStateHelper)
        .createAndQueueDelegateTaskForEcsRunTaskDeploy(
            eq(bag), any(), any(), any(), any(), captor.capture(), any(), any(), eq(true));
    EcsRunTaskDeployRequest request = captor.getValue();
    assertThat(request).isNotNull();
    assertThat(request.getRunTaskFamilyName()).isEqualTo(runTaskFamilyName);
    assertThat(request.getListTaskDefinitionJson().get(0)).isEqualTo(taskDefJson);
    assertThat(request.getLaunchType()).isEqualTo("EC2");
    assertThat(request.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(request.getCluster()).isEqualTo(runTaskClusterName);
    assertThat(response.getErrorMessage()).isEqualTo(null);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(((EcsRunTaskStateExecutionData) response.getStateExecutionData()).getEcsRunTaskDataBag()).isEqualTo(bag);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseEcsRunTask() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    EcsCommandExecutionResponse ecsCommandExecutionResponse =
        EcsCommandExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    EcsRunTaskDeployResponse ecsRunTaskDeployResponse =
        EcsRunTaskDeployResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    EcsRunTaskStateExecutionData ecsRunTaskStateExecutionData = new EcsRunTaskStateExecutionData();
    ecsRunTaskStateExecutionData.setTaskType(TaskType.ECS_COMMAND_TASK);
    ecsRunTaskStateExecutionData.setActivityId(ACTIVITY_ID);
    ecsRunTaskStateExecutionData.setAppId(APP_ID);
    ecsCommandExecutionResponse.setEcsCommandResponse(ecsRunTaskDeployResponse);
    doReturn(ecsRunTaskStateExecutionData).when(mockContext).getStateExecutionData();
    doReturn(APP_ID).when(mockContext).getAppId();
    ExecutionResponse executionResponse =
        state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, ecsCommandExecutionResponse));
    verify(mockActivityService).updateStatus(eq(ACTIVITY_ID), eq(APP_ID), eq(ExecutionStatus.SUCCESS));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(ecsRunTaskStateExecutionData);
    assertThat(executionResponse.getFailureTypes()).isNull();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseEcsRunTask_Timeout() {
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    EcsCommandExecutionResponse ecsCommandExecutionResponse =
        EcsCommandExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    EcsRunTaskDeployResponse ecsRunTaskDeployResponse = EcsRunTaskDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .timeoutFailure(true)
                                                            .build();
    EcsRunTaskStateExecutionData ecsRunTaskStateExecutionData = new EcsRunTaskStateExecutionData();
    ecsRunTaskStateExecutionData.setTaskType(TaskType.ECS_COMMAND_TASK);
    ecsRunTaskStateExecutionData.setActivityId(ACTIVITY_ID);
    ecsRunTaskStateExecutionData.setAppId(APP_ID);
    ecsCommandExecutionResponse.setEcsCommandResponse(ecsRunTaskDeployResponse);
    doReturn(ecsRunTaskStateExecutionData).when(mockContext).getStateExecutionData();
    doReturn(APP_ID).when(mockContext).getAppId();
    ExecutionResponse executionResponse =
        state.handleAsyncResponse(mockContext, ImmutableMap.of(ACTIVITY_ID, ecsCommandExecutionResponse));
    verify(mockActivityService).updateStatus(eq(ACTIVITY_ID), eq(APP_ID), eq(ExecutionStatus.SUCCESS));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(ecsRunTaskStateExecutionData);
    assertThat(executionResponse.getFailureTypes()).isEqualTo(TIMEOUT);
  }

  @Test
  @Owner(developers = RAGHVENDRA)
  @Category(UnitTests.class)
  public void testExecuteGitTask() {
    state.setAddTaskDefinition("Remote");
    GitFileConfig gitFileConfig = GitFileConfig.builder().useBranch(true).filePathList(singletonList("FILE_1")).build();
    state.setGitFileConfig(gitFileConfig);
    state.setRunTaskFamilyName(runTaskFamilyName);
    state.setServiceSteadyStateTimeout(10L);
    state.setTaskDefinitionJson(null);
    ExecutionContextImpl mockContext = mock(ExecutionContextImpl.class);
    when(mockContext.renderExpression(anyString())).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[0];
      }
    });
    EcsRunTaskDataBag bag = EcsRunTaskDataBag.builder()
                                .applicationUuid(APP_ID)
                                .applicationAppId(APP_ID)
                                .ecsRunTaskFamilyName(runTaskFamilyName)
                                .envUuid(ENV_ID)
                                .listTaskDefinitionJson(null)
                                .skipSteadyStateCheck(true)
                                .serviceSteadyStateTimeout(10L)
                                .applicationAccountId(applicationAccountId)
                                .awsConfig(AwsConfig.builder().build())
                                .build();

    doReturn(bag)
        .when(mockEcsStateHelper)
        .prepareBagForEcsRunTask(any(), any(), anyBoolean(), any(), any(), any(), any(), any());
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    doReturn(activity)
        .when(mockEcsStateHelper)
        .createActivity(any(), anyString(), anyString(), any(), anyString(), any());
    Application application = Application.Builder.anApplication().uuid(APP_ID).build();
    doReturn(application).when(mockEcsStateHelper).getApplicationFromExecutionContext(mockContext);
    doReturn(application).when(mockContext).getApp();

    Environment environment = anEnvironment().build();
    doReturn(environment).when(mockContext).getEnv();
    ManagerExecutionLogCallback executionLogCallback = new ManagerExecutionLogCallback();
    doReturn(executionLogCallback)
        .when(mockEcsStateHelper)
        .getExecutionLogCallback(eq(mockContext), eq(ACTIVITY_ID), eq(ECS_RUN_TASK_COMMAND), any());
    EcsInfrastructureMapping ecsInfrastructureMapping = EcsInfrastructureMapping.builder()
                                                            .clusterName(runTaskClusterName)
                                                            .region("us-east-1")
                                                            .accountId(applicationAccountId)
                                                            .build();
    ecsInfrastructureMapping.setLaunchType("EC2");
    doReturn(ecsInfrastructureMapping)
        .when(mockEcsStateHelper)
        .getInfrastructureMappingFromInfraMappingService(any(), any(), any());
    doReturn(emptyList()).when(mockSecretManager).getEncryptionDetails(any(), any(), any());

    doReturn(ecsInfrastructureMapping).when(mockInfrastructureMappingService).get(eq(APP_ID), any());
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(SERVICE_ID).build()).build();
    doReturn(phaseElement).when(mockContext).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    doReturn(APP_ID).when(mockContext).getAppId();
    doReturn(application).when(mockAppService).get(APP_ID);
    when(mockGitFileConfigHelperService.renderGitFileConfig(any(), any())).thenCallRealMethod();
    GitConfig gitConfig = GitConfig.builder().build();
    doReturn(gitConfig).when(mockSettingsService).fetchGitConfigFromConnectorId(gitFileConfig.getConnectorId());
    doNothing().when(mockGitConfigHelperService).convertToRepoGitConfig(any(), any());
    doReturn("SUCCESS").when(mockDelegateService).queueTask(any());
    doReturn(true)
        .when(mockFeatureFlagService)
        .isEnabled(FeatureName.BIND_FETCH_FILES_TASK_TO_DELEGATE, application.getAccountId());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());

    ExecutionResponse response = state.execute(mockContext);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(mockDelegateService).queueTask(captor.capture());
    DelegateTask delegateTask = captor.getValue();

    assertThat(delegateTask).isNotNull();
    assertThat(delegateTask.getData().getTaskType()).isEqualTo(GIT_FETCH_FILES_TASK.name());
    assertThat(((GitFetchFilesTaskParams) delegateTask.getData().getParameters()[0]).getAppManifestKind())
        .isEqualTo(AppManifestKind.K8S_MANIFEST);
    assertThat(((GitFetchFilesTaskParams) delegateTask.getData().getParameters()[0]).getAppId())
        .isEqualTo(application.getUuid());
    assertThat(((GitFetchFilesTaskParams) delegateTask.getData().getParameters()[0])
                   .getGitFetchFilesConfigMap()
                   .values()
                   .iterator()
                   .next()
                   .getGitFileConfig())
        .isEqualTo(gitFileConfig);
    assertThat(response.getErrorMessage()).isEqualTo(null);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(((EcsRunTaskStateExecutionData) response.getStateExecutionData()).getEcsRunTaskDataBag()).isEqualTo(bag);
  }
}
