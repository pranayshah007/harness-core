/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

import static io.harness.rule.OwnerRule.AKHIL_PANDEY;
import static io.harness.rule.OwnerRule.ARVIND;

import static software.wings.beans.command.EcsResizeParams.EcsResizeParamsBuilder.anEcsResizeParams;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.CLUSTER_NAME;
import static software.wings.utils.WingsTestConstants.SERVICE_NAME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TimeoutException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceData;
import software.wings.beans.command.EcsResizeParams;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsServiceDeployRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsServiceDeployResponse;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsDeployCommandHandlerTest extends WingsBaseTest {
  @Mock private EcsDeployCommandTaskHelper mockEcsDeployCommandTaskHelper;
  @Mock private AwsClusterService mockAwsClusterService;
  @InjectMocks @Inject private EcsDeployCommandHandler handler;

  @Test
  @Owner(developers = AKHIL_PANDEY)
  @Category(UnitTests.class)
  public void testGetECSMaxDesiredRollbackCount() {
    EcsCommandRequest ecsCommandRequest = mock(EcsCommandRequest.class);
    EncryptedDataDetail encryptedDataDetail = mock(EncryptedDataDetail.class);
    ContainerServiceData oldContainerServiceData = mock(ContainerServiceData.class);
    ContainerServiceData newContainerServiceData = mock(ContainerServiceData.class);
    EcsResizeParams ecsResizeParams = mock(EcsResizeParams.class);

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    List<ContainerServiceData> newInstanceDataList = new ArrayList<>();
    List<ContainerServiceData> oldInstanceDataList = new ArrayList<>();
    encryptedDataDetails.add(encryptedDataDetail);
    newInstanceDataList.add(newContainerServiceData);
    oldInstanceDataList.add(oldContainerServiceData);
    try {
      int value = handler.getECSMaxDesiredRollbackCount(
          ecsCommandRequest, encryptedDataDetails, ecsResizeParams, newInstanceDataList, oldInstanceDataList);
    } catch (Exception e) {
      assertThat(e.getClass().equals(InvalidRequestException.class));
    }
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());

    EcsServiceDeployResponse ecsServiceDeployResponse = EcsServiceDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .output(StringUtils.EMPTY)
                                                            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest = new EcsCommandRequest(null, null, null, null, null, null, null, null, false);
    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getErrorMessage()).isEqualTo("Invalid request Type, expected EcsServiceDeployRequest");
    assertThat(ecsServiceDeployResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(ecsServiceDeployResponse.getOutput())
        .isEqualTo("Invalid request Type, expected EcsServiceDeployRequest");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskRollback() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());

    EcsServiceDeployResponse ecsServiceDeployResponse = EcsServiceDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .output(StringUtils.EMPTY)
                                                            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest =
        EcsServiceDeployRequest.builder()
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .cluster(CLUSTER_NAME)
            .ecsResizeParams(anEcsResizeParams()
                                 .withRollback(true)
                                 .withRollbackAllPhases(true)
                                 .withNewInstanceData(Collections.singletonList(ContainerServiceData.builder().build()))
                                 .withOldInstanceData(Collections.singletonList(ContainerServiceData.builder().build()))
                                 .build())
            .build();

    doReturn(true).when(mockEcsDeployCommandTaskHelper).getDeployingToHundredPercent(any());

    doReturn(new LinkedHashMap<String, Integer>() {
      { put(SERVICE_NAME, 3); }
    })
        .when(mockEcsDeployCommandTaskHelper)
        .listOfStringArrayToMap(any());
    doReturn(new LinkedHashMap<String, Integer>() {
      { put(SERVICE_NAME, 2); }
    })
        .when(mockEcsDeployCommandTaskHelper)
        .getActiveServiceCounts(any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);

    verify(mockAwsClusterService, times(2))
        .resizeCluster(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    verify(mockEcsDeployCommandTaskHelper, times(2)).restoreAutoScalarConfigs(any(), any(), any());
    verify(mockEcsDeployCommandTaskHelper, times(2)).createAutoScalarConfigIfServiceReachedMaxSize(any(), any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskNoRollback() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());

    EcsServiceDeployResponse ecsServiceDeployResponse = EcsServiceDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .output(StringUtils.EMPTY)
                                                            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest = EcsServiceDeployRequest.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .cluster(CLUSTER_NAME)
                                              .ecsResizeParams(anEcsResizeParams().withRollback(false).build())
                                              .build();

    doReturn(true).when(mockEcsDeployCommandTaskHelper).getDeployingToHundredPercent(any());
    doReturn(ContainerServiceData.builder().build())
        .when(mockEcsDeployCommandTaskHelper)
        .getNewInstanceData(any(), any());
    doReturn(Collections.singletonList(ContainerServiceData.builder().build()))
        .when(mockEcsDeployCommandTaskHelper)
        .getOldInstanceData(any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);
    assertThat(response.getEcsCommandResponse().isTimeoutFailure()).isFalse();
    verify(mockAwsClusterService, times(2))
        .resizeCluster(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    verify(mockEcsDeployCommandTaskHelper, times(2)).deregisterAutoScalarsIfExists(any(), any());
    verify(mockEcsDeployCommandTaskHelper, times(2)).createAutoScalarConfigIfServiceReachedMaxSize(any(), any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTask_NoRollback_TimeoutFailure() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(any());

    EcsServiceDeployResponse ecsServiceDeployResponse = EcsServiceDeployResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .output(StringUtils.EMPTY)
                                                            .build();
    doReturn(ecsServiceDeployResponse).when(mockEcsDeployCommandTaskHelper).getEmptyEcsServiceDeployResponse();

    EcsCommandRequest ecsCommandRequest = EcsServiceDeployRequest.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .cluster(CLUSTER_NAME)
                                              .ecsResizeParams(anEcsResizeParams().withRollback(false).build())
                                              .timeoutErrorSupported(true)
                                              .build();

    doReturn(true).when(mockEcsDeployCommandTaskHelper).getDeployingToHundredPercent(any());
    doReturn(ContainerServiceData.builder().build())
        .when(mockEcsDeployCommandTaskHelper)
        .getNewInstanceData(any(), any());
    doReturn(Collections.singletonList(ContainerServiceData.builder().build()))
        .when(mockEcsDeployCommandTaskHelper)
        .getOldInstanceData(any(), any());
    doThrow(new TimeoutException("", "", null))
        .when(mockAwsClusterService)
        .resizeCluster(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), any(), anyBoolean());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);
    assertThat(response.getEcsCommandResponse().isTimeoutFailure()).isTrue();

    verify(mockAwsClusterService)
        .resizeCluster(any(), any(), any(), any(), any(), anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    verify(mockEcsDeployCommandTaskHelper, times(1)).deregisterAutoScalarsIfExists(any(), any());
  }
}
