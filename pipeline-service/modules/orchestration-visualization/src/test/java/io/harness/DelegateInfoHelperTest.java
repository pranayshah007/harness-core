/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.client.DelegateSelectionLogHttpClient;
import io.harness.delegate.beans.DelegateSelectionLogParams;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.SyncExecutableResponse;
import io.harness.pms.contracts.execution.TaskChainExecutableResponse;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.PIPELINE)
public class DelegateInfoHelperTest extends OrchestrationVisualizationTestBase {
  @Mock DelegateSelectionLogHttpClient delegateSelectionLogHttpClient;
  @InjectMocks DelegateInfoHelper delegateInfoHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getDelegateInfoForGivenTask() throws IOException {
    Call<RestResponse<DelegateSelectionLogParams>> getConnectorResourceCall = mock(Call.class);
    RestResponse<DelegateSelectionLogParams> responseDTO =
        new RestResponse<>(DelegateSelectionLogParams.builder().build());

    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(delegateSelectionLogHttpClient.getDelegateInfo(any(), any())).thenReturn(getConnectorResourceCall);

    List<ExecutableResponse> executableResponses = new ArrayList<>();
    executableResponses.add(ExecutableResponse.newBuilder()
                                .setTask(TaskExecutableResponse.newBuilder().setTaskId(generateUuid()).build())
                                .build());
    executableResponses.add(
        ExecutableResponse.newBuilder()
            .setTaskChain(TaskChainExecutableResponse.newBuilder().setTaskId(generateUuid()).build())
            .build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setSync(SyncExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().build()).build());

    delegateInfoHelper.getDelegateInformationForGivenTask(executableResponses, ExecutionMode.TASK, "accountId");
    verify(delegateSelectionLogHttpClient, times(2)).getDelegateInfo(any(), any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getDelegateInfoForGivenTaskChain() throws IOException {
    Call<RestResponse<DelegateSelectionLogParams>> getConnectorResourceCall = mock(Call.class);
    RestResponse<DelegateSelectionLogParams> responseDTO =
        new RestResponse<>(DelegateSelectionLogParams.builder().build());

    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(delegateSelectionLogHttpClient.getDelegateInfo(any(), any())).thenReturn(getConnectorResourceCall);

    List<ExecutableResponse> executableResponses = new ArrayList<>();
    executableResponses.add(ExecutableResponse.newBuilder()
                                .setTask(TaskExecutableResponse.newBuilder().setTaskId(generateUuid()).build())
                                .build());
    executableResponses.add(
        ExecutableResponse.newBuilder()
            .setTaskChain(TaskChainExecutableResponse.newBuilder().setTaskId(generateUuid()).build())
            .build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setSync(SyncExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().build()).build());

    delegateInfoHelper.getDelegateInformationForGivenTask(executableResponses, ExecutionMode.TASK_CHAIN, "accountId");
    verify(delegateSelectionLogHttpClient, times(2)).getDelegateInfo(any(), any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getDelegateInfoForGivenAsync() throws IOException {
    Call<RestResponse<DelegateSelectionLogParams>> getConnectorResourceCall = mock(Call.class);
    RestResponse<DelegateSelectionLogParams> responseDTO =
        new RestResponse<>(DelegateSelectionLogParams.builder().build());

    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    when(delegateSelectionLogHttpClient.getDelegateInfo(any(), any())).thenReturn(getConnectorResourceCall);

    List<ExecutableResponse> executableResponses = new ArrayList<>();
    executableResponses.add(
        ExecutableResponse.newBuilder().setTask(TaskExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setTaskChain(TaskChainExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setSync(SyncExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().build()).build());

    delegateInfoHelper.getDelegateInformationForGivenTask(executableResponses, ExecutionMode.ASYNC, "accountId");
    verify(delegateSelectionLogHttpClient, times(0)).getDelegateInfo(any(), any());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void getDelegateInfoForGivenSync() throws IOException {
    Call<RestResponse<DelegateSelectionLogParams>> getConnectorResourceCall = mock(Call.class);
    RestResponse<DelegateSelectionLogParams> responseDTO =
        new RestResponse<>(DelegateSelectionLogParams.builder().build());

    when(getConnectorResourceCall.execute()).thenReturn(Response.success(responseDTO));

    List<ExecutableResponse> executableResponses = new ArrayList<>();
    executableResponses.add(
        ExecutableResponse.newBuilder().setTask(TaskExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setTaskChain(TaskChainExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setSync(SyncExecutableResponse.newBuilder().build()).build());
    executableResponses.add(
        ExecutableResponse.newBuilder().setAsync(AsyncExecutableResponse.newBuilder().build()).build());

    delegateInfoHelper.getDelegateInformationForGivenTask(executableResponses, ExecutionMode.SYNC, "accountId");
    verify(delegateSelectionLogHttpClient, times(0)).getDelegateInfo(any(), any());
  }
}
