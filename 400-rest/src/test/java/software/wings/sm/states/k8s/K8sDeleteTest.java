/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskType.DELETE;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.SAHIL;

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.K8S_DELETE;
import static software.wings.sm.states.k8s.K8sDelete.K8S_DELETE_COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.tasks.ResponseData;

import software.wings.api.k8s.K8sCanaryDeleteServiceElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.K8sDummyCommandUnit;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sDeleteTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.utils.ApplicationManifestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
public class K8sDeleteTest extends CategoryTest {
  private static final String RELEASE_NAME = "releaseName";
  private static final String FILE_PATHS = "abc/xyz";
  private static final String RESOURCES = "*";

  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private AppService appService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ActivityService activityService;
  @InjectMocks K8sDelete k8sDelete = spy(new K8sDelete(K8S_DELETE.name()));

  private StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().displayName(STATE_NAME).build();

  @Mock private ExecutionContextImpl context;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    k8sDelete.setFilePaths(FILE_PATHS);
    k8sDelete.setDeleteNamespacesForRelease(true);
    k8sDelete.setResources(RESOURCES);
    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);
    doReturn(Activity.builder().uuid("activity-id").build())
        .when(k8sDelete)
        .createK8sActivity(any(), eq(K8S_DELETE_COMMAND_NAME), anyString(), any(ActivityService.class), anyList());
    doReturn(new DirectKubernetesInfrastructureMapping())
        .when(k8sStateHelper)
        .fetchContainerInfrastructureMapping(context);

    doReturn("release-name")
        .when(k8sDelete)
        .fetchReleaseName(eq(context), eq(new DirectKubernetesInfrastructureMapping()));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeWithoutManifestDeleteNamespace() {
    when(featureFlagService.isEnabled(eq(FeatureName.NEW_KUBECTL_VERSION), any())).thenReturn(false);
    doReturn("Deployment/test").when(context).renderExpression("${workflow.variables.resources}");
    doReturn(ExecutionResponse.builder().build()).when(k8sDelete).queueK8sDelegateTask(any(), any(), any());
    doReturn(K8sCanaryDeleteServiceElement.builder().build()).when(k8sDelete).fetchK8sCanaryDeleteServiceElement(any());
    k8sDelete.setResources("${workflow.variables.resources}");
    k8sDelete.setFilePaths(null);
    k8sDelete.setDeleteNamespacesForRelease(true);
    k8sDelete.setStateTimeoutInMinutes(15);
    k8sDelete.execute(context);

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sDelete, never()).executeWrapperWithManifest(any(), any(), anyLong());
    verify(k8sDelete, times(1))
        .createK8sActivity(eq(context), eq(K8S_DELETE_COMMAND_NAME), eq("K8S_DELETE"), any(ActivityService.class),
            listArgumentCaptor.capture());

    @SuppressWarnings("unchecked") List<K8sDummyCommandUnit> commandUnits = listArgumentCaptor.getValue();
    assertThat(commandUnits.stream().map(K8sDummyCommandUnit::getName).collect(Collectors.toList()))
        .containsExactly(K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Delete);

    verify(k8sDelete, times(1))
        .queueK8sDelegateTask(eq(context),
            eq(K8sDeleteTaskParameters.builder()
                    .releaseName("release-name")
                    .resources("Deployment/test")
                    .filePaths(null)
                    .activityId("activity-id")
                    .commandName(K8S_DELETE_COMMAND_NAME)
                    .k8sTaskType(DELETE)
                    .deleteNamespacesForRelease(true)
                    .timeoutIntervalInMin(15)
                    .useNewKubectlVersion(false)
                    .build()),
            nullable(Map.class));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeWithoutManifestNotDeleteNamespace() {
    when(featureFlagService.isEnabled(eq(FeatureName.NEW_KUBECTL_VERSION), any())).thenReturn(false);
    doReturn("Deployment/test").when(context).renderExpression("${workflow.variables.resources}");
    doReturn(ExecutionResponse.builder().build()).when(k8sDelete).queueK8sDelegateTask(any(), any(), any());
    doReturn(K8sCanaryDeleteServiceElement.builder().build()).when(k8sDelete).fetchK8sCanaryDeleteServiceElement(any());
    k8sDelete.setResources("${workflow.variables.resources}");
    k8sDelete.setFilePaths(null);
    k8sDelete.setDeleteNamespacesForRelease(false);
    k8sDelete.setStateTimeoutInMinutes(5);
    k8sDelete.execute(context);

    ArgumentCaptor<List> listArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(k8sDelete, never()).executeWrapperWithManifest(any(), any(), anyLong());
    verify(k8sDelete, times(1))
        .createK8sActivity(eq(context), eq(K8S_DELETE_COMMAND_NAME), eq("K8S_DELETE"), any(ActivityService.class),
            listArgumentCaptor.capture());

    @SuppressWarnings("unchecked") List<K8sDummyCommandUnit> commandUnits = listArgumentCaptor.getValue();
    assertThat(commandUnits.stream().map(K8sDummyCommandUnit::getName).collect(Collectors.toList()))
        .containsExactly(K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Delete);

    verify(k8sDelete, times(1))
        .queueK8sDelegateTask(eq(context),
            eq(K8sDeleteTaskParameters.builder()
                    .releaseName("release-name")
                    .resources("Deployment/test")
                    .filePaths(null)
                    .activityId("activity-id")
                    .commandName(K8S_DELETE_COMMAND_NAME)
                    .k8sTaskType(DELETE)
                    .deleteNamespacesForRelease(false)
                    .timeoutIntervalInMin(5)
                    .useNewKubectlVersion(false)
                    .build()),
            nullable(Map.class));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void executeWithManifest() {
    doReturn(ExecutionResponse.builder().build())
        .when(k8sDelete)
        .executeWrapperWithManifest(any(K8sStateExecutor.class), any(ExecutionContext.class), anyLong());
    k8sDelete.setFilePaths("templates/foo.yaml");
    k8sDelete.execute(context);
    verify(k8sDelete, times(1)).executeWrapperWithManifest(k8sDelete, context, 10 * 60 * 1000L);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExecute() {
    k8sDelete.setFilePaths(FILE_PATHS);
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);

    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());

    doReturn(RELEASE_NAME).when(k8sDelete).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build()).when(k8sDelete).createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sDelete).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sDelete).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sDelete).fetchApplicationManifests(any());
    doReturn("abc/xyz").when(context).renderExpression("abc/xyz");
    doReturn("*").when(context).renderExpression("*");

    k8sDelete.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sDeleteTaskParamsArgumentCaptor =
        ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sDelete, times(1))
        .queueK8sDelegateTask(
            any(), k8sDeleteTaskParamsArgumentCaptor.capture(), any(applicationManifestMap.getClass()));
    K8sDeleteTaskParameters taskParams = (K8sDeleteTaskParameters) k8sDeleteTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(DELETE);
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_DELETE_COMMAND_NAME);
    assertThat(taskParams.getFilePaths()).isEqualTo(FILE_PATHS);
    assertThat(taskParams.isDeleteNamespacesForRelease()).isEqualTo(true);
    assertThat(taskParams.getResources()).isEqualTo(RESOURCES);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    doReturn(ExecutionResponse.builder().build())
        .when(k8sDelete)
        .handleAsyncResponseWrapper(any(K8sStateExecutor.class), any(ExecutionContext.class), anyMap());
    k8sDelete.handleAsyncResponse(context, new HashMap<>());
    verify(k8sDelete, times(1))
        .handleAsyncResponseWrapper(any(K8sStateExecutor.class), any(ExecutionContext.class), anyMap());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testValidateParameters() {
    k8sDelete.validateParameters(context);
    verify(k8sDelete, times(1)).validateParameters(any(ExecutionContext.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testValidateFields() {
    String RESOURCES_KEY = "resources";
    k8sDelete.setFilePaths(null);
    k8sDelete.setResources(null);
    Map<String, String> invalidFields = k8sDelete.validateFields();
    assertThat(invalidFields).isNotEmpty();
    assertThat(invalidFields.size()).isEqualTo(1);
    assertThat(invalidFields).containsKeys(RESOURCES_KEY);
    assertThat(invalidFields.get(RESOURCES_KEY)).isEqualTo("Resources must not be blank");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testCommandName() {
    String commandName = k8sDelete.commandName();
    assertThat(commandName).isEqualTo(K8S_DELETE_COMMAND_NAME);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testStateType() {
    String stateType = k8sDelete.stateType();
    assertThat(stateType).isEqualTo(K8S_DELETE.name());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testCommandUnitList() {
    List<CommandUnit> applyCommandUnits = k8sDelete.commandUnitList(true, "accountId");
    assertThat(applyCommandUnits).isNotEmpty();
    assertThat(applyCommandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(applyCommandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(applyCommandUnits.get(applyCommandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.Delete);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTask() {
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    when(context.getContextElement(any(ContextElementType.class))).thenReturn(workflowStandardParams);

    K8sTaskExecutionResponse k8sTaskExecutionResponse = K8sTaskExecutionResponse.builder().build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("k8sTaskExecutionResponse", k8sTaskExecutionResponse);

    when(context.getStateExecutionData()).thenReturn(new K8sStateExecutionData());

    k8sDelete.handleAsyncResponseForK8sTask(context, response);
    verify(activityService, times(1))
        .updateStatus(nullable(String.class), nullable(String.class), any(ExecutionStatus.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAbortEvent() {
    k8sDelete.handleAbortEvent(context);
    verify(k8sDelete, times(1)).handleAbortEvent(any(ExecutionContext.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testGetTimeoutMillis() {
    k8sDelete.setStateTimeoutInMinutes(1);
    Integer timeout = k8sDelete.getTimeoutMillis();
    assertThat(timeout).isEqualTo(60000);
  }
}
