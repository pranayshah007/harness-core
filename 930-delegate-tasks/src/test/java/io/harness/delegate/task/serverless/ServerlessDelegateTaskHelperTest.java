/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.serverless.ServerlessAwsLambdaDeployCommandTaskHandler;
import io.harness.delegate.serverless.ServerlessCommandTaskHandler;
import io.harness.delegate.task.serverless.request.ServerlessCommandRequest;
import io.harness.delegate.task.serverless.request.ServerlessDeployRequest;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.delegate.task.serverless.response.ServerlessDeployResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class ServerlessDelegateTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ServerlessInfraConfigHelper serverlessInfraConfigHelper;
  @Mock Map<String, ServerlessCommandTaskHandler> serverlessCommandTaskHandlerMap;
  @Mock ServerlessAwsLambdaDeployCommandTaskHandler serverlessAwsLambdaDeployCommandTaskHandler;
  @InjectMocks ServerlessDelegateTaskHelper serverlessDelegateTaskHelper;
  @Mock ILogStreamingTaskClient iLogStreamingTaskClient;

  private static final String WORKING_DIR_BASE = "./repository/serverless/";

  LinkedHashMap<String, CommandUnitProgress> commandUnitProgressLinkedHashMap = new LinkedHashMap<>();
  ServerlessInfraConfig serverlessInfraConfig = ServerlessAwsLambdaInfraConfig.builder().build();

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getServerlessCommandResponseTest() throws Exception {
    CommandUnitProgress commandUnitProgress =
        CommandUnitProgress.builder().startTime(1).endTime(2).status(CommandExecutionStatus.SUCCESS).build();
    commandUnitProgressLinkedHashMap.put("safsdfasd", commandUnitProgress);
    CommandUnitsProgress commandUnitsProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitProgressLinkedHashMap).build();
    ServerlessCommandRequest serverlessCommandRequest =
        ServerlessDeployRequest.builder()
            .serverlessInfraConfig(serverlessInfraConfig)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_DEPLOY)
            .commandUnitsProgress(commandUnitsProgress)
            .build();
    doReturn(serverlessAwsLambdaDeployCommandTaskHandler)
        .when(serverlessCommandTaskHandlerMap)
        .get(serverlessCommandRequest.getServerlessCommandType().name());
    ServerlessCommandResponse serverlessCommandResponse = ServerlessDeployResponse.builder().build();
    doReturn(serverlessCommandResponse)
        .when(serverlessAwsLambdaDeployCommandTaskHandler)
        .executeTask(eq(serverlessCommandRequest), any(), eq(iLogStreamingTaskClient), eq(commandUnitsProgress));
    assertThat(
        serverlessDelegateTaskHelper.getServerlessCommandResponse(serverlessCommandRequest, iLogStreamingTaskClient))
        .isEqualTo(serverlessCommandResponse);
  }

  @Test(expected = TaskNGDataException.class)
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void getServerlessCommandResponseExceptionTest() throws Exception {
    CommandUnitProgress commandUnitProgress =
        CommandUnitProgress.builder().startTime(1).endTime(2).status(CommandExecutionStatus.SUCCESS).build();
    CommandUnitsProgress commandUnitsProgress =
        CommandUnitsProgress.builder().commandUnitProgressMap(commandUnitProgressLinkedHashMap).build();
    ServerlessCommandRequest serverlessCommandRequest =
        ServerlessDeployRequest.builder()
            .serverlessInfraConfig(serverlessInfraConfig)
            .serverlessCommandType(ServerlessCommandType.SERVERLESS_AWS_LAMBDA_DEPLOY)
            .commandUnitsProgress(commandUnitsProgress)
            .build();
    ServerlessCommandResponse serverlessCommandResponse = ServerlessDeployResponse.builder().build();
    serverlessDelegateTaskHelper.getServerlessCommandResponse(serverlessCommandRequest, iLogStreamingTaskClient);
  }
}