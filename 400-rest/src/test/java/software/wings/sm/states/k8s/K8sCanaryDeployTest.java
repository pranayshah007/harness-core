/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.k8s.K8sTaskType.CANARY_DEPLOY;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.GcpKubernetesInfrastructureMapping.Builder.aGcpKubernetesInfrastructureMapping;
import static software.wings.beans.appmanifest.StoreType.Local;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.StateType.K8S_CANARY_DEPLOY;
import static software.wings.sm.states.k8s.K8sCanaryDeploy.K8S_CANARY_DEPLOY_COMMAND_NAME;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
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
import static org.mockito.ArgumentMatchers.isA;
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
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.k8s.model.KubernetesResource;
import io.harness.rule.Owner;

import software.wings.api.ContextElementParamMapper;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.InstanceElementListParam;
import software.wings.api.k8s.K8sElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.InstanceUnitType;
import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.k8s.request.K8sCanaryDeployTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.helpers.ext.k8s.response.K8sCanaryDeployResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.utils.ApplicationManifestUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
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
public class K8sCanaryDeployTest extends CategoryTest {
  private static final String RELEASE_NAME = "releaseName";

  @Mock private ApplicationManifestUtils applicationManifestUtils;
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private ActivityService activityService;
  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private ContextElementParamMapperFactory contextElementParamMapperFactory;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;
  @InjectMocks K8sCanaryDeploy k8sCanaryDeploy = spy(new K8sCanaryDeploy(K8S_CANARY_DEPLOY.name()));

  private StateExecutionInstance stateExecutionInstance = aStateExecutionInstance().displayName(STATE_NAME).build();

  @InjectMocks private ExecutionContextImpl context;

  @Before
  public void setup() {
    context = new ExecutionContextImpl(stateExecutionInstance);
    MockitoAnnotations.initMocks(this);

    on(context).set("contextElementParamMapperFactory", contextElementParamMapperFactory);
    on(context).set("workflowStandardParamsExtensionService", workflowStandardParamsExtensionService);
    k8sCanaryDeploy.setSkipDryRun(true);
    k8sCanaryDeploy.setStateTimeoutInMinutes(10);
    k8sCanaryDeploy.setInstances("5");
    k8sCanaryDeploy.setInstanceUnitType(InstanceUnitType.COUNT);

    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);

