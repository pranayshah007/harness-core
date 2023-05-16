/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.k8s;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.common.WorkflowConstants.K8S_SCALE;
import static software.wings.sm.StateExecutionInstance.Builder.aStateExecutionInstance;
import static software.wings.sm.WorkflowStandardParams.Builder.aWorkflowStandardParams;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.STATE_NAME;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.api.InstanceElementListParam;
import software.wings.api.k8s.K8sElement;
import software.wings.api.k8s.K8sStateExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.InstanceUnitType;
import software.wings.common.VariableProcessor;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.helpers.ext.k8s.request.K8sTaskParameters;
import software.wings.helpers.ext.k8s.response.K8sScaleResponse;
import software.wings.helpers.ext.k8s.response.K8sTaskExecutionResponse;
import software.wings.service.intfc.ActivityService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.WorkflowStandardParams;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
public class K8sScaleTest extends CategoryTest {
  @Mock private VariableProcessor variableProcessor;
  @Mock private ManagerExpressionEvaluator evaluator;
  @Mock private ActivityService activityService;
  @Mock private K8sStateHelper k8sStateHelper;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks private K8sScale k8sScale = spy(new K8sScale(K8S_SCALE));

  private static final String ERROR_MESSAGE = "errorMessage";
  private static final String RELEASE_NAME = "releaseName";
  private WorkflowStandardParams workflowStandardParams = aWorkflowStandardParams().withAppId(APP_ID).build();
  private StateExecutionInstance stateExecutionInstance =
      aStateExecutionInstance()
          .displayName(STATE_NAME)
          .addContextElement(workflowStandardParams)
          .addStateExecutionData(K8sStateExecutionData.builder().build())
          .build();
  private ExecutionContextImpl context;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    context = new ExecutionContextImpl(stateExecutionInstance);
    k8sScale.setStateTimeoutInMinutes(10);
    k8sScale.setInstances("5");
    k8sScale.setInstanceUnitType(InstanceUnitType.COUNT);

