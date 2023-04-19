/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ABHINAV2;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.common.WorkflowConstants.K8S_DEPLOYMENT_ROLLING_ROLLBACK;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import io.harness.context.ContextElementType;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.api.k8s.K8sContextElement;
import software.wings.api.k8s.K8sHelmDeploymentElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sRollingDeployRollbackResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
public class K8sRollingDeployRollbackTest extends CategoryTest {
  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private ActivityService activityService;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks
  K8sRollingDeployRollback k8sRollingDeployRollback =
      spy(new K8sRollingDeployRollback(K8S_DEPLOYMENT_ROLLING_ROLLBACK));

  @InjectMocks
  K8sRollingDeployRollback k8sRollingState = spy(new K8sRollingDeployRollback(K8S_DEPLOYMENT_ROLLING_ROLLBACK));

  private final StateExecutionInstance stateExecutionInstance =
      aStateExecutionInstance().displayName(STATE_NAME).build();
  private final ExecutionContextImpl context = new ExecutionContextImpl(stateExecutionInstance);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTimeoutValue() {
    K8sRollingDeployRollback state = new K8sRollingDeployRollback("k8s-rollback");
    assertThat(state.getTimeoutMillis()).isNull();

    state.setStateTimeoutInMinutes(5);
    assertThat(state.getTimeoutMillis()).isEqualTo(300000);

    state.setStateTimeoutInMinutes(Integer.MAX_VALUE);
    assertThat(state.getTimeoutMillis()).isNull();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAbortEvent() {
    k8sRollingDeployRollback.handleAbortEvent(context);
    verify(k8sRollingDeployRollback, times(1)).handleAbortEvent(any(ExecutionContext.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteSkipped() {
    ExecutionResponse response = k8sRollingDeployRollback.execute(context);
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testExecuteSkippedWhenK8sDeployIsSkipped() {
    stateExecutionInstance.setContextElements(
        new LinkedList<>(Collections.singletonList(K8sContextElement.builder().build())));
    ExecutionResponse response = k8sRollingDeployRollback.execute(context);
    assertThat(response.getExecutionStatus()).isEqualTo(SKIPPED);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecute() {
    when(k8sStateHelper.fetchContainerInfrastructureMapping(any()))
        .thenReturn(DirectKubernetesInfrastructureMapping.builder().build());
    when(featureFlagService.isEnabled(eq(FeatureName.NEW_KUBECTL_VERSION), any())).thenReturn(false);
    stateExecutionInstance.setContextElements(
        new LinkedList<>(Arrays.asList(K8sContextElement.builder().releaseName(STATE_NAME).build())));
    doReturn(new Activity())
        .when(k8sRollingState)
        .createK8sActivity(
            any(ExecutionContext.class), anyString(), anyString(), any(ActivityService.class), anyList());
    doReturn(ExecutionResponse.builder().build()).when(k8sRollingState).queueK8sDelegateTask(any(), any(), any());
    ExecutionResponse response = k8sRollingState.execute(context);
    verify(k8sRollingState, times(1))
        .createK8sActivity(
            any(ExecutionContext.class), anyString(), anyString(), any(ActivityService.class), anyList());
    verify(k8sRollingState, times(1))
        .queueK8sDelegateTask(any(ExecutionContext.class), any(K8sTaskParameters.class), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteInvalidRequestException() {
    stateExecutionInstance.setContextElements(
        new LinkedList<>(Arrays.asList(K8sContextElement.builder().releaseName(STATE_NAME).build())));
    k8sRollingDeployRollback.execute(context);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    K8sRollingDeployRollbackResponse rollbackResponse =
        K8sRollingDeployRollbackResponse.builder().k8sPodList(Collections.emptyList()).build();
    K8sTaskExecutionResponse k8sTaskExecutionResponse = K8sTaskExecutionResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .k8sTaskResponse(rollbackResponse)
                                                            .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("k8sTaskExecutionResponse", k8sTaskExecutionResponse);

    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    stateExecutionInstance.setContextElements(new LinkedList<>(Arrays.asList(workflowStandardParams)));

    Map<String, StateExecutionData> stateExecutionMap = new HashMap<>();
    stateExecutionMap.put(STATE_NAME, new K8sStateExecutionData());
    stateExecutionInstance.setStateExecutionMap(stateExecutionMap);
    doReturn(K8sHelmDeploymentElement.builder().build())
        .when(k8sRollingDeployRollback)
        .fetchK8sHelmDeploymentElement(any());
    doNothing()
        .when(k8sRollingDeployRollback)
        .saveInstanceInfoToSweepingOutput(any(ExecutionContext.class), anyList(), anyList());

    ExecutionResponse executionResponse = k8sRollingDeployRollback.handleAsyncResponse(context, response);

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    verify(activityService, times(1))
        .updateStatus(nullable(String.class), nullable(String.class), any(ExecutionStatus.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInvalidRequestException() {
    K8sTaskExecutionResponse k8sTaskExecutionResponse =
        K8sTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put("k8sTaskExecutionResponse", k8sTaskExecutionResponse);
    WorkflowStandardParams workflowStandardParams = new WorkflowStandardParams();
    stateExecutionInstance.setContextElements(new LinkedList<>(Arrays.asList(workflowStandardParams)));
    k8sRollingDeployRollback.handleAsyncResponse(context, response);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGettingHelmChartInfoFromK8sHelmElement() {
    ExecutionContextImpl spyContext = spy(context);
    K8sRollingDeployRollbackResponse rollbackResponse =
        K8sRollingDeployRollbackResponse.builder().k8sPodList(Collections.emptyList()).build();
    Map<String, ResponseData> response = ImmutableMap.of("response",
        K8sTaskExecutionResponse.builder().commandExecutionStatus(SUCCESS).k8sTaskResponse(rollbackResponse).build());
    doReturn(WorkflowStandardParams.Builder.aWorkflowStandardParams().withAppId(APP_ID).build())
        .when(spyContext)
        .getContextElement(ContextElementType.STANDARD);

    testK8sHelmElementExists(spyContext, response);
    testK8sHelmElementDoesntExists(spyContext, response);
  }

  private void testK8sHelmElementExists(ExecutionContextImpl context, Map<String, ResponseData> response) {
    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder().build();
    HelmChartInfo helmChartInfo = HelmChartInfo.builder().name("name").version("1.2.3").build();
    doReturn(K8sHelmDeploymentElement.builder().previousDeployedHelmChart(helmChartInfo).build())
        .when(k8sRollingState)
        .fetchK8sHelmDeploymentElement(context);
    doReturn(stateExecutionData).when(context).getStateExecutionData();
    doNothing()
        .when(k8sRollingState)
        .saveInstanceInfoToSweepingOutput(any(ExecutionContext.class), anyList(), anyList());
    k8sRollingState.handleAsyncResponse(context, response);

    assertThat(stateExecutionData.getHelmChartInfo()).isEqualTo(helmChartInfo);
  }

  private void testK8sHelmElementDoesntExists(ExecutionContextImpl context, Map<String, ResponseData> response) {
    K8sStateExecutionData stateExecutionData = K8sStateExecutionData.builder().build();
    doReturn(null).when(k8sRollingState).fetchK8sHelmDeploymentElement(context);
    doNothing()
        .when(k8sRollingState)
        .saveInstanceInfoToSweepingOutput(any(ExecutionContext.class), anyList(), anyList());
    k8sRollingState.handleAsyncResponse(context, response);

    assertThat(stateExecutionData.getHelmChartInfo()).isNull();
  }
}
