/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.perpetualtask.CustomDeploymentInstanceSyncPerpetualTaskExecuter.OUTPUT_PATH_KEY;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.SOURABH;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.DelegateTestBase;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.CustomDeploymentServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.CustomDeploymentInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.task.shell.ShellExecutorFactoryNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.CustomDeploymentNGInstanceSyncPerpetualTaskParams;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutionData;

import com.google.protobuf.Any;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
public class CustomDeploymentInstanceSyncPerpetualTaskExecuterTest extends DelegateTestBase {
  @Mock private Call<RestResponse<Boolean>> mockCall;
  @Mock private DelegateAgentManagerClient mockDelegateAgentManagerClient;
  @Mock private ShellExecutorFactoryNG shellExecutorFactory;
  @InjectMocks private CustomDeploymentInstanceSyncPerpetualTaskExecuter executor;

  @Captor private ArgumentCaptor<CustomDeploymentInstanceSyncPerpetualTaskResponse> perpetualTaskResponseCaptor;

  private final String taskId = "task-id";
  private final String accountId = "accountId";
  private final String output = "{\n"
      + "  \"items\": [\n"
      + "    {\n"
      + "      \"identity\": [\n"
      + "        \"servers\",\n"
      + "        \"myserver\"\n"
      + "      ],\n"
      + "      \"stagingMode\": \"nostage\",\n"
      + "      \"listenAddress\": \"4.4\"\n"
      + "    },\n"
      + "    {\n"
      + "      \"identity\": [\n"
      + "        \"servers\",\n"
      + "        \"server-1\"\n"
      + "      ],\n"
      + "      \"stagingMode\": \"external_stage\",\n"
      + "      \"listenAddress\": \"2.2\"\n"
      + "    },\n"
      + "    {\n"
      + "      \"identity\": [\n"
      + "        \"servers\",\n"
      + "        \"cluster-2-server1\"\n"
      + "      ],\n"
      + "      \"stagingMode\": \"stage\",\n"
      + "      \"listenAddress\": \"3.3\"\n"
      + "    },\n"
      + "    {\n"
      + "      \"identity\": [\n"
      + "        \"servers\",\n"
      + "        \"cluster-2-server2\"\n"
      + "      ],\n"
      + "      \"stagingMode\": \"stage\",\n"
      + "      \"listenAddress\": \"1.1\"\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  @Before
  public void setUp() throws IOException {
    when(mockDelegateAgentManagerClient.processInstanceSyncNGResult(
             anyString(), anyString(), perpetualTaskResponseCaptor.capture()))
        .thenReturn(mockCall);
    doReturn(retrofit2.Response.success("success")).when(mockCall).execute();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRunOnceSuccess() throws IOException {
    mockStatic(Files.class);
    when(Files.readAllBytes(any(Path.class))).thenReturn(output.getBytes());
    ExecuteCommandResponse commandResponse =
        ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build();

    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    doReturn(scriptProcessExecutor).when(shellExecutorFactory).getExecutor(any(), any(), any());
    doReturn(commandResponse).when(scriptProcessExecutor).executeCommandString(any(), any(), any(), any());

    ArgumentCaptor<CustomDeploymentInstanceSyncPerpetualTaskResponse> argumentCaptor =
        ArgumentCaptor.forClass(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);

    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(taskId).build();
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(perpetualTaskId, perpetualTaskParams, Instant.now());

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(executor.cleanup(perpetualTaskId, perpetualTaskParams)).isEqualTo(false);

    verify(mockDelegateAgentManagerClient)
        .processInstanceSyncNGResult(eq(taskId), eq(accountId), argumentCaptor.capture());
    CustomDeploymentInstanceSyncPerpetualTaskResponse argumentCaptorValue = argumentCaptor.getValue();
    assertThat(argumentCaptorValue).isInstanceOf(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);
    assertThat(argumentCaptorValue.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = argumentCaptorValue.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(4);
    List<String> instancesHostNames =
        serverInstanceDetails.stream()
            .map(server -> ((CustomDeploymentServerInstanceInfo) server).getInstanceName())
            .collect(Collectors.toList());
    assertThat(instancesHostNames).contains("1.1", "2.2", "3.3", "4.4");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRunOnceFailure() throws IOException {
    mockStatic(Files.class);
    when(Files.readAllBytes(any(Path.class))).thenReturn(output.getBytes());
    ExecuteCommandResponse commandResponse =
        ExecuteCommandResponse.builder().status(CommandExecutionStatus.FAILURE).build();

    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    doReturn(scriptProcessExecutor).when(shellExecutorFactory).getExecutor(any(), any(), any());
    doReturn(commandResponse).when(scriptProcessExecutor).executeCommandString(any(), any(), any(), any());

    ArgumentCaptor<CustomDeploymentInstanceSyncPerpetualTaskResponse> argumentCaptor =
        ArgumentCaptor.forClass(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);

    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(taskId).build();
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(perpetualTaskId, perpetualTaskParams, Instant.now());

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(executor.cleanup(perpetualTaskId, perpetualTaskParams)).isEqualTo(false);

    verify(mockDelegateAgentManagerClient)
        .processInstanceSyncNGResult(eq(taskId), eq(accountId), argumentCaptor.capture());
    CustomDeploymentInstanceSyncPerpetualTaskResponse argumentCaptorValue = argumentCaptor.getValue();
    assertThat(argumentCaptorValue).isInstanceOf(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);
    assertThat(argumentCaptorValue.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = argumentCaptorValue.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRunOnceFailureOnReadingOutPutFile() throws IOException {
    MockedStatic<Files> filesMockedStatic = mockStatic(Files.class);
    filesMockedStatic.when(() -> Files.readAllBytes(any(Path.class))).thenThrow(IOException.class);

    ExecuteCommandResponse commandResponse =
        ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build();

    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    doReturn(scriptProcessExecutor).when(shellExecutorFactory).getExecutor(any(), any(), any());
    doReturn(commandResponse).when(scriptProcessExecutor).executeCommandString(any(), any(), any(), any());

    ArgumentCaptor<CustomDeploymentInstanceSyncPerpetualTaskResponse> argumentCaptor =
        ArgumentCaptor.forClass(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);

    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(taskId).build();
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(perpetualTaskId, perpetualTaskParams, Instant.now());
    assertThat(perpetualTaskResponse).isNotNull();
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(executor.cleanup(perpetualTaskId, perpetualTaskParams)).isEqualTo(false);
    verify(mockDelegateAgentManagerClient)
        .processInstanceSyncNGResult(eq(taskId), eq(accountId), argumentCaptor.capture());
    CustomDeploymentInstanceSyncPerpetualTaskResponse argumentCaptorValue = argumentCaptor.getValue();
    assertThat(argumentCaptorValue).isInstanceOf(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);
    assertThat(argumentCaptorValue.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = argumentCaptorValue.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testRunOnceFailureWithExecuteCommandFailure() {
    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    doReturn(scriptProcessExecutor).when(shellExecutorFactory).getExecutor(any(), any(), any());
    doThrow(new RuntimeException("Error occurred"))
        .when(scriptProcessExecutor)
        .executeCommandString(any(), any(), any(), any());

    ArgumentCaptor<CustomDeploymentInstanceSyncPerpetualTaskResponse> argumentCaptor =
        ArgumentCaptor.forClass(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);

    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(taskId).build();
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(perpetualTaskId, perpetualTaskParams, Instant.now());

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(executor.cleanup(perpetualTaskId, perpetualTaskParams)).isEqualTo(false);

    verify(mockDelegateAgentManagerClient)
        .processInstanceSyncNGResult(eq(taskId), eq(accountId), argumentCaptor.capture());
    CustomDeploymentInstanceSyncPerpetualTaskResponse argumentCaptorValue = argumentCaptor.getValue();
    assertThat(argumentCaptorValue).isInstanceOf(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);
    assertThat(argumentCaptorValue.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = argumentCaptorValue.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testRunOnceFailureWithNULLPointerException() {
    ExecuteCommandResponse commandResponse =
        ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build();

    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    doReturn(scriptProcessExecutor).when(shellExecutorFactory).getExecutor(any(), any(), any());
    doReturn(commandResponse).when(scriptProcessExecutor).executeCommandString(any(), any(), any(), any());

    ArgumentCaptor<CustomDeploymentInstanceSyncPerpetualTaskResponse> argumentCaptor =
        ArgumentCaptor.forClass(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);

    PerpetualTaskId perpetualTaskId = PerpetualTaskId.newBuilder().setId(taskId).build();
    PerpetualTaskExecutionParams perpetualTaskParams = getPerpetualTaskParams();
    PerpetualTaskResponse perpetualTaskResponse = executor.runOnce(perpetualTaskId, perpetualTaskParams, Instant.now());

    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage()).isEqualTo("success");
    assertThat(executor.cleanup(perpetualTaskId, perpetualTaskParams)).isEqualTo(false);

    verify(mockDelegateAgentManagerClient)
        .processInstanceSyncNGResult(eq(taskId), eq(accountId), argumentCaptor.capture());
    CustomDeploymentInstanceSyncPerpetualTaskResponse argumentCaptorValue = argumentCaptor.getValue();
    assertThat(argumentCaptorValue).isInstanceOf(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);
    assertThat(argumentCaptorValue.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    List<ServerInstanceInfo> serverInstanceDetails = argumentCaptorValue.getServerInstanceDetails();
    assertThat(serverInstanceDetails.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void processInstanceSyncResultFailure() {
    Map<String, String> outPutEnvVariables = new HashMap<>();
    outPutEnvVariables.put(OUTPUT_PATH_KEY, output);
    ShellExecutionData shellExecutionData =
        ShellExecutionData.builder().sweepingOutputEnvVariables(outPutEnvVariables).build();
    ExecuteCommandResponse commandResponse = ExecuteCommandResponse.builder()
                                                 .commandExecutionData(shellExecutionData)
                                                 .status(CommandExecutionStatus.SUCCESS)
                                                 .build();

    ScriptProcessExecutor scriptProcessExecutor = mock(ScriptProcessExecutor.class);
    doReturn(scriptProcessExecutor).when(shellExecutorFactory).getExecutor(any(), any(), any());
    doReturn(commandResponse).when(scriptProcessExecutor).executeCommandString(any(), any(), any(), any());

    ArgumentCaptor<CustomDeploymentInstanceSyncPerpetualTaskResponse> argumentCaptor =
        ArgumentCaptor.forClass(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);

    doThrow(new RuntimeException("Fail"))
        .when(mockDelegateAgentManagerClient)
        .processInstanceSyncNGResult(eq(taskId), eq(accountId), any());
    PerpetualTaskResponse perpetualTaskResponse =
        executor.runOnce(PerpetualTaskId.newBuilder().setId(taskId).build(), getPerpetualTaskParams(), Instant.now());

    verify(mockDelegateAgentManagerClient)
        .processInstanceSyncNGResult(eq(taskId), eq(accountId), argumentCaptor.capture());
    CustomDeploymentInstanceSyncPerpetualTaskResponse argumentCaptorValue = argumentCaptor.getValue();
    assertThat(argumentCaptorValue).isInstanceOf(CustomDeploymentInstanceSyncPerpetualTaskResponse.class);
    assertThat(perpetualTaskResponse.getResponseCode()).isEqualTo(SC_OK);
    assertThat(perpetualTaskResponse.getResponseMessage())
        .isEqualTo(
            "Failed to publish CustomDeployment instance sync result PerpetualTaskId [task-id], accountId [accountId]");
  }

  private PerpetualTaskExecutionParams getPerpetualTaskParams() {
    String script = "INSTANCE_OUTPUT_PATH=$(echo '{\n"
        + "  \"items\" : [\n"
        + "    {\n"
        + "      \"identity\" : [ \"servers\", \"myserver\" ],\n"
        + "      \"stagingMode\" : \"nostage\",\n"
        + "      \"listenAddress\" : \"3.3\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"identity\" : [ \"servers\", \"server-1\" ],\n"
        + "      \"stagingMode\" : \"external_stage\",\n"
        + "      \"listenAddress\" : \"2.2\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"identity\" : [ \"servers\", \"cluster-2-server1\" ],\n"
        + "      \"stagingMode\" : \"stage\",\n"
        + "      \"listenAddress\" : \"4.4\n"
        + "    },\n"
        + "    {\n"
        + "      \"identity\" : [ \"servers\", \"cluster-2-server2\" ],\n"
        + "      \"stagingMode\" : \"stage\",\n"
        + "      \"listenAddress\" : \"1.1\"\n"
        + "    }\n"
        + "  ]\n"
        + "}')";

    String instancesListPath = "items";
    Map<String, String> attributes = new HashMap<>();
    attributes.put("instancename", "listenAddress");
    CustomDeploymentNGInstanceSyncPerpetualTaskParams taskParams =
        CustomDeploymentNGInstanceSyncPerpetualTaskParams.newBuilder()
            .setAccountId("accountId")
            .setScript(script)
            .setOutputPathKey("")
            .setInstancesListPath(instancesListPath)
            .setInfrastructureKey("infra1")
            .putAllInstanceAttributes(attributes)
            .build();
    return PerpetualTaskExecutionParams.newBuilder().setCustomizedParams(Any.pack(taskParams)).build();
  }
}
