/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AsyncDelegateResumeCallbackTest extends CategoryTest {
  private static String NODE_EXECUTION_ID = "nodeId";
  private static String PLAN_EXECUTION_ID = "planExecutionId";

  @Mock SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Mock AsyncDelegateInfoHelper asyncDelegateInfoHelper;
  @InjectMocks private AsyncDelegateResumeCallback asyncDelegateResumeCallback;
  Ambiance ambiance;
  Map<String, ResponseData> response;
  Map<String, ResponseData> responseWrong;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .addLevels(Level.newBuilder().setRuntimeId(NODE_EXECUTION_ID).build())
                   .build();
    response = new HashMap<>();
    response.put("taskId", K8sDeployResponse.builder().build());
    responseWrong = new HashMap<>();
    responseWrong.put("taskWrongId", K8sDeployResponse.builder().build());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testNotify() {
    AsyncDelegateResumeCallback asyncDelegateResumeCallback =
        AsyncDelegateResumeCallback.builder()
            .sdkGraphVisualizationDataService(sdkGraphVisualizationDataService)
            .asyncDelegateInfoHelper(asyncDelegateInfoHelper)
            .ambianceBytes(ambiance.toByteArray())
            .taskName("taskName")
            .taskId("taskId")
            .build();
    doReturn(Optional.of(StepDelegateInfo.builder().build()))
        .when(asyncDelegateInfoHelper)
        .getDelegateInformationForGivenTask(any(), any(), any());
    asyncDelegateResumeCallback.notify(new HashMap<>());
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notify(null);
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notify(responseWrong);
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notify(response);
    verify(sdkGraphVisualizationDataService, times(1)).publishStepDetailInformation(any(), any(), any());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testNotifyError() {
    AsyncDelegateResumeCallback asyncDelegateResumeCallback =
        AsyncDelegateResumeCallback.builder()
            .sdkGraphVisualizationDataService(sdkGraphVisualizationDataService)
            .asyncDelegateInfoHelper(asyncDelegateInfoHelper)
            .ambianceBytes(ambiance.toByteArray())
            .taskName("taskName")
            .taskId("taskId")
            .build();
    doReturn(Optional.of(StepDelegateInfo.builder().build()))
        .when(asyncDelegateInfoHelper)
        .getDelegateInformationForGivenTask(any(), any(), any());
    asyncDelegateResumeCallback.notifyError(new HashMap<>());
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notifyError(null);
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notifyError(responseWrong);
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notifyError(response);
    verify(sdkGraphVisualizationDataService, times(1)).publishStepDetailInformation(any(), any(), any());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testNotifyTimeout() {
    AsyncDelegateResumeCallback asyncDelegateResumeCallback =
        AsyncDelegateResumeCallback.builder()
            .sdkGraphVisualizationDataService(sdkGraphVisualizationDataService)
            .asyncDelegateInfoHelper(asyncDelegateInfoHelper)
            .ambianceBytes(ambiance.toByteArray())
            .taskName("taskName")
            .taskId("taskId")
            .build();
    doReturn(Optional.of(StepDelegateInfo.builder().build()))
        .when(asyncDelegateInfoHelper)
        .getDelegateInformationForGivenTask(any(), any(), any());
    asyncDelegateResumeCallback.notifyTimeout(new HashMap<>());
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notifyTimeout(null);
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notifyTimeout(responseWrong);
    verify(sdkGraphVisualizationDataService, times(0)).publishStepDetailInformation(any(), any(), any());
    asyncDelegateResumeCallback.notifyTimeout(response);
    verify(sdkGraphVisualizationDataService, times(1)).publishStepDetailInformation(any(), any(), any());
  }
}
