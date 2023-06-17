/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskType.APPLY;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.appmanifest.StoreType.Remote;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.K8S_APPLY;
import static software.wings.sm.states.k8s.K8sApplyState.K8S_APPLY_STATE;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
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
import io.harness.exception.InvalidRequestException;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.k8s.model.KubernetesResource;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sApplyResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.helpers.ext.openshift.OpenShiftManagerService;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.utils.ApplicationManifestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
public class K8sApplyStateTest extends CategoryTest {
  private static final String RELEASE_NAME = "releaseName";
  private static final String FILE_PATHS = "abc/xyz";

  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private OpenShiftManagerService openShiftManagerService;
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private AppService appService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private ActivityService activityService;
  @InjectMocks K8sApplyState k8sApplyState = spy(new K8sApplyState(K8S_APPLY.name()));

  private StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().displayName(STATE_NAME).build();

  private ExecutionContextImpl context;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    context = new ExecutionContextImpl(stateExecutionInstance);
    k8sApplyState.setStateTimeoutInMinutes("10");
    k8sApplyState.setSkipDryRun(true);
    k8sApplyState.setSkipRendering(true);
    k8sApplyState.setFilePaths(FILE_PATHS);

    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(
             nullable(String.class), anyMap(), any(VariableResolverTracker.class), nullable(String.class)))
        .thenAnswer(i -> i.getArguments()[0]);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() {
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);

    when(featureFlagService.isEnabled(eq(FeatureName.NEW_KUBECTL_VERSION), any())).thenReturn(false);
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(RELEASE_NAME).when(k8sApplyState).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sApplyState)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sApplyState).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sApplyState).queueK8sDelegateTask(any(), any(), any());
    doNothing().when(k8sApplyState).storePreviousHelmDeploymentInfo(any(), any());

    k8sApplyState.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sApplyTaskParamsArgumentCaptor =
        ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sApplyState, times(1)).queueK8sDelegateTask(any(), k8sApplyTaskParamsArgumentCaptor.capture(), any());
    K8sApplyTaskParameters taskParams = (K8sApplyTaskParameters) k8sApplyTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(APPLY);
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_APPLY_STATE);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.isSkipDryRun()).isTrue();
    assertThat(taskParams.isSkipRendering()).isTrue();
    assertThat(taskParams.getFilePaths()).isEqualTo(FILE_PATHS);
    assertThat(taskParams.isUseNewKubectlVersion()).isFalse();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritManifests() {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.add(KubernetesResource.builder().build());
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    k8sApplyState.setInheritManifests(true);
    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), nullable(String.class));
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(RELEASE_NAME).when(k8sApplyState).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sApplyState)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sApplyState).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sApplyState).queueK8sDelegateTask(any(), any(), any());
    doNothing().when(k8sApplyState).storePreviousHelmDeploymentInfo(any(), any());

    k8sApplyState.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sApplyTaskParamsArgumentCaptor =
        ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sApplyState, times(1)).queueK8sDelegateTask(any(), k8sApplyTaskParamsArgumentCaptor.capture(), any());
    verify(k8sStateHelper, times(1)).getResourcesFromSweepingOutput(any(), nullable(String.class));
    K8sApplyTaskParameters taskParams = (K8sApplyTaskParameters) k8sApplyTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(APPLY);
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_APPLY_STATE);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.isSkipDryRun()).isTrue();
    assertThat(taskParams.isSkipRendering()).isTrue();
    assertThat(taskParams.getFilePaths()).isEqualTo(FILE_PATHS);
    assertThat(taskParams.isInheritManifests()).isTrue();
    assertThat(taskParams.getKubernetesResources()).isEqualTo(kubernetesResources);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritManifestsNoResourcesFound() {
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    k8sApplyState.setInheritManifests(true);
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(RELEASE_NAME).when(k8sApplyState).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sApplyState)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sApplyState).fetchRenderedValuesFiles(any(), any());
    doNothing().when(k8sApplyState).storePreviousHelmDeploymentInfo(any(), any());

    assertThatThrownBy(() -> k8sApplyState.executeK8sTask(context, ACTIVITY_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No kubernetes resources found to inherit");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTimeoutValue() {
    K8sApplyState state = new K8sApplyState("k8s-apply");
    assertThat(state.getTimeoutMillis()).isNull();

    state.setStateTimeoutInMinutes("5");
    assertThat(state.getTimeoutMillis()).isEqualTo(300000);

    state.setStateTimeoutInMinutes("foo");
    assertThat(state.getTimeoutMillis()).isNull();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testCommandName() {
    String commandName = k8sApplyState.commandName();
    assertThat(commandName).isEqualTo(K8S_APPLY_STATE);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testStateType() {
    String stateType = k8sApplyState.stateType();
    assertThat(stateType).isEqualTo(K8S_APPLY.name());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testValidateFields() {
    k8sApplyState.setFilePaths(null);
    Map<String, String> invalidFields = k8sApplyState.validateFields();
    assertThat(invalidFields).isNotEmpty();
    assertThat(invalidFields.size()).isEqualTo(1);
    assertThat(invalidFields).containsKeys("File paths");
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExec() {
    doReturn(ExecutionResponse.builder().build())
        .when(k8sApplyState)
        .executeWrapperWithManifest(any(K8sStateExecutor.class), any(ExecutionContext.class), anyLong());
    k8sApplyState.execute(context);
    verify(k8sApplyState, times(1))
        .executeWrapperWithManifest(any(K8sStateExecutor.class), any(ExecutionContext.class), anyLong());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteWhenInheritManifests() {
    k8sApplyState.setInheritManifests(true);
    doReturn(Activity.builder().build()).when(k8sApplyState).createK8sActivity(any(), any(), any(), any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sApplyState).executeK8sTask(any(), any());
    k8sApplyState.execute(context);
    verify(k8sApplyState, times(1)).executeK8sTask(any(), any());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    doReturn(ExecutionResponse.builder().build())
        .when(k8sApplyState)
        .handleAsyncResponseWrapper(any(K8sStateExecutor.class), any(ExecutionContext.class), anyMap());
    k8sApplyState.handleAsyncResponse(context, new HashMap<>());
    verify(k8sApplyState, times(1))
        .handleAsyncResponseWrapper(any(K8sStateExecutor.class), any(ExecutionContext.class), anyMap());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTask() {
    K8sTaskExecutionResponse k8sTaskExecutionResponse = K8sTaskExecutionResponse.builder().build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("k8sTaskExecutionResponse", k8sTaskExecutionResponse);
    when(appService.get(nullable(String.class))).thenReturn(new Application());
    Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put(STATE_NAME, new K8sStateExecutionData());
    stateExecutionInstance.setStateExecutionMap(stateExecutionMap);
    k8sApplyState.handleAsyncResponseForK8sTask(context, response);

    verify(appService, times(1)).get(nullable(String.class));
    verify(activityService, times(1))
        .updateStatus(nullable(String.class), nullable(String.class), any(ExecutionStatus.class));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskResourcesReturned() {
    List<KubernetesResource> resources = new ArrayList<>();
    K8sTaskExecutionResponse k8sTaskExecutionResponse =
        K8sTaskExecutionResponse.builder()
            .k8sTaskResponse(K8sApplyResponse.builder().resources(resources).build())
            .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("k8sTaskExecutionResponse", k8sTaskExecutionResponse);
    when(appService.get(nullable(String.class))).thenReturn(new Application());
    Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put(STATE_NAME, new K8sStateExecutionData());
    stateExecutionInstance.setStateExecutionMap(stateExecutionMap);

    k8sApplyState.handleAsyncResponseForK8sTask(context, response);

    verify(appService, times(1)).get(nullable(String.class));
    verify(activityService, times(1))
        .updateStatus(nullable(String.class), nullable(String.class), any(ExecutionStatus.class));
    verify(k8sStateHelper, times(1)).saveResourcesToSweepingOutput(context, resources, K8S_APPLY.name());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCommandUnitListFeatureEnabled() {
    k8sApplyState.setInheritManifests(false);
    k8sApplyState.setExportManifests(false);
    List<CommandUnit> applyCommandUnits = k8sApplyState.commandUnitList(true, "accountId");
    assertThat(applyCommandUnits).isNotEmpty();
    assertThat(applyCommandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(applyCommandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(applyCommandUnits.get(4).getName()).isEqualTo(K8sCommandUnitConstants.WaitForSteadyState);
    assertThat(applyCommandUnits.get(applyCommandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);

    k8sApplyState.setExportManifests(false);
    k8sApplyState.setInheritManifests(true);
    applyCommandUnits = k8sApplyState.commandUnitList(true, "accountId");
    assertThat(applyCommandUnits).isNotEmpty();
    assertThat(applyCommandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(applyCommandUnits.get(3).getName()).isEqualTo(K8sCommandUnitConstants.WaitForSteadyState);
    assertThat(applyCommandUnits.get(applyCommandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);

    k8sApplyState.setInheritManifests(false);
    k8sApplyState.setExportManifests(true);
    applyCommandUnits = k8sApplyState.commandUnitList(true, "accountId");
    assertThat(applyCommandUnits.size()).isEqualTo(2);
    assertThat(applyCommandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(applyCommandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Init);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCommandUnitListFeatureDisabled() {
    k8sApplyState.setInheritManifests(false);
    k8sApplyState.setExportManifests(false);
    List<CommandUnit> applyCommandUnits = k8sApplyState.commandUnitList(true, "accountId");
    assertThat(applyCommandUnits).isNotEmpty();
    assertThat(applyCommandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(applyCommandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(applyCommandUnits.get(applyCommandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);

    k8sApplyState.setExportManifests(false);
    k8sApplyState.setInheritManifests(true);
    applyCommandUnits = k8sApplyState.commandUnitList(true, "accountId");
    assertThat(applyCommandUnits).isNotEmpty();
    assertThat(applyCommandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.Init);
    assertThat(applyCommandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Prepare);
    assertThat(applyCommandUnits.get(applyCommandUnits.size() - 1).getName()).isEqualTo(K8sCommandUnitConstants.WrapUp);

    k8sApplyState.setInheritManifests(false);
    k8sApplyState.setExportManifests(true);
    applyCommandUnits = k8sApplyState.commandUnitList(true, "accountId");
    assertThat(applyCommandUnits).isNotEmpty();
    assertThat(applyCommandUnits.get(0).getName()).isEqualTo(K8sCommandUnitConstants.FetchFiles);
    assertThat(applyCommandUnits.get(1).getName()).isEqualTo(K8sCommandUnitConstants.Init);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testValidateParameters() {
    k8sApplyState.validateParameters(context);
    verify(k8sApplyState, times(1)).validateParameters(any(ExecutionContext.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAbortEvent() {
    k8sApplyState.handleAbortEvent(context);
    verify(k8sApplyState, times(1)).handleAbortEvent(any(ExecutionContext.class));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testFetchRenderedValuesFilesAndAddInlineStepOverride() {
    k8sApplyState.setInlineStepOverride("canaryWeight: 25");

    Map<K8sValuesLocation, ApplicationManifest> appManifestMap = new HashMap<>();
    appManifestMap.put(K8sValuesLocation.Service,
        ApplicationManifest.builder()
            .gitFileConfig(GitFileConfig.builder().build())
            .kind(AppManifestKind.VALUES)
            .storeType(Remote)
            .build());

    doReturn(new ArrayList<>()).when(k8sApplyState).fetchRenderedValuesFiles(any(), any());

    assertThat(k8sApplyState.fetchRenderedValuesFilesAndAddInlineStepOverride(appManifestMap, context))
        .contains("canaryWeight: 25");
  }
}
