/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskType.DEPLOYMENT_ROLLING;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.K8S_DEPLOYMENT_ROLLING;
import static software.wings.sm.states.k8s.K8sRollingDeploy.K8S_ROLLING_DEPLOY_COMMAND_NAME;
import static software.wings.sm.states.k8s.K8sStateHelper.fetchSafeTimeoutInMillis;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
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
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElementListParam;
import software.wings.api.k8s.K8sApplicationManifestSourceInfo;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Service;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sRollingDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
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
public class K8sRollingDeployTest extends CategoryTest {
  private static final String RELEASE_NAME = "releaseName";

  @Mock private FeatureFlagService featureFlagService;
  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private AppService appService;
  @Mock private SweepingOutputService mockedSweepingOutputService;
  @Mock private ActivityService activityService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @InjectMocks K8sRollingDeploy k8sRollingDeploy = spy(new K8sRollingDeploy(K8S_DEPLOYMENT_ROLLING.name()));

  private StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().displayName(STATE_NAME).build();

  private ExecutionContextImpl context;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    context = new ExecutionContextImpl(stateExecutionInstance);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    k8sRollingDeploy.setStateTimeoutInMinutes(10);
    k8sRollingDeploy.setSkipDryRun(true);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() {
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());

    doReturn(RELEASE_NAME).when(k8sRollingDeploy).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sRollingDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sRollingDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sRollingDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sRollingDeploy).fetchApplicationManifests(any());

    k8sRollingDeploy.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sTaskParamsArgumentCaptor = ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sRollingDeploy, times(1))
        .queueK8sDelegateTask(any(), k8sTaskParamsArgumentCaptor.capture(), any(applicationManifestMap.getClass()));
    K8sRollingDeployTaskParameters taskParams = (K8sRollingDeployTaskParameters) k8sTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(DEPLOYMENT_ROLLING);
    assertThat(taskParams.isInCanaryWorkflow()).isFalse();
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_ROLLING_DEPLOY_COMMAND_NAME);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.isSkipDryRun()).isTrue();
    assertThat(taskParams.getKubernetesResources()).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteK8sTaskWhenInheritManifestsAndNoResourcesFound() {
    k8sRollingDeploy.setSkipDryRun(false);
    k8sRollingDeploy.setInheritManifests(true);
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());

    doReturn(null).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sRollingDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sRollingDeploy).fetchRenderedValuesFiles(any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sRollingDeploy).fetchApplicationManifests(any());

    assertThatThrownBy(() -> k8sRollingDeploy.executeK8sTask(context, ACTIVITY_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No kubernetes resources found to inherit");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteK8sTaskWhenInheritManifests() {
    k8sRollingDeploy.setSkipDryRun(false);
    k8sRollingDeploy.setInheritManifests(true);
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.add(KubernetesResource.builder().build());
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());

    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(RELEASE_NAME).when(k8sRollingDeploy).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sRollingDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sRollingDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sRollingDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sRollingDeploy).fetchApplicationManifests(any());

    k8sRollingDeploy.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sTaskParamsArgumentCaptor = ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sRollingDeploy, times(1))
        .queueK8sDelegateTask(any(), k8sTaskParamsArgumentCaptor.capture(), any(applicationManifestMap.getClass()));
    K8sRollingDeployTaskParameters taskParams = (K8sRollingDeployTaskParameters) k8sTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(DEPLOYMENT_ROLLING);
    assertThat(taskParams.isInCanaryWorkflow()).isFalse();
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_ROLLING_DEPLOY_COMMAND_NAME);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.isSkipDryRun()).isFalse();
    assertThat(taskParams.isInheritManifests()).isTrue();
    assertThat(taskParams.getKubernetesResources()).isEqualTo(kubernetesResources);
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testExecuteK8sTask_FF_InheritManifest() {
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(RELEASE_NAME).when(k8sRollingDeploy).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sRollingDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sRollingDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sRollingDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sRollingDeploy).fetchApplicationManifests(any());
    doReturn(true).when(featureFlagService).isEnabled(any(), any());
    doReturn(Service.builder().uuid("serviceId").build()).when(applicationManifestUtils).fetchServiceFromContext(any());

    k8sRollingDeploy.executeK8sTask(context, ACTIVITY_ID);
    ArgumentCaptor<SweepingOutputInquiry> sweepingOutputInquiryCaptor =
        ArgumentCaptor.forClass(SweepingOutputInquiry.class);
    verify(mockedSweepingOutputService, times(1)).findSweepingOutput(sweepingOutputInquiryCaptor.capture());
    assertThat(sweepingOutputInquiryCaptor.getValue().getName())
        .isEqualTo(K8sApplicationManifestSourceInfo.SWEEPING_OUTPUT_NAME_PREFIX + "-serviceId");

    reset(featureFlagService);
    doReturn(false).when(featureFlagService).isEnabled(any(), any());
    reset(mockedSweepingOutputService);
    k8sRollingDeploy.executeK8sTask(context, ACTIVITY_ID);
    verify(mockedSweepingOutputService, times(0)).findSweepingOutput(any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDelegateExecuteToK8sStateHelper() {
    k8sRollingDeploy.setInheritManifests(false);
    doReturn(ExecutionResponse.builder().build())
        .when(k8sRollingDeploy)
        .executeWrapperWithManifest(any(K8sStateExecutor.class), any(ExecutionContext.class), anyLong());
    k8sRollingDeploy.execute(context);
    verify(k8sRollingDeploy, times(1))
        .executeWrapperWithManifest(
            k8sRollingDeploy, context, fetchSafeTimeoutInMillis(k8sRollingDeploy.getTimeoutMillis()));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteWhenInheritManifests() {
    k8sRollingDeploy.setInheritManifests(true);
    doReturn(Activity.builder().build()).when(k8sRollingDeploy).createK8sActivity(any(), any(), any(), any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sRollingDeploy).executeK8sTask(any(), any());
    k8sRollingDeploy.execute(context);
    verify(k8sRollingDeploy, times(1)).executeK8sTask(eq(context), any());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTimeoutValue() {
    K8sRollingDeploy state = new K8sRollingDeploy("k8s-rolling");
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
            .k8sTaskResponse(K8sRollingDeployResponse.builder().helmChartInfo(helmChartInfo).build())
            .build();

    doReturn(Application.Builder.anApplication().uuid("uuid").build()).when(appService).get(nullable(String.class));
    doReturn(InstanceElementListParam.builder().build())
        .when(k8sRollingDeploy)
        .fetchInstanceElementListParam(anyList());
    doReturn(emptyList()).when(k8sRollingDeploy).fetchInstanceStatusSummaries(any(), any());
    doNothing().when(k8sRollingDeploy).saveK8sElement(any(), any());
    doNothing().when(k8sRollingDeploy).saveInstanceInfoToSweepingOutput(any(), any(), any());
    doReturn(APP_ID).when(k8sRollingDeploy).fetchAppId(context);
    ExecutionResponse executionResponse =
        k8sRollingDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of("response", taskExecutionResponse));
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
    List<CommandUnit> result = k8sRollingDeploy.commandUnitList(true, "accountId");
    assertThat(result).isEqualTo(commandUnits);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testCommandName() {
    String commandName = k8sRollingDeploy.commandName();
    assertThat(commandName).isEqualTo(K8S_ROLLING_DEPLOY_COMMAND_NAME);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testStateType() {
    String stateType = k8sRollingDeploy.stateType();
    assertThat(stateType).isEqualTo(K8S_DEPLOYMENT_ROLLING.name());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskFailed() {
    Application app = Application.Builder.anApplication().uuid(APP_ID).build();
    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder().build();
    WorkflowStandardParams standardParams =
        WorkflowStandardParams.Builder.aWorkflowStandardParams().withAppId(APP_ID).build();
    stateExecutionInstance.setStateExecutionMap(
        ImmutableMap.of(stateExecutionInstance.getDisplayName(), stateExecutionData));
    K8sTaskExecutionResponse taskResponse = K8sTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).build();

    context.pushContextElement(standardParams);
    doReturn(app).when(appService).get(APP_ID);
    doReturn(ACTIVITY_ID).when(k8sRollingDeploy).fetchActivityId(context);
    doReturn(APP_ID).when(k8sRollingDeploy).fetchAppId(context);

    ExecutionResponse executionResponse =
        k8sRollingDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of(ACTIVITY_ID, taskResponse));

    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(stateExecutionData);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskK8sResourcesReturned() {
    Application app = Application.Builder.anApplication().accountId("accountId").uuid(APP_ID).build();
    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder().build();
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    WorkflowStandardParams standardParams =
        spy(WorkflowStandardParams.Builder.aWorkflowStandardParams().withAppId(APP_ID).build());
    stateExecutionInstance.setStateExecutionMap(
        ImmutableMap.of(stateExecutionInstance.getDisplayName(), stateExecutionData));
    K8sTaskExecutionResponse taskResponse =
        K8sTaskExecutionResponse.builder()
            .k8sTaskResponse(K8sRollingDeployResponse.builder().resources(kubernetesResources).build())
            .commandExecutionStatus(SUCCESS)
            .build();

    context.pushContextElement(standardParams);
    doReturn(app).when(workflowStandardParamsExtensionService).getApp(standardParams);
    doReturn(app).when(appService).get(APP_ID);
    doReturn(ACTIVITY_ID).when(k8sRollingDeploy).fetchActivityId(context);
    doReturn(APP_ID).when(k8sRollingDeploy).fetchAppId(context);

    ExecutionResponse executionResponse =
        k8sRollingDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of(ACTIVITY_ID, taskResponse));

    verify(k8sStateHelper, times(1))
        .saveResourcesToSweepingOutput(context, kubernetesResources, K8S_DEPLOYMENT_ROLLING.name());
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(executionResponse.getStateExecutionData()).isEqualTo(stateExecutionData);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDelegateHandleAsyncResponseToK8sStateHelper() {
    doReturn(ExecutionResponse.builder().build())
        .when(k8sRollingDeploy)
        .handleAsyncResponseWrapper(any(K8sStateExecutor.class), any(ExecutionContext.class), anyMap());
    K8sTaskExecutionResponse response = K8sTaskExecutionResponse.builder().build();
    Map<String, ResponseData> responseMap = ImmutableMap.of(ACTIVITY_ID, response);

    k8sRollingDeploy.handleAsyncResponse(context, responseMap);
    verify(k8sRollingDeploy, times(1)).handleAsyncResponseWrapper(k8sRollingDeploy, context, responseMap);
  }
}
