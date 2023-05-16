/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskType.BLUE_GREEN_DEPLOY;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.K8S_BLUE_GREEN_DEPLOY;
import static software.wings.sm.states.k8s.K8sBlueGreenDeploy.K8S_BLUE_GREEN_DEPLOY_COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.KubernetesResource;
import io.harness.rule.Owner;

import software.wings.api.InstanceElementListParam;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.k8s.request.K8sBlueGreenDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sBlueGreenDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.utils.ApplicationManifestUtils;

import com.google.common.collect.ImmutableMap;
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
public class K8sBlueGreenDeployTest extends CategoryTest {
  private static final String RELEASE_NAME = "releaseName";

  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private ActivityService activityService;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks
  private K8sBlueGreenDeploy k8sBlueGreenDeploy = spy(new K8sBlueGreenDeploy(K8S_BLUE_GREEN_DEPLOY.name()));

  private StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().displayName(STATE_NAME).build();

  private ExecutionContextImpl context;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    context = new ExecutionContextImpl(stateExecutionInstance);
    k8sBlueGreenDeploy.setStateTimeoutInMinutes(10);
    k8sBlueGreenDeploy.setSkipDryRun(true);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() {
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);

    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(RELEASE_NAME).when(k8sBlueGreenDeploy).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sBlueGreenDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sBlueGreenDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sBlueGreenDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sBlueGreenDeploy).fetchApplicationManifests(any());

    k8sBlueGreenDeploy.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sApplyTaskParamsArgumentCaptor =
        ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sBlueGreenDeploy, times(1))
        .queueK8sDelegateTask(
            any(), k8sApplyTaskParamsArgumentCaptor.capture(), any(applicationManifestMap.getClass()));
    K8sBlueGreenDeployTaskParameters taskParams =
        (K8sBlueGreenDeployTaskParameters) k8sApplyTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(BLUE_GREEN_DEPLOY);
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_BLUE_GREEN_DEPLOY_COMMAND_NAME);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.isSkipDryRun()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritManifests() {
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    k8sBlueGreenDeploy.setInheritManifests(true);
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.add(KubernetesResource.builder().build());

    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(RELEASE_NAME).when(k8sBlueGreenDeploy).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sBlueGreenDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sBlueGreenDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sBlueGreenDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sBlueGreenDeploy).fetchApplicationManifests(any());

    k8sBlueGreenDeploy.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sApplyTaskParamsArgumentCaptor =
        ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sBlueGreenDeploy, times(1))
        .queueK8sDelegateTask(
            any(), k8sApplyTaskParamsArgumentCaptor.capture(), any(applicationManifestMap.getClass()));
    K8sBlueGreenDeployTaskParameters taskParams =
        (K8sBlueGreenDeployTaskParameters) k8sApplyTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(BLUE_GREEN_DEPLOY);
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_BLUE_GREEN_DEPLOY_COMMAND_NAME);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.isSkipDryRun()).isTrue();
    assertThat(taskParams.getKubernetesResources()).isEqualTo(kubernetesResources);
    assertThat(taskParams.isInheritManifests()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritManifestsNoResourcesFound() {
    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
    k8sBlueGreenDeploy.setInheritManifests(true);
    List<KubernetesResource> kubernetesResources = new ArrayList<>();

    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(RELEASE_NAME).when(k8sBlueGreenDeploy).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sBlueGreenDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sBlueGreenDeploy).fetchRenderedValuesFiles(any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sBlueGreenDeploy).fetchApplicationManifests(any());

    assertThatThrownBy(() -> k8sBlueGreenDeploy.executeK8sTask(context, ACTIVITY_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No kubernetes resources found to inherit");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteWhenInheritManifests() {
    k8sBlueGreenDeploy.setInheritManifests(true);
    doReturn(Activity.builder().build()).when(k8sBlueGreenDeploy).createK8sActivity(any(), any(), any(), any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sBlueGreenDeploy).executeK8sTask(any(), any());
    k8sBlueGreenDeploy.execute(context);
    verify(k8sBlueGreenDeploy, times(1)).executeK8sTask(eq(context), any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTimeoutValue() {
    K8sBlueGreenDeploy state = new K8sBlueGreenDeploy("k8s-bg");
    assertThat(state.getTimeoutMillis()).isNull();

    state.setStateTimeoutInMinutes(5);
    assertThat(state.getTimeoutMillis()).isEqualTo(300000);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHelmChartInfoValue() {
    stateExecutionInstance.setStateExecutionMap(ImmutableMap.of(STATE_NAME, new K8sStateExecutionData()));
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();
    K8sTaskExecutionResponse taskExecutionResponse =
        K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .k8sTaskResponse(K8sBlueGreenDeployResponse.builder().helmChartInfo(helmChartInfo).build())
            .build();

    doReturn(InstanceElementListParam.builder().build())
        .when(k8sBlueGreenDeploy)
        .fetchInstanceElementListParam(anyList());
    doReturn(emptyList()).when(k8sBlueGreenDeploy).fetchInstanceStatusSummaries(any(), any());
    doNothing().when(k8sBlueGreenDeploy).saveK8sElement(any(), any());
    doNothing().when(k8sBlueGreenDeploy).saveInstanceInfoToSweepingOutput(any(), any(), any());
    doReturn(APP_ID).when(k8sBlueGreenDeploy).fetchAppId(context);
    ExecutionResponse executionResponse =
        k8sBlueGreenDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of("response", taskExecutionResponse));
    K8sStateExecutionData executionData = (K8sStateExecutionData) executionResponse.getStateExecutionData();

    assertThat(executionData.getHelmChartInfo()).isEqualTo(helmChartInfo);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCommandUnitList() {
    List<CommandUnit> commandUnits = new ArrayList<>();
    doReturn(commandUnits)
        .when(k8sStateHelper)
        .getCommandUnits(anyBoolean(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    List<CommandUnit> result = k8sBlueGreenDeploy.commandUnitList(true, "accountId");
    assertThat(result).isEqualTo(commandUnits);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testCommandName() {
    String commandName = k8sBlueGreenDeploy.commandName();
    assertThat(commandName).isEqualTo(K8S_BLUE_GREEN_DEPLOY_COMMAND_NAME);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testStateType() {
    String stateType = k8sBlueGreenDeploy.stateType();
    assertThat(stateType).isEqualTo(K8S_BLUE_GREEN_DEPLOY.name());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDelegateExecuteToK8sStateHelper() {
    doReturn(ExecutionResponse.builder().build())
        .when(k8sBlueGreenDeploy)
        .executeWrapperWithManifest(any(K8sStateExecutor.class), any(ExecutionContext.class), anyLong());

    k8sBlueGreenDeploy.execute(context);

    verify(k8sBlueGreenDeploy, times(1))
        .executeWrapperWithManifest(k8sBlueGreenDeploy, context,
            K8sStateHelper.fetchSafeTimeoutInMillis(k8sBlueGreenDeploy.getTimeoutMillis()));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskFailed() {
    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder().build();
    K8sTaskExecutionResponse taskExecutionResponse =
        K8sTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).build();

    stateExecutionInstance.setStateExecutionMap(
        ImmutableMap.of(stateExecutionInstance.getDisplayName(), stateExecutionData));
    doReturn(ACTIVITY_ID).when(k8sBlueGreenDeploy).fetchActivityId(context);
    doReturn(APP_ID).when(k8sBlueGreenDeploy).fetchAppId(context);

    ExecutionResponse executionResponse =
        k8sBlueGreenDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of(ACTIVITY_ID, taskExecutionResponse));

    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(stateExecutionData);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskK8sResourcesReturned() {
    List<KubernetesResource> resources = new ArrayList<>();
    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder().build();
    K8sTaskExecutionResponse taskExecutionResponse =
        K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .k8sTaskResponse(K8sBlueGreenDeployResponse.builder().resources(resources).build())
            .build();

    stateExecutionInstance.setStateExecutionMap(
        ImmutableMap.of(stateExecutionInstance.getDisplayName(), stateExecutionData));
    doReturn(ACTIVITY_ID).when(k8sBlueGreenDeploy).fetchActivityId(context);
    doReturn(APP_ID).when(k8sBlueGreenDeploy).fetchAppId(context);

    ExecutionResponse executionResponse =
        k8sBlueGreenDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of(ACTIVITY_ID, taskExecutionResponse));

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(k8sStateHelper, times(1)).saveResourcesToSweepingOutput(context, resources, K8S_BLUE_GREEN_DEPLOY.name());
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
  }
}
