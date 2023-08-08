/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.ScmConnectorMapperDelegate;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.LogCallback;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.shell.SshSessionConfig;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class GitFetchTaskNGTest {
  private static final String TEST_INPUT_ID = generateUuid();
  @Mock private NGGitService ngGitService;
  @Mock private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private ExecutorService executorService;
  @Mock private FetchFilesResult fetchFilesResult;
  @Mock private ScmConnectorMapperDelegate scmConnectorMapperDelegate;

  @InjectMocks
  GitFetchTaskNG gitFetchTaskNG = new GitFetchTaskNG(
      DelegateTaskPackage.builder()
          .delegateId(TEST_INPUT_ID)
          .delegateTaskId(TEST_INPUT_ID)
          .data(TaskData.builder().parameters(new Object[] {}).taskType(TEST_INPUT_ID).async(false).build())
          .accountId(TEST_INPUT_ID)
          .build(),
      logStreamingTaskClient, null, null);

  private static final GitFetchFilesConfig gitFetchFilesConfig =
      GitFetchFilesConfig.builder()
          .succeedIfFileNotFound(true)
          .identifier(TEST_INPUT_ID)
          .manifestType(TEST_INPUT_ID)
          .gitStoreDelegateConfig(GitStoreDelegateConfig.builder()
                                      .gitConfigDTO(GitConfigDTO.builder().build())
                                      .fetchType(FetchType.BRANCH)
                                      .branch(TEST_INPUT_ID)
                                      .paths(Collections.singletonList(TEST_INPUT_ID))
                                      .build())
          .build();

  private static final TaskParameters taskParameters =
      GitFetchRequest.builder()
          .accountId(TEST_INPUT_ID)
          .activityId(TEST_INPUT_ID)
          .gitFetchFilesConfigs(Collections.singletonList(gitFetchFilesConfig))
          .shouldOpenLogStream(false)
          .executionLogName(TEST_INPUT_ID)
          .build();

  private SshSessionConfig sshSessionConfig;
  private Future<?> future;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
    doReturn(future).when(executorService).submit(any(Runnable.class));
    doNothing().when(gitDecryptionHelper).decryptGitConfig(any(GitConfigDTO.class), anyList());
    doReturn(sshSessionConfig).when(gitDecryptionHelper).getSSHSessionConfig(any(SSHKeySpecDTO.class), anyList());
    doNothing()
        .when(gitFetchFilesTaskHelper)
        .printFileNamesInExecutionLogs(anyList(), any(LogCallback.class), any(Boolean.class));
    doNothing()
        .when(gitFetchFilesTaskHelper)
        .printFileNamesInExecutionLogs(any(LogCallback.class), anyList(), any(Boolean.class));
    doReturn(new ArrayList<>()).when(fetchFilesResult).getFiles();
    doReturn(GitConfigDTO.builder().build()).when(scmConnectorMapperDelegate).toGitConfigDTO(any(), any());
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTaskRunWithInvalidParams() {
    assertThatThrownBy(() -> gitFetchTaskNG.run(new Object[] {})).isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTaskRun() throws IOException {
    doReturn(fetchFilesResult)
        .when(ngGitService)
        .fetchFilesByPath(anyString(), any(GitStoreDelegateConfig.class), anyString(), any(SshSessionConfig.class),
            any(GitConfigDTO.class));
    doReturn(new ArrayList<>()).when(fetchFilesResult).getFiles();
    when(gitDecryptionHelper.getSSHSessionConfig(any(), any())).thenReturn(mock(SshSessionConfig.class));
    GitFetchResponse response = gitFetchTaskNG.run(taskParameters);
    assertThat(response.getTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFetchFilesFromRepoWithNonExistentFile() throws IOException {
    doThrow(new InvalidRequestException(TEST_INPUT_ID, new NoSuchFileException(TEST_INPUT_ID)))
        .when(ngGitService)
        .fetchFilesByPath(anyString(), any(GitStoreDelegateConfig.class), anyString(), any(SshSessionConfig.class),
            any(GitConfigDTO.class));
    when(gitDecryptionHelper.getSSHSessionConfig(any(), any())).thenReturn(mock(SshSessionConfig.class));

    GitFetchResponse response = gitFetchTaskNG.run(taskParameters);
    assertThat(response.getTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testFetchFilesFromRepoWithException() throws IOException {
    doThrow(new InvalidRequestException(TEST_INPUT_ID))
        .when(ngGitService)
        .fetchFilesByPath(anyString(), any(GitStoreDelegateConfig.class), anyString(), any(SshSessionConfig.class),
            any(GitConfigDTO.class));
    when(gitDecryptionHelper.getSSHSessionConfig(any(), any())).thenReturn(mock(SshSessionConfig.class));
    assertThatThrownBy(() -> gitFetchTaskNG.run(taskParameters))
        .isInstanceOf(TaskNGDataException.class)
        .hasCauseInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTaskRunInvalidRequest() {
    assertThatThrownBy(() -> gitFetchTaskNG.run(GitFetchRequest.builder().build()))
        .isInstanceOf(TaskNGDataException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTaskRunGivenWingsException() {
    doThrow(new WingsException(TEST_INPUT_ID)).when(executorService).submit(any(Runnable.class));
    assertThatThrownBy(() -> gitFetchTaskNG.run(GitFetchRequest.builder().build()))
        .isInstanceOf(TaskNGDataException.class);
  }
}
