/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FileContentBatchResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.service.scm.ScmDelegateClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.exception.GitClientException;
import io.harness.git.model.FetchFilesResult;
import io.harness.logging.LogCallback;
import io.harness.product.ci.scm.proto.FileBatchContentResponse;
import io.harness.product.ci.scm.proto.FileContent;
import io.harness.rule.Owner;
import io.harness.service.ScmServiceClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class ScmFetchFilesHelperNGTest extends CategoryTest {
  @Mock private ScmDelegateClient scmDelegateClient;
  @Mock private ScmServiceClient scmServiceClient;
  @InjectMocks ScmFetchFilesHelperNG scmFetchFilesHelperNG;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldFetchFilesFromRepoWithScm() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    List<String> filePathList = Collections.singletonList("test");
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().fetchType(FetchType.BRANCH).branch("branch").build();
    doReturn(FileContentBatchResponse.builder()
                 .fileBatchContentResponse(
                     FileBatchContentResponse.newBuilder()
                         .addFileContents(FileContent.newBuilder().setStatus(200).setContent("content").build())
                         .addFileContents(FileContent.newBuilder().setStatus(200).setContent("content").build())
                         .build())
                 .build())
        .when(scmDelegateClient)
        .processScmRequest(any());

    FetchFilesResult result = spyScmFetchFilesHelperNG.fetchFilesFromRepoWithScm(gitStoreDelegateConfig, filePathList);

    assertThat(result.getFiles().size()).isEqualTo(2);
    assertThat(result.getFiles().get(0).getFileContent()).isEqualTo("content");
    assertThat(result.getFiles().get(1).getFileContent()).isEqualTo("content");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldFetchFilesFromRepoWithScmExceptionThrownIfAFileNotFound() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    List<String> filePathList = Collections.singletonList("test");
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().fetchType(FetchType.BRANCH).branch("branch").build();
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
        .isThrownBy(() -> spyScmFetchFilesHelperNG.fetchFilesFromRepoWithScm(gitStoreDelegateConfig, filePathList));
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testShouldFetchAnyFilesPresent() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    List<String> filePathList = Collections.singletonList("test");
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().fetchType(FetchType.BRANCH).branch("branch").build();
    doReturn(FileContentBatchResponse.builder()
                 .fileBatchContentResponse(
                     FileBatchContentResponse.newBuilder()
                         .addFileContents(FileContent.newBuilder().setStatus(200).setContent("content").build())
                         .addFileContents(FileContent.newBuilder().setStatus(400).setContent("content").build())
                         .build())
                 .build())
        .when(scmDelegateClient)
        .processScmRequest(any());

    FetchFilesResult fetchFilesResult =
        spyScmFetchFilesHelperNG.fetchAnyFilesFromRepoWithScm(gitStoreDelegateConfig, filePathList);
    assertThat(fetchFilesResult.getFiles()).hasSize(1);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFolder() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .paths(Collections.singletonList("test"))
                                                        .fetchType(FetchType.BRANCH)
                                                        .branch("branch")
                                                        .build();
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

    spyScmFetchFilesHelperNG.downloadFilesUsingScm("manifests", gitStoreDelegateConfig, logCallback);

    File file = new File("manifests/test2/path.txt");
    assertThat(file.exists()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFolderRootPath() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .paths(Arrays.asList(".", "/"))
                                                        .fetchType(FetchType.BRANCH)
                                                        .branch("branch")
                                                        .build();

    when(scmDelegateClient.processScmRequest(any()))
        .thenReturn(FileContentBatchResponse.builder()
                        .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                                      .addFileContents(FileContent.newBuilder()
                                                                           .setStatus(200)
                                                                           .setContent("content")
                                                                           .setPath("test2/path.txt")
                                                                           .build())
                                                      .build())
                        .build(),
            FileContentBatchResponse.builder()
                .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                              .addFileContents(FileContent.newBuilder()
                                                                   .setStatus(200)
                                                                   .setContent("content")
                                                                   .setPath("test3/path.txt")
                                                                   .build())
                                              .build())
                .build());

    spyScmFetchFilesHelperNG.downloadFilesUsingScm("manifests", gitStoreDelegateConfig, logCallback);

    File file = new File("manifests/test2/path.txt");
    File file2 = new File("manifests/test3/path.txt");
    assertThat(file.exists()).isTrue();
    assertThat(file2.exists()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFolderMultipleFolders() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    List<String> paths = Arrays.asList("path1", "path2");
    GitStoreDelegateConfig gitStoreDelegateConfig =
        GitStoreDelegateConfig.builder().paths(paths).fetchType(FetchType.BRANCH).branch("branch").build();

    when(scmDelegateClient.processScmRequest(any()))
        .thenReturn(FileContentBatchResponse.builder()
                        .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                                      .addFileContents(FileContent.newBuilder()
                                                                           .setStatus(200)
                                                                           .setContent("content")
                                                                           .setPath("path1/file1/path.txt")
                                                                           .build())
                                                      .build())
                        .build())
        .thenReturn(FileContentBatchResponse.builder()
                        .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                                      .addFileContents(FileContent.newBuilder()
                                                                           .setStatus(200)
                                                                           .setContent("content")
                                                                           .setPath("path2/file2/path.txt")
                                                                           .build())
                                                      .build())
                        .build());

    spyScmFetchFilesHelperNG.downloadFilesUsingScm("manifests", gitStoreDelegateConfig, logCallback);

    File file = new File("manifests/file1/path.txt");
    assertThat(file.exists()).isTrue();
    File file2 = new File("manifests/file2/path.txt");
    assertThat(file2.exists()).isTrue();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFilepath() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .paths(Collections.singletonList("test/test2/path.txt"))
                                                        .fetchType(FetchType.BRANCH)
                                                        .branch("branch")
                                                        .build();
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

    spyScmFetchFilesHelperNG.downloadFilesUsingScm("manifests", gitStoreDelegateConfig, logCallback);

    File file = new File("manifests/test/test2/path.txt");
    assertThat(file.exists()).isTrue();
  }

  @Test
  @SneakyThrows(IOException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testShouldDownloadFilesUsingScmByFilepathWithLeadingSlash() {
    ScmFetchFilesHelperNG spyScmFetchFilesHelperNG = spy(scmFetchFilesHelperNG);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    String encodedValue = Base64.getEncoder().encodeToString("content: abc".getBytes(StandardCharsets.UTF_8));
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .paths(Collections.singletonList("/test/templates"))
                                                        .fetchType(FetchType.BRANCH)
                                                        .branch("main")
                                                        .build();
    when(scmDelegateClient.processScmRequest(any()))
        .thenReturn(FileContentBatchResponse.builder()
                        .fileBatchContentResponse(FileBatchContentResponse.newBuilder()
                                                      .addFileContents(FileContent.newBuilder()
                                                                           .setStatus(200)
                                                                           .setContent(encodedValue)
                                                                           .setPath("test/templates/deployment.yaml")
                                                                           .build())
                                                      .build())
                        .build());

    spyScmFetchFilesHelperNG.downloadFilesUsingScm("manifests", gitStoreDelegateConfig, logCallback);

    File file = new File("manifests/deployment.yaml");
    assertThat(file.exists()).isTrue();
    assertThat(FileUtils.readFileToString(file, StandardCharsets.UTF_8)).isEqualTo("content: abc");
  }
}
