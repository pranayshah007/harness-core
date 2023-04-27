/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.cf;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.cf.PcfCommandTaskHandler;
import io.harness.delegate.cf.PcfDeployCommandTaskHandler;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfCommandTaskParameters;
import io.harness.delegate.task.pcf.request.CfRunPluginCommandRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class PcfCommandTaskTest extends CategoryTest {
  @Mock PcfDelegateTaskHelper pcfDelegateTaskHelper;
  @InjectMocks
  private final io.harness.delegate.task.cf.PcfCommandTask pcfTask =
      new PcfCommandTask(DelegateTaskPackage.builder()
                             .delegateId("delegateId")
                             .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                             .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(pcfTask).set("pcfDelegateTaskHelper", pcfDelegateTaskHelper);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testRun() {
    CfCommandDeployRequest deployRequest = CfCommandDeployRequest.builder().build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    CfCommandTaskParameters taskParameters = CfCommandTaskParameters.builder()
                                                 .pcfCommandRequest(deployRequest)
                                                 .encryptedDataDetails(encryptedDataDetails)
                                                 .build();
    pcfTask.run(new Object[] {taskParameters});
    verify(pcfDelegateTaskHelper, times(1))
        .getPcfCommandExecutionResponse(
            eq(deployRequest), eq(encryptedDataDetails), eq(false), eq(pcfTask.getLogStreamingTaskClient()));

    reset(pcfDelegateTaskHelper);
    pcfTask.run(new Object[] {deployRequest, encryptedDataDetails});
    verify(pcfDelegateTaskHelper, times(1))
        .getPcfCommandExecutionResponse(
            eq(deployRequest), eq(encryptedDataDetails), eq(false), eq(pcfTask.getLogStreamingTaskClient()));

    reset(pcfDelegateTaskHelper);
    CfRunPluginCommandRequest pluginCommandRequest =
        CfRunPluginCommandRequest.builder().encryptedDataDetails(encryptedDataDetails).build();
    pcfTask.run(pluginCommandRequest);
    verify(pcfDelegateTaskHelper, times(1))
        .getPcfCommandExecutionResponse(
            eq(pluginCommandRequest), eq(encryptedDataDetails), eq(false), eq(pcfTask.getLogStreamingTaskClient()));

    reset(pcfDelegateTaskHelper);
    assertThatThrownBy(() -> pcfTask.run(K8sApplyRequest.builder().build()))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDelegateTaskHelper() {
    CfCommandDeployRequest pcfCommandRequest =
        CfCommandDeployRequest.builder().pcfCommandType(CfCommandRequest.PcfCommandType.RESIZE).build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    Map<String, PcfCommandTaskHandler> commandTaskTypeToTaskHandlerMap = new HashMap<>();
    PcfDeployCommandTaskHandler mockHandler = mock(PcfDeployCommandTaskHandler.class);
    commandTaskTypeToTaskHandlerMap.put(CfCommandRequest.PcfCommandType.RESIZE.name(), mockHandler);

    PcfDelegateTaskHelper delegateTaskHelper = new PcfDelegateTaskHelper();
    on(delegateTaskHelper).set("commandTaskTypeToTaskHandlerMap", commandTaskTypeToTaskHandlerMap);
    delegateTaskHelper.getPcfCommandExecutionResponse(pcfCommandRequest, encryptedDataDetails, false, null);
    verify(mockHandler, times(1))
        .executeTask(
            eq(pcfCommandRequest), eq(encryptedDataDetails), eq(false), eq(pcfTask.getLogStreamingTaskClient()));

    doAnswer(invocation -> { throw new Exception(); })
        .when(mockHandler)
        .executeTask(
            eq(pcfCommandRequest), eq(encryptedDataDetails), eq(false), eq(pcfTask.getLogStreamingTaskClient()));
    CfCommandExecutionResponse cfCommandExecutionResponse = delegateTaskHelper.getPcfCommandExecutionResponse(
        pcfCommandRequest, encryptedDataDetails, eq(false), eq(pcfTask.getLogStreamingTaskClient()));
    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }
}
