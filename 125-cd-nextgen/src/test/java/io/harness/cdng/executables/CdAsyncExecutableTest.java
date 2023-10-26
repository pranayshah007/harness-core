/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.k8s.K8sBGSwapServicesStep;
import io.harness.delegate.AccountId;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskId;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.execution.invokers.StrategyHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

public class CdAsyncExecutableTest extends CategoryTest {
  @Mock private CdTaskExecutableTest cdTaskExecutable;
  @Mock private StrategyHelper strategyHelper;
  @Mock private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock private AsyncExecutableTaskHelper asyncExecutableTaskHelper;

  @InjectMocks private CdAsyncExecutable<K8sDeployResponse, K8sBGSwapServicesStep> cdAsyncExecutable;

  Ambiance ambiance = buildAmbiance();
  StepInputPackage stepInputPackage = StepInputPackage.builder().build();

  StepElementParameters stepElementParameters = StepElementParameters.builder().build();
  private AutoCloseable mocks;
  private static final String TASK_ID = "task-id";

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
    ReflectionTestUtils.setField(cdAsyncExecutable, "cdTaskExecutable", cdTaskExecutable);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    when(cdTaskExecutable.getStepParametersClass()).thenReturn(StepBaseParameters.class);

    Class<StepBaseParameters> stepParametersClass = cdAsyncExecutable.getStepParametersClass();

    verify(cdTaskExecutable).getStepParametersClass();
    assertThat(stepParametersClass).isEqualTo(StepBaseParameters.class);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testValidateResources() {
    cdAsyncExecutable.validateResources(ambiance, stepElementParameters);
    verify(cdTaskExecutable).validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternal() throws Exception {
    StepResponse expectedResponse = StepResponse.builder().status(Status.SUCCEEDED).build();
    when(cdTaskExecutable.handleTaskResult(any(), any(), any())).thenReturn(expectedResponse);

    StepResponse stepResponse =
        cdAsyncExecutable.handleAsyncResponseInternal(ambiance, stepElementParameters, getResponseDataMap());

    verify(cdTaskExecutable).handleTaskResult(any(), any(), any());
    assertThat(stepResponse).isEqualTo(expectedResponse);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseInternalThrowsAnException() throws Exception {
    when(cdTaskExecutable.handleTaskResult(any(), any(), any())).thenThrow(new InvalidArgumentsException("Error"));

    cdAsyncExecutable.handleAsyncResponseInternal(ambiance, stepElementParameters, getResponseDataMap());

    verify(strategyHelper).handleException(any());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testHandleAbort() {
    cdAsyncExecutable.handleAbort(
        ambiance, stepElementParameters, AsyncExecutableResponse.newBuilder().addCallbackIds(TASK_ID).build(), false);

    verify(delegateGrpcClientWrapper)
        .cancelV2Task(AccountId.newBuilder().setId("ACCOUNT_ID").build(), TaskId.newBuilder().setId(TASK_ID).build());
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac() {
    when(cdTaskExecutable.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage))
        .thenReturn(getTaskRequest());
    when(asyncExecutableTaskHelper.extractTaskRequest(any())).thenReturn(TaskData.builder().timeout(1000).build());
    when(asyncExecutableTaskHelper.mapTaskRequestToDelegateTaskRequest(any(), any(), any(), any(), anyBoolean()))
        .thenReturn(io.harness.beans.DelegateTaskRequest.builder().build());
    when(delegateGrpcClientWrapper.submitAsyncTaskV2(any(), any())).thenReturn(TASK_ID);

    AsyncExecutableResponse response =
        cdAsyncExecutable.executeAsyncAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(asyncExecutableTaskHelper).extractTaskRequest(any());
    verify(asyncExecutableTaskHelper).mapTaskRequestToDelegateTaskRequest(any(), any(), any(), any(), anyBoolean());
    assertThat(response.getLogKeys(0)).isEqualTo("logkey1");
    assertThat(response.getLogKeys(1)).isEqualTo("logkey2");
    assertThat(response.getLogKeys(2)).isEqualTo("logkey3");
    assertThat(response.getUnits(0)).isEqualTo("unit1");
    assertThat(response.getUnits(1)).isEqualTo("unit2");
    assertThat(response.getUnits(2)).isEqualTo("unit3");
    assertThat(response.getCallbackIds(0)).isEqualTo(TASK_ID);
    assertThat(response.getTimeout()).isEqualTo(1000);
  }

  static class CdTaskExecutableTest extends CdTaskExecutable<ResponseData> {
    @Override
    public Class<StepBaseParameters> getStepParametersClass() {
      return null;
    }

    @Override
    public StepResponse handleTaskResultWithSecurityContextAndNodeInfo(
        Ambiance ambiance, StepBaseParameters stepParameters, ThrowingSupplier responseDataSupplier) throws Exception {
      return null;
    }

    @Override
    public void validateResources(Ambiance ambiance, StepBaseParameters stepParameters) {}

    @Override
    public TaskRequest obtainTaskAfterRbac(
        Ambiance ambiance, StepBaseParameters stepParameters, StepInputPackage inputPackage) {
      return null;
    }
  }

  private Ambiance buildAmbiance() {
    Level phaseLevel =
        Level.newBuilder()
            .setRuntimeId("PHASE_RUNTIME_ID")
            .setSetupId("PHASE_SETUP_ID")
            .setStepType(StepType.newBuilder().setType("DEPLOY_PHASE").setStepCategory(StepCategory.STEP).build())
            .setGroup("PHASE")
            .build();
    Level sectionLevel =
        Level.newBuilder()
            .setRuntimeId("SECTION_RUNTIME_ID")
            .setSetupId("SECTION_SETUP_ID")
            .setStepType(StepType.newBuilder().setType("DEPLOY_SECTION").setStepCategory(StepCategory.STEP).build())
            .setGroup("SECTION")
            .build();
    List<Level> levels = new ArrayList<>();
    levels.add(phaseLevel);
    levels.add(sectionLevel);
    return Ambiance.newBuilder()
        .setPlanExecutionId("EXECUTION_INSTANCE_ID")
        .putAllSetupAbstractions(ImmutableMap.of(
            "accountId", "ACCOUNT_ID", "appId", "APP_ID", "orgIdentifier", "ORG_ID", "projectIdentifier", "PROJECT_ID"))
        .addAllLevels(levels)
        .build();
  }

  private Map<String, ResponseData> getResponseDataMap() {
    return Map.of("taskId", K8sDeployResponse.builder().build());
  }

  private TaskRequest getTaskRequest() {
    return TaskRequest.newBuilder()
        .setDelegateTaskRequest(
            DelegateTaskRequest.newBuilder()
                .addLogKeys("logkey1")
                .addLogKeys("logkey2")
                .addLogKeys("logkey3")
                .addUnits("unit1")
                .addUnits("unit2")
                .addUnits("unit3")
                .setRequest(SubmitTaskRequest.newBuilder().setDetails(TaskDetails.newBuilder().build()).build())
                .build())
        .build();
  }
}
