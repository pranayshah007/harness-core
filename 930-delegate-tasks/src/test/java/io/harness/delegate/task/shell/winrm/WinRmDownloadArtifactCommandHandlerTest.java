/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType.ARTIFACTORY;
import static io.harness.rule.OwnerRule.BOJAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.shell.WinrmTaskParameters;
import io.harness.delegate.task.ssh.CopyCommandUnit;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.NgDownloadArtifactCommandUnit;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.winrm.ArtifactDownloadHandler;
import io.harness.delegate.task.winrm.ArtifactoryArtifactDownloadHandler;
import io.harness.delegate.task.winrm.DefaultWinRmExecutor;
import io.harness.delegate.task.winrm.WinRmExecutorFactoryNG;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.NoopExecutionCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

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
public class WinRmDownloadArtifactCommandHandlerTest {
  private static final String COMMAND_STRING = "command string";
  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Mock private WinRmExecutorFactoryNG winRmExecutorFactoryNG;
  @Mock private WinRmConfigAuthEnhancer winRmConfigAuthEnhancer;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private DefaultWinRmExecutor defaultWinRmExecutor;
  @Mock private Map<String, Object> taskContext;
  @Mock private ArtifactoryArtifactDownloadHandler artifactoryArtifactDownloadHandler;
  @Mock private Map<SshWinRmArtifactType, ArtifactDownloadHandler> artifactHandlers;

  @InjectMocks private WinRmDownloadArtifactCommandHandler winRmDownloadArtifactCommandHandler;

  final NgCommandUnit downloadArtifactCommandUnit =
      NgDownloadArtifactCommandUnit.builder().name("Download Artifact").destinationPath("/test").build();

  @Before
  public void setup() {
    when(winRmExecutorFactoryNG.getExecutor(any(), anyBoolean(), anyBoolean(), any(), any()))
        .thenReturn(defaultWinRmExecutor);
    when(artifactHandlers.get(ARTIFACTORY)).thenReturn(artifactoryArtifactDownloadHandler);
    when(artifactoryArtifactDownloadHandler.getCommandString(any(), any(), any())).thenReturn(COMMAND_STRING);
    when(defaultWinRmExecutor.getLogCallback()).thenReturn(new NoopExecutionCallback());
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldDownloadArtifactWithWinRmExecutor() {
    when(defaultWinRmExecutor.executeCommandString(COMMAND_STRING)).thenReturn(CommandExecutionStatus.SUCCESS);
    WinrmTaskParameters winrmTaskParameters =
        getWinrmTaskParameters(downloadArtifactCommandUnit, Collections.emptyList());
    CommandExecutionStatus result =
        winRmDownloadArtifactCommandHandler
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
                           -> winRmDownloadArtifactCommandHandler.handle(WinrmTaskParameters.builder().build(),
                               CopyCommandUnit.builder().build(), iLogStreamingTaskClient,
                               CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid command unit specified for command task.");
  }

  @Test
  @Owner(developers = BOJAN)
  @Category(UnitTests.class)
  public void testShouldHandleInvalidRequestException_DelegateConfigNull() {
    WinrmTaskParameters parameters = WinrmTaskParameters.builder()
                                         .commandUnits(Collections.singletonList(downloadArtifactCommandUnit))
                                         .winRmInfraDelegateConfig(mock(WinRmInfraDelegateConfig.class))
                                         .artifactDelegateConfig(null)
                                         .build();

    assertThatThrownBy(()
                           -> winRmDownloadArtifactCommandHandler.handle(parameters, downloadArtifactCommandUnit,
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
    WinrmTaskParameters parameters = getWinrmTaskParameters(commandUnit, Collections.emptyList());
    assertThatThrownBy(()
                           -> winRmDownloadArtifactCommandHandler.handle(parameters, commandUnit,
                               iLogStreamingTaskClient, CommandUnitsProgress.builder().build(), taskContext))
        .isInstanceOf(HintException.class)
        .hasMessage(
            "Please provide the destination path of the step download artifact command unit: Download Artifact");
  }

  private WinrmTaskParameters getWinrmTaskParameters(
      NgCommandUnit downloadArtifactCommandUnit, List<String> outputVariables) {
    return WinrmTaskParameters.builder()
        .commandUnits(Collections.singletonList(downloadArtifactCommandUnit))
        .winRmInfraDelegateConfig(mock(WinRmInfraDelegateConfig.class))
        .executeOnDelegate(false)
        .disableWinRMCommandEncodingFFSet(true)
        .outputVariables(outputVariables)
        .artifactDelegateConfig(
            ArtifactoryArtifactDelegateConfig.builder().artifactDirectory("/").artifactPath("testpath").build())
        .build();
  }
}
