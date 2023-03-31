/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws.ecs.ecstaskhandler.deploy;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.ContainerServiceData;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.cloudprovider.aws.AwsClusterService;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.helpers.ext.ecs.request.EcsCommandRequest;
import software.wings.helpers.ext.ecs.request.EcsDeployRollbackDataFetchRequest;
import software.wings.helpers.ext.ecs.response.EcsCommandExecutionResponse;
import software.wings.helpers.ext.ecs.response.EcsDeployRollbackDataFetchResponse;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.LinkedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class EcsDeployRollbackDataFetchCommandHandlerTest extends WingsBaseTest {
  @Mock private EcsDeployCommandTaskHelper mockEcsDeployCommandTaskHelper;
  @Mock private AwsClusterService mockAwsClusterService;
  @Mock private DelegateFileManager mockDelegateFileManager;
  @Mock private EncryptionService mockEncryptionService;
  @Mock private DelegateLogService mockDelegateLogService;
  @InjectMocks @Inject private EcsDeployRollbackDataFetchCommandHandler handler;

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());

    EcsDeployRollbackDataFetchResponse ecsDeployRollbackDataFetchResponse =
        EcsDeployRollbackDataFetchResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .output(StringUtils.EMPTY)
            .build();
    doReturn(ecsDeployRollbackDataFetchResponse)
        .when(mockEcsDeployCommandTaskHelper)
        .getEmptyEcsDeployRollbackDataFetchResponse();

    EcsCommandRequest ecsCommandRequest = new EcsCommandRequest(null, null, null, null, null, null, null, null, false);
    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);
    assertThat(response).isNotNull();
    assertThat(response.getErrorMessage()).isEqualTo("Invalid request Type, expected EcsDeployRollbackFetchRequest");
    assertThat(ecsDeployRollbackDataFetchResponse.getCommandExecutionStatus())
        .isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(ecsDeployRollbackDataFetchResponse.getOutput())
        .isEqualTo("Invalid request Type, expected EcsDeployRollbackFetchRequest");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskRollback() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());

    EcsDeployRollbackDataFetchResponse ecsDeployRollbackDataFetchResponse =
        EcsDeployRollbackDataFetchResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .output(StringUtils.EMPTY)
            .build();
    doReturn(ecsDeployRollbackDataFetchResponse)
        .when(mockEcsDeployCommandTaskHelper)
        .getEmptyEcsDeployRollbackDataFetchResponse();

    EcsCommandRequest ecsCommandRequest =
        EcsDeployRollbackDataFetchRequest.builder()
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

    verify(mockAwsClusterService, times(0))
        .resizeCluster(
            anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    verify(mockEcsDeployCommandTaskHelper, times(0)).restoreAutoScalarConfigs(any(), any(), any());
    verify(mockEcsDeployCommandTaskHelper, times(0)).createAutoScalarConfigIfServiceReachedMaxSize(any(), any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testExecuteTaskNoRollback() {
    ExecutionLogCallback mockCallback = mock(ExecutionLogCallback.class);
    doNothing().when(mockCallback).saveExecutionLog(anyString());

    EcsDeployRollbackDataFetchResponse ecsDeployRollbackDataFetchResponse =
        EcsDeployRollbackDataFetchResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .output(StringUtils.EMPTY)
            .build();
    doReturn(ecsDeployRollbackDataFetchResponse)
        .when(mockEcsDeployCommandTaskHelper)
        .getEmptyEcsDeployRollbackDataFetchResponse();

    EcsCommandRequest ecsCommandRequest = EcsDeployRollbackDataFetchRequest.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .cluster(CLUSTER_NAME)
                                              .ecsResizeParams(anEcsResizeParams().withRollback(false).build())
                                              .build();

    doReturn(true).when(mockEcsDeployCommandTaskHelper).getDeployingToHundredPercent(any());
    ContainerServiceData newInstanceData = ContainerServiceData.builder().desiredCount(12).previousCount(5).build();
    doReturn(newInstanceData).when(mockEcsDeployCommandTaskHelper).getNewInstanceData(any(), any());
    ContainerServiceData oldInstanceData = ContainerServiceData.builder().desiredCount(5).previousCount(12).build();
    doReturn(Collections.singletonList(oldInstanceData))
        .when(mockEcsDeployCommandTaskHelper)
        .getOldInstanceData(any(), any());

    EcsCommandExecutionResponse response = handler.executeTaskInternal(ecsCommandRequest, null, mockCallback);
    EcsDeployRollbackDataFetchResponse ecsCommandResponse =
        (EcsDeployRollbackDataFetchResponse) response.getEcsCommandResponse();
    assertThat(ecsCommandResponse.getNewInstanceData()).containsExactly(newInstanceData);
    assertThat(ecsCommandResponse.getOldInstanceData()).containsExactly(oldInstanceData);

    verify(mockAwsClusterService, times(0))
        .resizeCluster(
            anyString(), any(), any(), anyString(), anyString(), anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    verify(mockEcsDeployCommandTaskHelper, times(0)).deregisterAutoScalarsIfExists(any(), any());
    verify(mockEcsDeployCommandTaskHelper, times(0)).createAutoScalarConfigIfServiceReachedMaxSize(any(), any(), any());
  }
}
