/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt.files;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.shell.SshSessionConfig.Builder.aSshSessionConfig;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.git.GitClientV2;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.logging.LogCallback;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.SshSessionConfig;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class GitStoreDownloadServiceTest extends CategoryTest {
  private static final String COMMIT_ID = "commitId";
  private static final String GIT_URL = "git@git.com";
  private static final String ACCOUNT_ID = "accountId";
  private static final String OUTPUT_DIR = "./output";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private GitClientV2 gitClient;
  @Mock private SshSessionConfigMapper sshSessionConfigMapper;
  @Mock private NGGitService ngGitService;
  @Mock private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Mock private LogCallback logCallback;

  @InjectMocks private GitStoreDownloadService downloadService;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadGitSsh() {
    final GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url(GIT_URL).gitAuthType(GitAuthType.SSH).build();
    final SSHKeySpecDTO sshKeySpec = SSHKeySpecDTO.builder().build();
    testDownloadGit(gitConfigDTO, sshKeySpec, singletonList("./"));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadGitHttp() {
    final GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url(GIT_URL).gitAuthType(GitAuthType.HTTP).build();
    testDownloadGit(gitConfigDTO, null, asList("file1", "file2"));
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchFilesSsh() {
    final GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url(GIT_URL).gitAuthType(GitAuthType.SSH).build();
    final SSHKeySpecDTO sshKeySpec = SSHKeySpecDTO.builder().build();
    testFetchFiles(gitConfigDTO, sshKeySpec, asList("file1", "file2", "file3"));
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchFilesHttp() {
    final GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url(GIT_URL).gitAuthType(GitAuthType.HTTP).build();
    testFetchFiles(gitConfigDTO, null, asList("file1", "file2", "file3"));
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchFilesScm() {
    final List<String> files = asList("file1", "file2");
    final GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url(GIT_URL).gitAuthType(GitAuthType.HTTP).build();
    final GitStoreDelegateConfig storeDelegateConfig = GitStoreDelegateConfig.builder()
                                                           .commitId(COMMIT_ID)
                                                           .gitConfigDTO(gitConfigDTO)
                                                           .paths(files)
                                                           .fetchType(FetchType.COMMIT)
                                                           .optimizedFilesFetch(true)
                                                           .build();

    FetchFilesResult result = downloadService.fetchFiles(storeDelegateConfig, ACCOUNT_ID, OUTPUT_DIR, logCallback);
    verify(scmFetchFilesHelper).downloadFilesUsingScm(OUTPUT_DIR, storeDelegateConfig, logCallback);
    assertThat(result.getFiles())
        .containsAll(files.stream()
                         .map(file -> Paths.get(OUTPUT_DIR, file).toAbsolutePath().toString())
                         .collect(Collectors.toList()));
  }

  private void testDownloadGit(GitConfigDTO gitConfigDTO, SSHKeySpecDTO sshKeySpec, List<String> paths) {
    final List<EncryptedDataDetail> encryptedDataDetailList = emptyList();
    final GitStoreDelegateConfig storeDelegateConfig = GitStoreDelegateConfig.builder()
                                                           .commitId(COMMIT_ID)
                                                           .gitConfigDTO(gitConfigDTO)
                                                           .paths(paths)
                                                           .sshKeySpecDTO(sshKeySpec)
                                                           .fetchType(FetchType.COMMIT)
                                                           .encryptedDataDetails(encryptedDataDetailList)
                                                           .build();
    final SshSessionConfig sshSessionConfig = aSshSessionConfig().build();
    if (sshKeySpec != null) {
      doReturn(sshSessionConfig).when(sshSessionConfigMapper).getSSHSessionConfig(sshKeySpec, encryptedDataDetailList);
    }

    downloadService.download(storeDelegateConfig, ACCOUNT_ID, OUTPUT_DIR, logCallback);

    if (sshKeySpec != null) {
      verify(sshSessionConfigMapper).getSSHSessionConfig(sshKeySpec, encryptedDataDetailList);
    }

    verify(ngGitService).getAuthRequest(gitConfigDTO, sshKeySpec != null ? sshSessionConfig : null);
    ArgumentCaptor<DownloadFilesRequest> downloadFileRequestCaptor =
        ArgumentCaptor.forClass(DownloadFilesRequest.class);
    verify(gitClient).cloneRepoAndCopyToDestDir(downloadFileRequestCaptor.capture());
    DownloadFilesRequest request = downloadFileRequestCaptor.getValue();
    assertThat(request.getFilePaths()).containsAll(paths);
    assertThat(request.getCommitId()).isEqualTo(COMMIT_ID);
  }

  private void testFetchFiles(GitConfigDTO gitConfigDTO, SSHKeySpecDTO sshKeySpec, List<String> paths)
      throws IOException {
    final List<EncryptedDataDetail> encryptedDataDetailList = emptyList();
    final GitStoreDelegateConfig storeDelegateConfig = GitStoreDelegateConfig.builder()
                                                           .commitId(COMMIT_ID)
                                                           .gitConfigDTO(gitConfigDTO)
                                                           .paths(paths)
                                                           .sshKeySpecDTO(sshKeySpec)
                                                           .fetchType(FetchType.COMMIT)
                                                           .encryptedDataDetails(encryptedDataDetailList)
                                                           .build();
    final SshSessionConfig sshSessionConfig = aSshSessionConfig().build();
    if (sshKeySpec != null) {
      doReturn(sshSessionConfig).when(sshSessionConfigMapper).getSSHSessionConfig(sshKeySpec, encryptedDataDetailList);
    }

    FetchFilesResult result = downloadService.fetchFiles(storeDelegateConfig, ACCOUNT_ID, OUTPUT_DIR, logCallback);

    if (sshKeySpec != null) {
      verify(sshSessionConfigMapper).getSSHSessionConfig(sshKeySpec, encryptedDataDetailList);
    }

    verify(ngGitService).getAuthRequest(gitConfigDTO, sshKeySpec != null ? sshSessionConfig : null);
    ArgumentCaptor<DownloadFilesRequest> downloadFileRequestCaptor =
        ArgumentCaptor.forClass(DownloadFilesRequest.class);
    verify(gitClient).downloadFiles(downloadFileRequestCaptor.capture());
    DownloadFilesRequest request = downloadFileRequestCaptor.getValue();
    assertThat(request.getFilePaths()).containsAll(paths);
    assertThat(request.getCommitId()).isEqualTo(COMMIT_ID);

    assertThat(result.getFiles())
        .containsAll(paths.stream()
                         .map(file -> Paths.get(OUTPUT_DIR, file).toAbsolutePath().toString())
                         .collect(Collectors.toList()));
  }
}