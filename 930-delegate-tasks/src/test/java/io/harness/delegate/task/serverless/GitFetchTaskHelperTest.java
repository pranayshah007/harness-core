/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.RAFAEL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitServiceImpl;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesTaskHelper;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.git.ScmFetchFilesHelperNG;
import io.harness.git.GitClientV2;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.SshSessionConfig;

import software.wings.service.impl.security.SecretDecryptionServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class GitFetchTaskHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private GitClientV2 gitClientV2;
  @Mock private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Mock private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Mock private NGGitServiceImpl ngGitService;
  @Mock private SecretDecryptionServiceImpl secretDecryptionService;
  @Mock private GitDecryptionHelper gitDecryptionHelper;
  @Mock private LogCallback executionLogCallback;

  @InjectMocks private GitFetchTaskHelper serverlessGitFetchTaskHelper;

  private static final String accountId = "accountId";

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getCompleteFilePathTest() {
    assertThat(GitFetchTaskHelper.getCompleteFilePath("a", "b")).isEqualTo("ab");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getCompleteFilePathTestWhenFolderEmpty() {
    assertThat(GitFetchTaskHelper.getCompleteFilePath(null, "b")).isEqualTo("b");
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchFileFromRepoisOptimizedFilesFetchTrueTest() throws IOException {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url("url").build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .branch("branch")
                                                        .commitId("commitId")
                                                        .connectorName("connector")
                                                        .connectorId("connectorId")
                                                        .manifestId("manifest")
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .fetchType(FetchType.BRANCH)
                                                        .paths(new ArrayList<String>(Arrays.asList("path1", "path2")))
                                                        .optimizedFilesFetch(true)
                                                        .build();
    List<String> filePaths = new ArrayList<>(Arrays.asList("path1", "path2"));
    serverlessGitFetchTaskHelper.fetchFileFromRepo(gitStoreDelegateConfig, filePaths, "accountId", gitConfigDTO);
    verify(scmFetchFilesHelper).fetchFilesFromRepoWithScm(gitStoreDelegateConfig, filePaths);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void fetchFileFromRepoTestisOptimizedFilesFetchFalseTest() throws IOException {
    GitConfigDTO gitConfigDTO = GitConfigDTO.builder().url("url").build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .branch("branch")
                                                        .commitId("commitId")
                                                        .connectorName("connector")
                                                        .connectorId("connectorId")
                                                        .manifestId("manifest")
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .fetchType(FetchType.BRANCH)
                                                        .paths(new ArrayList<String>(Arrays.asList("path1", "path2")))
                                                        .optimizedFilesFetch(false)
                                                        .build();
    List<String> filePaths = new ArrayList<>(Arrays.asList("path1", "path2"));

    SshSessionConfig sshSessionConfig = SshSessionConfig.Builder.aSshSessionConfig().withAccountId(accountId).build();
    doReturn(sshSessionConfig).when(gitDecryptionHelper).getSSHSessionConfig(any(), any());
    serverlessGitFetchTaskHelper.fetchFileFromRepo(gitStoreDelegateConfig, filePaths, accountId, gitConfigDTO);
    verify(ngGitService).getAuthRequest(gitConfigDTO, sshSessionConfig);
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void printFileNamesTestWhenCloseLogStreamIsTrue() {
    List<String> filelist = Arrays.asList("path1", "path2");
    serverlessGitFetchTaskHelper.printFileNames(executionLogCallback, filelist, true);
    verify(executionLogCallback).saveExecutionLog("\nFetching following Files :");
    verify(gitFetchFilesTaskHelper, times(1)).printFileNamesInExecutionLogs(filelist, executionLogCallback, true);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void printFileNamesTestWhenCloseLogStreamIsFalse() {
    List<String> filelist = Arrays.asList("path1", "path2");
    serverlessGitFetchTaskHelper.printFileNames(executionLogCallback, filelist, false);
    verify(executionLogCallback).saveExecutionLog("\nFetching following Files :");
    verify(gitFetchFilesTaskHelper, times(1)).printFileNamesInExecutionLogs(filelist, executionLogCallback, false);
  }
  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void decryptGitStoreConfigTest() {
    GithubAppSpecDTO githubApiAccessSpecDTO =
        GithubAppSpecDTO.builder().installationId("instId").applicationId("appId").build();
    GithubApiAccessDTO githubApiAccessDTO = GithubApiAccessDTO.builder().spec(githubApiAccessSpecDTO).build();
    GithubConnectorDTO gitConfigDTO = GithubConnectorDTO.builder().apiAccess(githubApiAccessDTO).url("url").build();
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .branch("branch")
                                                        .commitId("commitId")
                                                        .connectorName("connector")
                                                        .connectorId("connectorId")
                                                        .manifestId("manifest")
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .fetchType(FetchType.BRANCH)
                                                        .paths(new ArrayList<String>(Arrays.asList("path1", "path2")))
                                                        .optimizedFilesFetch(false)
                                                        .build();
    serverlessGitFetchTaskHelper.decryptGitStoreConfig(gitStoreDelegateConfig);
    verify(secretDecryptionService)
        .decrypt(GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
            gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
  }
}
