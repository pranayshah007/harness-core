/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.pcf;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.cf.PcfCommandTaskHandler;
import io.harness.delegate.task.cf.PcfDelegateTaskHelper;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.delegatetasks.pcf.pcftaskhandler.PcfSetupCommandTaskHandler;
import software.wings.helpers.ext.pcf.request.CfCommandSetupRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class PcfDelegateTaskHelperTest extends CategoryTest {
  @Spy private final Map<String, PcfCommandTaskHandler> commandTaskTypeToTaskHandlerMap = new HashMap<>();
  @Mock private PcfSetupCommandTaskHandler pcfSetupCommandTaskHandler;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;

  @InjectMocks private PcfDelegateTaskHelper pcfDelegateTaskHelper;

  @Before
  public void before() {
    commandTaskTypeToTaskHandlerMap.put(CfCommandRequest.PcfCommandType.SETUP.name(), pcfSetupCommandTaskHandler);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPcfCommandExecutionResponse() {
    CfCommandSetupRequest setupRequest =
        CfCommandSetupRequest.builder().pcfCommandType(CfCommandRequest.PcfCommandType.SETUP).build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();

    doReturn(CfCommandExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build())
        .when(pcfSetupCommandTaskHandler)
        .executeTask(eq(setupRequest), eq(encryptedDataDetails), eq(false), eq(logStreamingTaskClient));

    CfCommandExecutionResponse pcfCommandExecutionResponse = pcfDelegateTaskHelper.getPcfCommandExecutionResponse(
        setupRequest, encryptedDataDetails, false, logStreamingTaskClient);

    assertThat(pcfCommandExecutionResponse).isNotNull();
    assertThat(pcfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPcfCommandExecutionResponseWithException() {
    CfCommandSetupRequest setupRequest =
        CfCommandSetupRequest.builder().pcfCommandType(CfCommandRequest.PcfCommandType.SETUP).build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    String errorMsg = "Error msg";

    doThrow(new RuntimeException(errorMsg))
        .when(pcfSetupCommandTaskHandler)
        .executeTask(eq(setupRequest), eq(encryptedDataDetails), eq(false), eq(logStreamingTaskClient));

    CfCommandExecutionResponse pcfCommandExecutionResponse = pcfDelegateTaskHelper.getPcfCommandExecutionResponse(
        setupRequest, encryptedDataDetails, false, logStreamingTaskClient);

    assertThat(pcfCommandExecutionResponse).isNotNull();
    assertThat(pcfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(pcfCommandExecutionResponse.getErrorMessage()).isEqualTo("RuntimeException: Error msg");
  }
}
