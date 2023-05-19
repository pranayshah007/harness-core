/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.shell.ssh.CommandHandler.RESOLVED_ENV_VARIABLES_KEY;
import static io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType.ARTIFACTORY;
import static io.harness.rule.OwnerRule.BOJAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.SshCommandTaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgDownloadArtifactCommandUnit;
import io.harness.delegate.task.ssh.ScriptCommandUnit;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.winrm.ArtifactDownloadHandler;
import io.harness.delegate.task.winrm.ArtifactoryArtifactDownloadHandler;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.NoopExecutionCallback;
import io.harness.rule.Owner;
import io.harness.shell.AbstractScriptExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.Silent.class)
public class SshDownloadArtifactCommandHandlerTest {
  private static final String COMMAND_STRING = "command string";
  @Mock private SshScriptExecutorFactory sshScriptExecutorFactory;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private AbstractScriptExecutor executor;
  @Mock private Map<String, Object> taskContext;
  @Mock private ArtifactoryArtifactDownloadHandler artifactoryArtifactDownloadHandler;
  @Mock private Map<SshWinRmArtifactType, ArtifactDownloadHandler> artifactHandlers;

  @InjectMocks private SshDownloadArtifactCommandHandler sshDownloadArtifactCommandHandler;

  final NgCommandUnit downloadArtifactCommandUnit =
      NgDownloadArtifactCommandUnit.builder().name("Download Artifact").destinationPath("/test").build();

  @Before
  public void setup() {
    when(sshScriptExecutorFactory.getExecutor(any())).thenReturn(executor);
    when(artifactHandlers.get(ARTIFACTORY)).thenReturn(new ArtifactoryArtifactDownloadHandler());
    when(artifactHandlers.get(ARTIFACTORY)).thenReturn(artifactoryArtifactDownloadHandler);
    when(artifactoryArtifactDownloadHandler.getCommandString(any(), any(), any())).thenReturn(COMMAND_STRING);
    when(taskContext.get(RESOLVED_ENV_VARIABLES_KEY)).thenReturn(Collections.emptyMap());
    when(executor.getLogCallback()).thenReturn(new NoopExecutionCallback());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldDownloadArtifactWithSshExecutor() {
    when(executor.executeCommandString(COMMAND_STRING)).thenReturn(CommandExecutionStatus.SUCCESS);

    SshCommandTaskParameters winrmTaskParameters =
        getSshCommandTaskParameters(downloadArtifactCommandUnit, Collections.emptyList());
    CommandExecutionStatus result =
        sshDownloadArtifactCommandHandler
            .handle(winrmTaskParameters, downloadArtifactCommandUnit, iLogStreamingTaskClient,
                CommandUnitsProgress.builder().build(), taskContext)
            .getStatus();
    assertThat(result).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidRequestException_InvalidCommandUnit() {
    assertThatThrownBy(()
                           -> sshDownloadArtifactCommandHandler.handle(SshCommandTaskParameters.builder().build(),
                               ScriptCommandUnit.builder().build(), iLogStreamingTaskClient,
                               CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidRequestException_DelegateConfigNull() {
    SshCommandTaskParameters parameters = SshCommandTaskParameters.builder()
                                              .commandUnits(Collections.singletonList(downloadArtifactCommandUnit))
                                              .artifactDelegateConfig(null)
                                              .sshInfraDelegateConfig(mock(SshInfraDelegateConfig.class))
                                              .build();

    assertThatThrownBy(()
                           -> sshDownloadArtifactCommandHandler.handle(parameters, downloadArtifactCommandUnit,
                               iLogStreamingTaskClient, CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Artifact delegate config not found.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidRequestException_DestinationPathMissing() {
    NgDownloadArtifactCommandUnit commandUnit =
        NgDownloadArtifactCommandUnit.builder().name("Download Artifact").destinationPath(null).build();
    SshCommandTaskParameters parameters = getSshCommandTaskParameters(commandUnit, Collections.emptyList());
    assertThatThrownBy(()
                           -> sshDownloadArtifactCommandHandler.handle(parameters, commandUnit, iLogStreamingTaskClient,
                               CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Please provide the destination path of the step download artifact command unit: Download Artifact");
  }

  private SshCommandTaskParameters getSshCommandTaskParameters(
      NgCommandUnit downloadArtifactCommandUnit, List<String> outputVariables) {
    return SshCommandTaskParameters.builder()
        .commandUnits(Collections.singletonList(downloadArtifactCommandUnit))
        .sshInfraDelegateConfig(mock(SshInfraDelegateConfig.class))
        .executeOnDelegate(false)
        .outputVariables(outputVariables)
        .environmentVariables(Collections.emptyMap())
        .artifactDelegateConfig(
            ArtifactoryArtifactDelegateConfig.builder().artifactDirectory("/").artifactPath("testpath").build())
        .build();
  }
}
