/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FileContentBatchResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.exception.GitClientException;
import io.harness.logging.LogCallback;
import io.harness.product.ci.scm.proto.FileBatchContentResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.rule.Owner;
import io.harness.service.ScmServiceClient;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.GitConfig.ProviderType;
import software.wings.beans.GitFileConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.dto.SettingAttribute;
import software.wings.beans.yaml.GitFetchFilesResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class ScmFetchFilesHelperTest extends WingsBaseTest {
  @Mock private ScmDelegateClient scmDelegateClient;
  @Mock private ScmServiceClient scmServiceClient;
  @InjectMocks private ScmFetchFilesHelper scmFetchFilesHelper;

  @Before
  public void setUp() throws Exception {}

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFolder() {
    ScmFetchFilesHelper spyScmFetchFilesHelper = spy(scmFetchFilesHelper);
    LogCallback logCallback = mock(ExecutionLogCallback.class);
    GitConfig gitConfig = GitConfig.builder().repoUrl("helm-url").build();
    GitFileConfig gitFileConfig = GitFileConfig.builder().filePath("test").build();
    doReturn(GithubConnectorDTO.builder().build()).when(spyScmFetchFilesHelper).getScmConnector(any());
    doReturn(FileContentBatchResponse.builder()
                 .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                               .addFileContents(FileContent.newBuilder()
                                                                    .setStatus(200)
                                                                    .setContent("content")
                                                                    .setPath("test/test2/path.txt")
                                                                    .build())
                                               .build())
                 .build())
        .when(scmDelegateClient)
        .processScmRequest(any());
    spyScmFetchFilesHelper.downloadFilesUsingScm("manifests", gitFileConfig, gitConfig, logCallback);
    File file = new File("manifests/test/test2/path.txt");
    assertThat(file.exists()).isTrue();
  }

  @Test
  @SneakyThrows(IOException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFolderRootPath() {
    ScmFetchFilesHelper spyScmFetchFilesHelper = spy(scmFetchFilesHelper);
    String encodedValue = Base64.getEncoder().encodeToString("content: abc".getBytes(StandardCharsets.UTF_8));
    LogCallback logCallback = mock(ExecutionLogCallback.class);
    GitConfig gitConfig = GitConfig.builder().repoUrl("helm-url").build();
    GitFileConfig gitFileConfigSlashRootPath = GitFileConfig.builder().filePath("/").build();
    GitFileConfig gitFileConfigDotRootPath = GitFileConfig.builder().filePath(".").build();
    doReturn(GithubConnectorDTO.builder().build()).when(spyScmFetchFilesHelper).getScmConnector(any());
    doReturn(GitlabConnectorDTO.builder().build()).when(spyScmFetchFilesHelper).getScmConnector(any());
    doReturn(BitbucketConnectorDTO.builder().build()).when(spyScmFetchFilesHelper).getScmConnector(any());
    when(scmDelegateClient.processScmRequest(any()))
        .thenReturn(FileContentBatchResponse.builder()
                        .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                                      .addFileContents(FileContent.newBuilder()
                                                                           .setStatus(200)
                                                                           .setContent(encodedValue)
                                                                           .setPath("test2/path.txt")
                                                                           .build())
                                                      .build())
                        .build(),
            FileContentBatchResponse.builder()
                .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                              .addFileContents(FileContent.newBuilder()
                                                                   .setStatus(200)
                                                                   .setContent(encodedValue)
                                                                   .setPath("test3/path.txt")
                                                                   .build())
                                              .build())
                .build());
    spyScmFetchFilesHelper.downloadFilesUsingScm("manifests", gitFileConfigSlashRootPath, gitConfig, logCallback);
    spyScmFetchFilesHelper.downloadFilesUsingScm("manifests", gitFileConfigDotRootPath, gitConfig, logCallback);
    File file = new File("manifests/test2/path.txt");
    File file2 = new File("manifests/test3/path.txt");
    assertThat(file.exists()).isTrue();
    assertThat(file2.exists()).isTrue();
    assertThat(FileUtils.readFileToString(file, StandardCharsets.UTF_8)).isEqualTo("content: abc");
    assertThat(FileUtils.readFileToString(file2, StandardCharsets.UTF_8)).isEqualTo("content: abc");
  }

  @Test
  @SneakyThrows(IOException.class)
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFilepath() {
    ScmFetchFilesHelper spyScmFetchFilesHelper = spy(scmFetchFilesHelper);
    LogCallback logCallback = mock(ExecutionLogCallback.class);
    GitConfig gitConfig = GitConfig.builder().repoUrl("helm-url").build();
    GitFileConfig gitFileConfig = GitFileConfig.builder().filePath("test/test2/path.txt").build();
    doReturn(GithubConnectorDTO.builder().build()).when(spyScmFetchFilesHelper).getScmConnector(any());
    when(scmDelegateClient.processScmRequest(any()))
        .thenReturn(FileContentBatchResponse.builder()
                        .fileBatchContentResponse(FileBatchContentResponse.newBuilder().build())
                        .build())
        .thenReturn(FileContentBatchResponse.builder()
                        .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                                      .addFileContents(FileContent.newBuilder()
                                                                           .setStatus(200)
                                                                           .setContent("content")
                                                                           .setPath("test/test2/path.txt")
                                                                           .build())
                                                      .build())
                        .build());
    spyScmFetchFilesHelper.downloadFilesUsingScm("manifests", gitFileConfig, gitConfig, logCallback);
    File file = new File("manifests/test/test2/path.txt");
    assertThat(file.exists()).isTrue();
    assertThat(FileUtils.readFileToString(file, StandardCharsets.UTF_8)).isEqualTo("content");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldFetchFilesFromRepoWithScm() {
    ScmFetchFilesHelper spyScmFetchFilesHelper = spy(scmFetchFilesHelper);
    List<String> filePathList = Collections.singletonList("test");
    GitConfig gitConfig = GitConfig.builder().repoUrl("helm-url").build();
    GitFileConfig gitFileConfig = GitFileConfig.builder().build();
    doReturn(GithubConnectorDTO.builder().build()).when(spyScmFetchFilesHelper).getScmConnector(any());
    doReturn(FileContentBatchResponse.builder()
                 .fileBatchContentResponse(
                     FileBatchContentResponse.newBuilder()
                         .addFileContents(FileContent.newBuilder().setStatus(200).setContent("content").build())
                         .addFileContents(FileContent.newBuilder().setStatus(200).setContent("content").build())
                         .build())
                 .build())
        .when(scmDelegateClient)
        .processScmRequest(any());
    GitFetchFilesResult result =
        spyScmFetchFilesHelper.fetchFilesFromRepoWithScm(gitFileConfig, gitConfig, filePathList);
    assertThat(result.getFiles().size()).isEqualTo(2);
    assertThat(result.getFiles().get(0).getFileContent()).isEqualTo("content");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldThrowExceptionIfAFileNotFound() {
    ScmFetchFilesHelper spyScmFetchFilesHelper = spy(scmFetchFilesHelper);
    List<String> filePathList = Collections.singletonList("test");
    GitConfig gitConfig = GitConfig.builder().repoUrl("helm-url").build();
    GitFileConfig gitFileConfig = GitFileConfig.builder().build();
    doReturn(GithubConnectorDTO.builder().build()).when(spyScmFetchFilesHelper).getScmConnector(any());
    doReturn(FileContentBatchResponse.builder()
                 .fileBatchContentResponse(
                     FileBatchContentResponse.newBuilder()
                         .addFileContents(FileContent.newBuilder().setStatus(200).setContent("content").build())
                         .addFileContents(FileContent.newBuilder().setStatus(400).setContent("content").build())
                         .build())
                 .build())
        .when(scmDelegateClient)
        .processScmRequest(any());
    assertThatExceptionOfType(GitClientException.class)
        .isThrownBy(() -> spyScmFetchFilesHelper.fetchFilesFromRepoWithScm(gitFileConfig, gitConfig, filePathList));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldUseScm() {
    assertThat(scmFetchFilesHelper.shouldUseScm(
                   true, GitConfig.builder().sshSettingAttribute(null).providerType(ProviderType.GITHUB).build()))
        .isTrue();
    assertThat(scmFetchFilesHelper.shouldUseScm(
                   true, GitConfig.builder().sshSettingAttribute(null).providerType(ProviderType.GITLAB).build()))
        .isTrue();
    assertThat(scmFetchFilesHelper.shouldUseScm(
                   true, GitConfig.builder().sshSettingAttribute(null).providerType(ProviderType.GIT).build()))
        .isFalse();
    assertThat(scmFetchFilesHelper.shouldUseScm(
                   true, GitConfig.builder().sshSettingAttribute(null).providerType(ProviderType.BITBUCKET).build()))
        .isTrue();
    assertThat(scmFetchFilesHelper.shouldUseScm(false,
                   GitConfig.builder()
                       .sshSettingAttribute(SettingAttribute.builder().build())
                       .providerType(ProviderType.GITHUB)
                       .build()))
        .isFalse();
    assertThat(scmFetchFilesHelper.shouldUseScm(false,
                   GitConfig.builder()
                       .sshSettingAttribute(SettingAttribute.builder().build())
                       .providerType(ProviderType.GITLAB)
                       .build()))
        .isFalse();
    assertThat(scmFetchFilesHelper.shouldUseScm(false,
                   GitConfig.builder()
                       .sshSettingAttribute(SettingAttribute.builder().build())
                       .providerType(ProviderType.BITBUCKET)
                       .build()))
        .isFalse();
    assertThat(scmFetchFilesHelper.shouldUseScm(
                   false, GitConfig.builder().sshSettingAttribute(null).providerType(ProviderType.GITHUB).build()))
        .isFalse();
    assertThat(scmFetchFilesHelper.shouldUseScm(
                   false, GitConfig.builder().sshSettingAttribute(null).providerType(ProviderType.GITLAB).build()))
        .isFalse();
    assertThat(scmFetchFilesHelper.shouldUseScm(
                   false, GitConfig.builder().sshSettingAttribute(null).providerType(ProviderType.BITBUCKET).build()))
        .isFalse();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetScmConnector() {
    ScmConnector gitHubScmConnector = scmFetchFilesHelper.getScmConnector(GitConfig.builder()
                                                                              .repoUrl("GitHubURL")
                                                                              .password("password".toCharArray())
                                                                              .providerType(ProviderType.GITHUB)
                                                                              .build());
    assertThat(gitHubScmConnector.getUrl()).isEqualTo("GitHubURL");
    assertThat(((GithubTokenSpecDTO) ((GithubConnectorDTO) gitHubScmConnector).getApiAccess().getSpec())
                   .getTokenRef()
                   .getDecryptedValue())
        .isEqualTo("password".toCharArray());

    ScmConnector gitLabScmConnector = scmFetchFilesHelper.getScmConnector(GitConfig.builder()
                                                                              .repoUrl("GitlabURL")
                                                                              .password("password".toCharArray())
                                                                              .providerType(ProviderType.GITLAB)
                                                                              .build());
    assertThat(gitLabScmConnector.getUrl()).isEqualTo("GitlabURL");
    assertThat(((GitlabTokenSpecDTO) ((GitlabConnectorDTO) gitLabScmConnector).getApiAccess().getSpec())
                   .getTokenRef()
                   .getDecryptedValue())
        .isEqualTo("password".toCharArray());
    assertThat(((GitlabConnectorDTO) gitLabScmConnector).getApiAccess().getType()).isEqualTo(GitlabApiAccessType.TOKEN);
    assertThat(scmFetchFilesHelper.getScmConnector(GitConfig.builder().providerType(ProviderType.GIT).build()))
        .isNull();

    ScmConnector bitBucketScmConnector = scmFetchFilesHelper.getScmConnector(GitConfig.builder()
                                                                                 .repoUrl("BitbucketURL")
                                                                                 .password("password".toCharArray())
                                                                                 .username("username")
                                                                                 .providerType(ProviderType.BITBUCKET)
                                                                                 .build());
    assertThat(bitBucketScmConnector.getUrl()).isEqualTo("BitbucketURL");
    assertThat(
        ((BitbucketUsernameTokenApiAccessDTO) ((BitbucketConnectorDTO) bitBucketScmConnector).getApiAccess().getSpec())
            .getTokenRef()
            .getDecryptedValue())
        .isEqualTo("password".toCharArray());
    assertThat(
        ((BitbucketUsernameTokenApiAccessDTO) ((BitbucketConnectorDTO) bitBucketScmConnector).getApiAccess().getSpec())
            .getUsernameRef()
            .getDecryptedValue())
        .isEqualTo("username".toCharArray());
  }
}
