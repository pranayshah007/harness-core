/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ACASIAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.shell.ssh.CommandHandler;
import io.harness.delegate.task.shell.ssh.SshCleanupCommandHandler;
import io.harness.delegate.task.shell.ssh.SshCopyCommandHandler;
import io.harness.delegate.task.shell.ssh.SshInitCommandHandler;
import io.harness.delegate.task.shell.ssh.SshScriptCommandHandler;
import io.harness.delegate.task.ssh.NGCommandUnitType;
import io.harness.delegate.task.ssh.NgCleanupCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgInitCommandUnit;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CommandTaskNGTest extends CategoryTest {
  final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).build();
  @Mock BooleanSupplier preExecute;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;

  @Mock SshInitCommandHandler sshInitCommandHandler;
  @Mock SshCleanupCommandHandler sshCleanupCommandHandler;
  @Mock SshScriptCommandHandler sshScriptCommandHandler;
  @Mock SshCopyCommandHandler sshCopyCommandHandler;
  @Mock Map<String, CommandHandler> commandHandlers;

  final NgCommandUnit initCommandUnit = NgInitCommandUnit.builder().build();
  final NgCommandUnit scriptCommandUnit = ScriptCommandUnit.builder().build();
  final NgCommandUnit cleanupCommandUnit = NgCleanupCommandUnit.builder().build();

  @Inject
  @InjectMocks
  CommandTaskNG task = new CommandTaskNG(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(sshInitCommandHandler).when(commandHandlers).get(NGCommandUnitType.INIT);
    doReturn(sshScriptCommandHandler).when(commandHandlers).get(NGCommandUnitType.SCRIPT);
    doReturn(sshCopyCommandHandler).when(commandHandlers).get(NGCommandUnitType.COPY);
    doReturn(sshCleanupCommandHandler).when(commandHandlers).get(NGCommandUnitType.CLEANUP);
  }

  @Test(expected = NotImplementedException.class)
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testShouldAcceptOnlyTaskParams() {
    task.run(new Object[] {});
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecuteSshCommandTaskOnHostSuccess() {
    SshCommandTaskParameters taskParameters =
        SshCommandTaskParameters.builder()
            .commandUnits(Arrays.asList(initCommandUnit, scriptCommandUnit, cleanupCommandUnit))
            .executeOnDelegate(false)
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder()
                                        .sshKeySpecDto(SSHKeySpecDTO.builder().build())
                                        .encryptionDataDetails(Collections.emptyList())
                                        .hosts(Arrays.asList("host1"))
                                        .build())
            .accountId("accountId")
            .executionId("executionId")
            .host("host1")
            .build();

    doReturn(CommandExecutionStatus.SUCCESS)
        .when(sshInitCommandHandler)
        .handle(eq(taskParameters), eq(initCommandUnit), eq(logStreamingTaskClient), any());
    doReturn(CommandExecutionStatus.SUCCESS)
        .when(sshScriptCommandHandler)
        .handle(eq(taskParameters), eq(scriptCommandUnit), eq(logStreamingTaskClient), any());
    doReturn(CommandExecutionStatus.SUCCESS)
        .when(sshCleanupCommandHandler)
        .handle(eq(taskParameters), eq(cleanupCommandUnit), eq(logStreamingTaskClient), any());

    DelegateResponseData responseData = task.run(taskParameters);
    assertThat(responseData).isInstanceOf(CommandTaskResponse.class);
    CommandTaskResponse commandTaskResponse = (CommandTaskResponse) responseData;
    assertThat(commandTaskResponse.getStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(sshInitCommandHandler, times(1))
        .handle(eq(taskParameters), eq(initCommandUnit), eq(logStreamingTaskClient), any());
    verify(sshScriptCommandHandler, times(1))
        .handle(eq(taskParameters), eq(scriptCommandUnit), eq(logStreamingTaskClient), any());
    verify(sshCopyCommandHandler, times(0)).handle(eq(taskParameters), any(), eq(logStreamingTaskClient), any());
    verify(sshCleanupCommandHandler, times(1))
        .handle(eq(taskParameters), eq(cleanupCommandUnit), eq(logStreamingTaskClient), any());
  }

  @Test
  @Owner(developers = ACASIAN)
  @Category(UnitTests.class)
  public void testExecuteSshCommandTaskOnFailureExecutingScriptCommandUnit() {
    SshCommandTaskParameters taskParameters =
        SshCommandTaskParameters.builder()
            .commandUnits(Arrays.asList(initCommandUnit, scriptCommandUnit, cleanupCommandUnit))
            .executeOnDelegate(false)
            .sshInfraDelegateConfig(PdcSshInfraDelegateConfig.builder()
                                        .sshKeySpecDto(SSHKeySpecDTO.builder().build())
                                        .encryptionDataDetails(Collections.emptyList())
                                        .hosts(Arrays.asList("host1"))
                                        .build())
            .accountId("accountId")
            .executionId("executionId")
            .host("host1")
            .build();

    doReturn(CommandExecutionStatus.SUCCESS)
        .when(sshInitCommandHandler)
        .handle(eq(taskParameters), any(NgCommandUnit.class), eq(logStreamingTaskClient), any());
    lenient()
        .doThrow(new RuntimeException("failed to execute script"))
        .when(sshScriptCommandHandler)
        .handle(eq(taskParameters), any(NgCommandUnit.class), eq(logStreamingTaskClient), any());

    assertThatThrownBy(() -> task.run(taskParameters)).isInstanceOf(TaskNGDataException.class);

    verify(sshInitCommandHandler, times(1))
        .handle(eq(taskParameters), eq(initCommandUnit), eq(logStreamingTaskClient), any());
    verify(sshScriptCommandHandler, times(1))
        .handle(eq(taskParameters), eq(scriptCommandUnit), eq(logStreamingTaskClient), any());
    verify(sshCopyCommandHandler, times(0)).handle(eq(taskParameters), any(), eq(logStreamingTaskClient), any());
    verify(sshCleanupCommandHandler, times(0))
        .handle(eq(taskParameters), eq(cleanupCommandUnit), eq(logStreamingTaskClient), any());
  }
}