    when(variableProcessor.getVariables(any(), any())).thenReturn(emptyMap());
    when(evaluator.substitute(anyString(), anyMap(), any(VariableResolverTracker.class), anyString()))
        .thenAnswer(i -> i.getArguments()[0]);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseForFailure() {
    doReturn(ACTIVITY_ID).when(k8sScale).fetchActivityId(context);
    doReturn(APP_ID).when(k8sScale).fetchAppId(context);
    K8sTaskExecutionResponse k8sTaskExecutionResponse = K8sTaskExecutionResponse.builder()
                                                            .errorMessage("errorMessage")
                                                            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                            .k8sTaskResponse(K8sScaleResponse.builder().build())
                                                            .build();

    ExecutionResponse executionResponse =
        k8sScale.handleAsyncResponse(context, ImmutableMap.of(ACTIVITY_ID, k8sTaskExecutionResponse));
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(executionResponse.getStateExecutionData().getErrorMsg()).isEqualTo(ERROR_MESSAGE);
    verify(activityService, times(1)).updateStatus(ACTIVITY_ID, APP_ID, ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testTimeoutValue() {
    K8sScale state = new K8sScale("k8s-scale");
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
    k8sScale.handleAbortEvent(context);
    verify(k8sScale, times(1)).handleAbortEvent(any(ExecutionContext.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecuteSkipSteadyStateCheck() {
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    when(k8sStateHelper.fetchContainerInfrastructureMapping(any()))
        .thenReturn(DirectKubernetesInfrastructureMapping.builder().build());
    when(featureFlagService.isEnabled(eq(FeatureName.NEW_KUBECTL_VERSION), any())).thenReturn(false);
    when(executionContext.renderExpression(anyString())).thenReturn("1");
    k8sScale.setSkipSteadyStateCheck(true);
    when(k8sStateHelper.fetchK8sElement(any(ExecutionContext.class))).thenReturn(K8sElement.builder().build());
    doReturn(new Activity())
        .when(k8sScale)
        .createK8sActivity(
            any(ExecutionContext.class), anyString(), anyString(), any(ActivityService.class), anyList());
    doReturn(ExecutionResponse.builder().build()).when(k8sScale).queueK8sDelegateTask(any(), any(), any());
    doReturn(RELEASE_NAME).when(k8sScale).fetchReleaseName(any(), any());

    k8sScale.execute(executionContext);
    verify(k8sScale, times(1)).queueK8sDelegateTask(any(ExecutionContext.class), any(K8sTaskParameters.class), any());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testExecute() {
    ExecutionContextImpl executionContext = mock(ExecutionContextImpl.class);
    when(k8sStateHelper.fetchContainerInfrastructureMapping(any()))
        .thenReturn(DirectKubernetesInfrastructureMapping.builder().build());
    when(featureFlagService.isEnabled(eq(FeatureName.NEW_KUBECTL_VERSION), any())).thenReturn(false);
    when(executionContext.renderExpression(anyString())).thenReturn("1");
    when(k8sStateHelper.fetchK8sElement(any(ExecutionContext.class))).thenReturn(K8sElement.builder().build());
    doReturn(RELEASE_NAME).when(k8sScale).fetchReleaseName(any(), any());
    doReturn(new Activity())
        .when(k8sScale)
        .createK8sActivity(
            any(ExecutionContext.class), anyString(), anyString(), any(ActivityService.class), anyList());
    doReturn(ExecutionResponse.builder().build()).when(k8sScale).queueK8sDelegateTask(any(), any(), any());
    k8sScale.execute(executionContext);
    verify(k8sScale, times(1)).queueK8sDelegateTask(any(ExecutionContext.class), any(K8sTaskParameters.class), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteWingsException() {
    InvalidArgumentsException exceptionToBeThrown = new InvalidArgumentsException(Pair.of("args", "missing"));
    doThrow(exceptionToBeThrown).when(k8sStateHelper).fetchContainerInfrastructureMapping(context);

    assertThatThrownBy(() -> k8sScale.execute(context)).isSameAs(exceptionToBeThrown);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteAnyException() {
    IllegalStateException exceptionToBeThrown = new IllegalStateException();
    doThrow(exceptionToBeThrown).when(k8sStateHelper).fetchContainerInfrastructureMapping(context);

    assertThatThrownBy(() -> k8sScale.execute(context)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    ExecutionContext executionContext = spy(context);
    doReturn("accountId").when(executionContext).getAccountId();
    K8sTaskExecutionResponse k8sTaskExecutionResponse = K8sTaskExecutionResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .k8sTaskResponse(K8sScaleResponse.builder().build())
                                                            .build();
    Map<String, ResponseData> response = new HashMap<>();
    response.put(ACTIVITY_ID, k8sTaskExecutionResponse);
    doReturn(InstanceElementListParam.builder().build()).when(k8sScale).fetchInstanceElementListParam(anyList());
    doReturn(emptyList()).when(k8sScale).fetchInstanceStatusSummaries(any(), any());
    k8sScale.handleAsyncResponse(executionContext, response);
    verify(activityService, times(1)).updateStatus(nullable(String.class), anyString(), any(ExecutionStatus.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseWingsException() {
    K8sTaskExecutionResponse response = K8sTaskExecutionResponse.builder().build();
    InvalidArgumentsException exceptionToBeThrown = new InvalidArgumentsException(Pair.of("args", "missing"));

    doThrow(exceptionToBeThrown)
        .when(activityService)
        .updateStatus(nullable(String.class), nullable(String.class), any(ExecutionStatus.class));

    assertThatThrownBy(() -> k8sScale.handleAsyncResponse(context, ImmutableMap.of(ACTIVITY_ID, response)))
        .isSameAs(exceptionToBeThrown);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseAnyException() {
    K8sTaskExecutionResponse response = K8sTaskExecutionResponse.builder().build();
    IllegalStateException exceptionToBeThrown = new IllegalStateException();

    doThrow(exceptionToBeThrown)
        .when(activityService)
        .updateStatus(nullable(String.class), nullable(String.class), any(ExecutionStatus.class));

    assertThatThrownBy(() -> k8sScale.handleAsyncResponse(context, ImmutableMap.of(ACTIVITY_ID, response)))
        .isInstanceOf(InvalidRequestException.class);
  }
}
