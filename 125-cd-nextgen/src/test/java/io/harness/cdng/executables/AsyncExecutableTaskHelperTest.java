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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.cdng.common.beans.StepDetailsDelegateInfo;
import io.harness.delegate.AccountId;
import io.harness.delegate.SubmitTaskRequest;
import io.harness.delegate.TaskDetails;
import io.harness.delegate.TaskLogAbstractions;
import io.harness.delegate.TaskMode;
import io.harness.delegate.TaskSetupAbstractions;
import io.harness.delegate.TaskType;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.k8s.K8sBGDeployRequest;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.DelegateTaskRequest;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.helpers.ext.k8s.request.K8sApplyTaskParameters;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AsyncExecutableTaskHelperTest extends CategoryTest {
  @Mock private KryoSerializer kryoSerializer;
  @Mock private SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @InjectMocks private AsyncExecutableTaskHelper asyncExecutableTaskHelper;

  private AutoCloseable mocks;

  private static final String PARAMS = "PARAMS";
  private static final String JSON_PARAMS = "{name : value}";

  @Before
  public void setUp() throws Exception {
    mocks = MockitoAnnotations.openMocks(this);
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
  public void testExtractTaskRequest() {
    TaskDetails taskDetails = TaskDetails.newBuilder()
                                  .setKryoParameters(ByteString.copyFrom(PARAMS.getBytes()))
                                  .setType(TaskType.newBuilder().setType("SOME_TYPE").build())
                                  .setExecutionTimeout(Duration.newBuilder().setSeconds(3).build())
                                  .setMode(TaskMode.ASYNC)
                                  .build();
    when(kryoSerializer.asInflatedObject(any())).thenReturn(PARAMS);

    TaskData taskData = asyncExecutableTaskHelper.extractTaskRequest(taskDetails);

    assertThat(taskData.getParameters()[0]).isEqualTo(PARAMS);
    assertThat(taskData.getData()).isEqualTo(PARAMS.getBytes());
    assertThat(taskData.getTaskType()).isEqualTo("SOME_TYPE");
    assertThat(taskData.getTimeout()).isEqualTo(3000);
    assertThat(taskData.isAsync()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testExtractTaskRequestWithJsonPrams() {
    TaskDetails taskDetails = TaskDetails.newBuilder()
                                  .setJsonParameters(ByteString.copyFrom(JSON_PARAMS.getBytes()))
                                  .setType(TaskType.newBuilder().setType("SOME_TYPE").build())
                                  .setExecutionTimeout(Duration.newBuilder().setSeconds(3).build())
                                  .setMode(TaskMode.ASYNC)
                                  .build();

    TaskData taskData = asyncExecutableTaskHelper.extractTaskRequest(taskDetails);

    assertThat(taskData.getParameters()).isNull();
    assertThat(taskData.getData()).isEqualTo(JSON_PARAMS.getBytes());
    assertThat(taskData.getTaskType()).isEqualTo("SOME_TYPE");
    assertThat(taskData.getTimeout()).isEqualTo(3000);
    assertThat(taskData.isAsync()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testMapTaskRequestToDelegateTaskRequest() {
    TaskRequest taskRequest =
        TaskRequest.newBuilder()
            .setDelegateTaskRequest(
                DelegateTaskRequest.newBuilder()
                    .setRequest(SubmitTaskRequest.newBuilder()
                                    .setAccountId(AccountId.newBuilder().setId("accountId").build())
                                    .setSetupAbstractions(TaskSetupAbstractions.newBuilder()
                                                              .putAllValues(Map.of("key1", "value1", "key2", "value2"))
                                                              .build())
                                    .setLogAbstractions(TaskLogAbstractions.newBuilder()
                                                            .putAllValues(Map.of("ab1", "cd1", "ab2", "cd2"))
                                                            .build())
                                    .setForceExecute(false)
                                    .addAllEligibleToExecuteDelegateIds(List.of("delegate1", "delegate2"))
                                    .setExecuteOnHarnessHostedDelegates(true)
                                    .setEmitEvent(false)
                                    .setStageId("stageId")
                                    .build())
                    .build())
            .build();
    Set<String> taskSelectors = Set.of("selector1", "selector2");
    TaskData taskData = TaskData.builder()
                            .parameters(new Object[] {K8sApplyTaskParameters.builder().build()})
                            .taskType("SOME_TYPE")
                            .parked(false)
                            .timeout(3000)
                            .expressionFunctorToken(23)
                            .build();

    io.harness.beans.DelegateTaskRequest delegateTaskRequest =
        asyncExecutableTaskHelper.mapTaskRequestToDelegateTaskRequest(
            taskRequest, taskData, taskSelectors, "baseLogKey", true);

    assertThat(delegateTaskRequest.getAccountId()).isEqualTo("accountId");
    assertThat(delegateTaskRequest.getTaskSetupAbstractions()).isEqualTo(Map.of("key1", "value1", "key2", "value2"));
    assertThat(delegateTaskRequest.getLogStreamingAbstractions()).isEqualTo(Map.of("ab1", "cd1", "ab2", "cd2"));
    assertThat(delegateTaskRequest.isForceExecute()).isFalse();
    assertThat(delegateTaskRequest.getEligibleToExecuteDelegateIds()).isEqualTo(List.of("delegate1", "delegate2"));
    assertThat(delegateTaskRequest.isExecuteOnHarnessHostedDelegates()).isTrue();
    assertThat(delegateTaskRequest.isEmitEvent()).isFalse();
    assertThat(delegateTaskRequest.getStageId()).isEqualTo("stageId");
    assertThat(delegateTaskRequest.getTaskSelectors().size()).isEqualTo(2);
    assertThat(delegateTaskRequest.getTaskParameters()).isInstanceOf(K8sApplyTaskParameters.class);
    assertThat(delegateTaskRequest.getTaskType()).isEqualTo("SOME_TYPE");
    assertThat(delegateTaskRequest.isParked()).isFalse();
    assertThat(delegateTaskRequest.getExecutionTimeout().getSeconds()).isEqualTo(3);
    assertThat(delegateTaskRequest.getExpressionFunctorToken()).isEqualTo(23);
    assertThat(delegateTaskRequest.getBaseLogKey()).isEqualTo("baseLogKey");
    assertThat(delegateTaskRequest.isShouldSkipOpenStream()).isTrue();
    assertThat(delegateTaskRequest.isSelectionLogsTrackingEnabled()).isTrue();
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testPublishStepDelegateInfoStepDetails() {
    TaskData taskData =
        TaskData.builder()
            .parameters(new Object[] {K8sBGDeployRequest.builder().commandName("K8s BG Deploy").build()})
            .build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    String taskName = "Task Name";
    String taskId = "task-id";

    asyncExecutableTaskHelper.publishStepDelegateInfoStepDetails(ambiance, taskData, taskName, taskId);

    StepDetailsDelegateInfo stepDetailsDelegateInfo =
        StepDetailsDelegateInfo.builder()
            .stepDelegateInfos(List.of(StepDelegateInfo.builder().taskName(taskName).taskId(taskId).build()))
            .build();
    verify(sdkGraphVisualizationDataService)
        .publishStepDetailInformation(ambiance, stepDetailsDelegateInfo, "K8s BG Deploy");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testPublishStepDelegateInfoStepDetailsWithParamNotK8sDeploy() {
    TaskData taskData = TaskData.builder().parameters(new Object[] {GitFetchRequest.builder().build()}).build();
    Ambiance ambiance = Ambiance.newBuilder().build();
    String taskName = "Task Name";
    String taskId = "task-id";

    asyncExecutableTaskHelper.publishStepDelegateInfoStepDetails(ambiance, taskData, taskName, taskId);

    StepDetailsDelegateInfo stepDetailsDelegateInfo =
        StepDetailsDelegateInfo.builder()
            .stepDelegateInfos(List.of(StepDelegateInfo.builder().taskName(taskName).taskId(taskId).build()))
            .build();
    verify(sdkGraphVisualizationDataService).publishStepDetailInformation(ambiance, stepDetailsDelegateInfo, "");
  }
}