    on(context).set("variableProcessor", variableProcessor);
    on(context).set("evaluator", evaluator);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testExecute() {
    when(mockFeatureFlagService.isEnabled(eq(FeatureName.NEW_KUBECTL_VERSION), any())).thenReturn(false);
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(RELEASE_NAME).when(k8sCanaryDeploy).fetchReleaseName(any(), any());
    doNothing().when(k8sCanaryDeploy).saveK8sCanaryDeployRun(any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sCanaryDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sCanaryDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sCanaryDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sCanaryDeploy).fetchApplicationManifests(any());

    k8sCanaryDeploy.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sTaskParamsArgumentCaptor = ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sCanaryDeploy, times(1))
        .queueK8sDelegateTask(any(), k8sTaskParamsArgumentCaptor.capture(), any(applicationManifestMap.getClass()));
    K8sCanaryDeployTaskParameters taskParams = (K8sCanaryDeployTaskParameters) k8sTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(CANARY_DEPLOY);
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_CANARY_DEPLOY_COMMAND_NAME);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.getInstanceUnitType()).isEqualTo(InstanceUnitType.COUNT);
    assertThat(taskParams.getInstances()).isEqualTo(5);
    assertThat(taskParams.isSkipDryRun()).isTrue();
    assertThat(taskParams.isUseNewKubectlVersion()).isFalse();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritManifests() {
    k8sCanaryDeploy.setInheritManifests(true);
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.add(KubernetesResource.builder().build());
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(RELEASE_NAME).when(k8sCanaryDeploy).fetchReleaseName(any(), any());
    doNothing().when(k8sCanaryDeploy).saveK8sCanaryDeployRun(any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sCanaryDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sCanaryDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sCanaryDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sCanaryDeploy).fetchApplicationManifests(any());

    k8sCanaryDeploy.executeK8sTask(context, ACTIVITY_ID);

    ArgumentCaptor<K8sTaskParameters> k8sTaskParamsArgumentCaptor = ArgumentCaptor.forClass(K8sTaskParameters.class);
    verify(k8sCanaryDeploy, times(1))
        .queueK8sDelegateTask(any(), k8sTaskParamsArgumentCaptor.capture(), any(applicationManifestMap.getClass()));
    K8sCanaryDeployTaskParameters taskParams = (K8sCanaryDeployTaskParameters) k8sTaskParamsArgumentCaptor.getValue();

    assertThat(taskParams.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(taskParams.getActivityId()).isEqualTo(ACTIVITY_ID);
    assertThat(taskParams.getCommandType()).isEqualTo(CANARY_DEPLOY);
    assertThat(taskParams.getCommandName()).isEqualTo(K8S_CANARY_DEPLOY_COMMAND_NAME);
    assertThat(taskParams.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskParams.getInstanceUnitType()).isEqualTo(InstanceUnitType.COUNT);
    assertThat(taskParams.getInstances()).isEqualTo(5);
    assertThat(taskParams.isSkipDryRun()).isTrue();
    assertThat(taskParams.getKubernetesResources()).isEqualTo(kubernetesResources);
    assertThat(taskParams.isInheritManifests()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteInheritManifestsNoResourcesFound() {
    k8sCanaryDeploy.setInheritManifests(true);
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(RELEASE_NAME).when(k8sCanaryDeploy).fetchReleaseName(any(), any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sCanaryDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sCanaryDeploy).fetchRenderedValuesFiles(any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sCanaryDeploy).fetchApplicationManifests(any());

    assertThatThrownBy(() -> k8sCanaryDeploy.executeK8sTask(context, ACTIVITY_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("No kubernetes resources found to inherit");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testExecuteWhenInheritManifests() {
    k8sCanaryDeploy.setInheritManifests(true);
    doReturn(Activity.builder().build()).when(k8sCanaryDeploy).createK8sActivity(any(), any(), any(), any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sCanaryDeploy).executeK8sTask(any(), any());
    k8sCanaryDeploy.execute(context);
    verify(k8sCanaryDeploy, times(1)).executeK8sTask(eq(context), any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidateParameters() {
    doNothing().when(k8sCanaryDeploy).validateK8sV2TypeServiceUsed(context);
    k8sCanaryDeploy.validateParameters(context);
    verify(k8sCanaryDeploy, times(1)).validateK8sV2TypeServiceUsed(context);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testValidateParametersExportManifests() {
    k8sCanaryDeploy.setExportManifests(true);
    k8sCanaryDeploy.setInstances(null);
    doNothing().when(k8sCanaryDeploy).validateK8sV2TypeServiceUsed(context);
    k8sCanaryDeploy.validateParameters(context);
    verify(k8sCanaryDeploy, times(1)).validateK8sV2TypeServiceUsed(context);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTimeoutValue() {
    K8sCanaryDeploy state = new K8sCanaryDeploy("k8s-canary");
    assertThat(state.getTimeoutMillis()).isNull();

    state.setStateTimeoutInMinutes(5);
    assertThat(state.getTimeoutMillis()).isEqualTo(300000);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHelmChartInfoValue() {
    Application application = new Application();
    application.setAccountId("accountId");
    WorkflowStandardParams standardContextElement =
        spy(WorkflowStandardParams.Builder.aWorkflowStandardParams().build());
    stateExecutionInstance.setContextElements(Lists.newLinkedList(Arrays.asList(standardContextElement)));
    stateExecutionInstance.setStateExecutionMap(ImmutableMap.of(STATE_NAME, new K8sStateExecutionData()));
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("chart").version("1.0.0").build();
    K8sTaskExecutionResponse taskExecutionResponse =
        K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .k8sTaskResponse(K8sCanaryDeployResponse.builder().helmChartInfo(helmChartInfo).build())
            .build();

    doReturn(InstanceElementListParam.builder().build()).when(k8sCanaryDeploy).fetchInstanceElementListParam(anyList());
    doReturn(false).when(k8sCanaryDeploy).shouldSaveManifest(any());
    doReturn(emptyList()).when(k8sCanaryDeploy).fetchInstanceStatusSummaries(any(), any());
    doNothing().when(k8sCanaryDeploy).saveK8sElement(any(), any());
    doNothing().when(k8sCanaryDeploy).saveInstanceInfoToSweepingOutput(any(), any(), any());
    doReturn(APP_ID).when(k8sCanaryDeploy).fetchAppId(context);
    doReturn(application).when(workflowStandardParamsExtensionService).getApp(standardContextElement);
    doReturn(false).when(k8sCanaryDeploy).shouldInheritManifest(context);

    // Overwrite default param mapper for WorkflowStandardParams.
    ContextElementParamMapper paramMapper = spy(ContextElementParamMapper.class);
    when(paramMapper.paramMap(any())).thenReturn(ImmutableMap.of());
    when(this.contextElementParamMapperFactory.getParamMapper(isA(WorkflowStandardParams.class)))
        .thenReturn(paramMapper);

    ExecutionResponse executionResponse =
        k8sCanaryDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of("response", taskExecutionResponse));
    K8sStateExecutionData executionData = (K8sStateExecutionData) executionResponse.getStateExecutionData();

    assertThat(executionData.getHelmChartInfo()).isEqualTo(helmChartInfo);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskResourcesReturned() {
    setupHandleAsyncResponsePrerequisites();
    List<KubernetesResource> resources = new ArrayList<>();
    K8sTaskExecutionResponse taskExecutionResponse =
        K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(SUCCESS)
            .k8sTaskResponse(K8sCanaryDeployResponse.builder().resources(resources).build())
            .build();
    ExecutionResponse executionResponse =
        k8sCanaryDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of(ACTIVITY_ID, taskExecutionResponse));

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.SUCCESS);
    verify(k8sStateHelper, times(1)).saveResourcesToSweepingOutput(context, resources, K8S_CANARY_DEPLOY.name());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testCommandUnitList() {
    List<CommandUnit> commandUnits = new ArrayList<>();
    doReturn(commandUnits)
        .when(k8sStateHelper)
        .getCommandUnits(anyBoolean(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    List<CommandUnit> result = k8sCanaryDeploy.commandUnitList(true, "accountId");
    assertThat(result).isEqualTo(commandUnits);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testCommandName() {
    String commandName = k8sCanaryDeploy.commandName();
    assertThat(commandName).isEqualTo(K8S_CANARY_DEPLOY_COMMAND_NAME);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testStateType() {
    String stateType = k8sCanaryDeploy.stateType();
    assertThat(stateType).isEqualTo(K8S_CANARY_DEPLOY.name());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDelegateExecuteToK8sStateHelper() {
    doReturn(ExecutionResponse.builder().build())
        .when(k8sCanaryDeploy)
        .executeWrapperWithManifest(any(K8sStateExecutor.class), any(ExecutionContext.class), anyLong());
    k8sCanaryDeploy.execute(context);
    verify(k8sCanaryDeploy, times(1))
        .executeWrapperWithManifest(
            k8sCanaryDeploy, context, K8sStateHelper.fetchSafeTimeoutInMillis(k8sCanaryDeploy.getTimeoutMillis()));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskFailed() {
    setupHandleAsyncResponsePrerequisites();
    K8sTaskExecutionResponse taskResponse = K8sTaskExecutionResponse.builder().commandExecutionStatus(FAILURE).build();

    ExecutionResponse executionResponse =
        k8sCanaryDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of(ACTIVITY_ID, taskResponse));

    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForK8sTaskFailedWithCanaryWorkload() {
    setupHandleAsyncResponsePrerequisites();
    K8sTaskExecutionResponse taskResponse =
        K8sTaskExecutionResponse.builder()
            .commandExecutionStatus(FAILURE)
            .k8sTaskResponse(K8sCanaryDeployResponse.builder().canaryWorkload("workload").build())
            .build();
    doNothing().when(k8sCanaryDeploy).saveK8sElement(any(), any());

    ExecutionResponse executionResponse =
        k8sCanaryDeploy.handleAsyncResponseForK8sTask(context, ImmutableMap.of(ACTIVITY_ID, taskResponse));

    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
    verify(k8sCanaryDeploy, times(1)).saveK8sElement(context, K8sElement.builder().canaryWorkload("workload").build());

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  private void setupHandleAsyncResponsePrerequisites() {
    Application application = new Application();
    application.setAccountId("accountId");
    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder().activityId(ACTIVITY_ID).build();
    WorkflowStandardParams standardParams =
        spy(WorkflowStandardParams.Builder.aWorkflowStandardParams().withAppId(APP_ID).build());

    context.pushContextElement(standardParams);
    stateExecutionInstance.setStateExecutionMap(
        ImmutableMap.of(stateExecutionInstance.getDisplayName(), stateExecutionData));
    doReturn(application).when(workflowStandardParamsExtensionService).getApp(standardParams);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testNotSaveK8sCanaryDeploy() {
    k8sCanaryDeploy.setInheritManifests(true);
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.add(KubernetesResource.builder().build());
    k8sCanaryDeploy.setExportManifests(true);
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(RELEASE_NAME).when(k8sCanaryDeploy).fetchReleaseName(any(), any());
    doNothing().when(k8sCanaryDeploy).saveK8sCanaryDeployRun(any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sCanaryDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sCanaryDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sCanaryDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sCanaryDeploy).fetchApplicationManifests(any());
    doNothing().when(k8sCanaryDeploy).saveK8sCanaryDeployRun(context);
    k8sCanaryDeploy.executeK8sTask(context, ACTIVITY_ID);
    verify(k8sCanaryDeploy, times(0)).saveK8sCanaryDeployRun(context);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testSaveK8sCanaryDeploy() {
    k8sCanaryDeploy.setInheritManifests(true);
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.add(KubernetesResource.builder().build());
    k8sCanaryDeploy.setExportManifests(false);
    when(applicationManifestUtils.getApplicationManifests(context, AppManifestKind.VALUES)).thenReturn(new HashMap<>());
    when(k8sStateHelper.fetchContainerInfrastructureMapping(context))
        .thenReturn(aGcpKubernetesInfrastructureMapping().build());
    doReturn(kubernetesResources).when(k8sStateHelper).getResourcesFromSweepingOutput(any(), anyString());
    doReturn(RELEASE_NAME).when(k8sCanaryDeploy).fetchReleaseName(any(), any());
    doNothing().when(k8sCanaryDeploy).saveK8sCanaryDeployRun(any());
    doReturn(K8sDelegateManifestConfig.builder().build())
        .when(k8sCanaryDeploy)
        .createDelegateManifestConfig(any(), any());
    doReturn(emptyList()).when(k8sCanaryDeploy).fetchRenderedValuesFiles(any(), any());
    doReturn(ExecutionResponse.builder().build()).when(k8sCanaryDeploy).queueK8sDelegateTask(any(), any(), any());
    ApplicationManifest applicationManifest =
        ApplicationManifest.builder().skipVersioningForAllK8sObjects(true).storeType(Local).build();
    Map<K8sValuesLocation, ApplicationManifest> applicationManifestMap = new HashMap<>();
    applicationManifestMap.put(K8sValuesLocation.Service, applicationManifest);
    doReturn(applicationManifestMap).when(k8sCanaryDeploy).fetchApplicationManifests(any());
    doNothing().when(k8sCanaryDeploy).saveK8sCanaryDeployRun(context);
    k8sCanaryDeploy.executeK8sTask(context, ACTIVITY_ID);
    verify(k8sCanaryDeploy, times(1)).saveK8sCanaryDeployRun(context);
  }
}
