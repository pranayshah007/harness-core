/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.PRATYUSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.product.ci.scm.proto.AzureProvider;
import io.harness.product.ci.scm.proto.BitbucketServerProvider;
import io.harness.product.ci.scm.proto.ContentType;
import io.harness.product.ci.scm.proto.FileChange;
import io.harness.product.ci.scm.proto.FindFilesInBranchRequest;
import io.harness.product.ci.scm.proto.FindFilesInBranchResponse;
import io.harness.product.ci.scm.proto.GithubProvider;
import io.harness.product.ci.scm.proto.PageResponse;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class GetFilesInFolderForkTaskTest extends CategoryTest {
  SCMGrpc.SCMBlockingStub scmBlockingStub = mock(SCMGrpc.SCMBlockingStub.class);
  GetFilesInFolderForkTask getFilesInFolderForkTask = new GetFilesInFolderForkTask(null, null, null, null, null);

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testAppendFolderPath1() {
    String folderPath = "/";
    FileChange file1 = FileChange.newBuilder().setPath("file1.yaml").build();
    FileChange file2 = FileChange.newBuilder().setPath("file2.yaml").build();
    List<FileChange> files = new ArrayList<>();
    files.add(file1);
    files.add(file2);
    List<FileChange> actual = getFilesInFolderForkTask.appendFolderPath(files, folderPath);
    assertThat(actual.get(0).getPath()).isEqualTo("/file1.yaml");
    assertThat(actual.get(1).getPath()).isEqualTo("/file2.yaml");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testAppendFolderPath2() {
    String folderPath1 = "folder1";
    String folderPath2 = "folder1/";
    FileChange file1 = FileChange.newBuilder().setPath("file1.yaml").build();
    FileChange file2 = FileChange.newBuilder().setPath("file2.yaml").build();
    List<FileChange> files = new ArrayList<>();
    files.add(file1);
    files.add(file2);
    List<FileChange> actual1 = getFilesInFolderForkTask.appendFolderPath(files, folderPath1);
    List<FileChange> actual2 = getFilesInFolderForkTask.appendFolderPath(files, folderPath2);
    assertThat(actual1.get(0).getPath()).isEqualTo("folder1/file1.yaml");
    assertThat(actual1.get(1).getPath()).isEqualTo("folder1/file2.yaml");
    assertThat(actual1.get(0).getPath()).isEqualTo(actual2.get(0).getPath());
    assertThat(actual1.get(1).getPath()).isEqualTo(actual2.get(1).getPath());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSetNextPage() {
    final Provider provider = Provider.newBuilder().setGithub(GithubProvider.newBuilder().build()).build();
    final FindFilesInBranchResponse filesInBranchResponse = getFindFilesInBranchResponseWithPage(2);
    final FindFilesInBranchRequest.Builder requestBuilder = FindFilesInBranchRequest.newBuilder();
    final GetFilesInFolderForkTask testGetFilesInFolderTask =
        new GetFilesInFolderForkTask(null, provider, null, null, null);

    testGetFilesInFolderTask.setNextPage(requestBuilder, filesInBranchResponse);
    assertThat(requestBuilder.getPagination().getPage()).isEqualTo(2);
    assertThat(requestBuilder.getPagination().getSize()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSetNextPageBitbucketServer() {
    final Provider provider =
        Provider.newBuilder().setBitbucketServer(BitbucketServerProvider.newBuilder().build()).build();
    final FindFilesInBranchResponse filesInBranchResponse = getFindFilesInBranchResponseWithPage(3);
    final FindFilesInBranchRequest.Builder requestBuilder = FindFilesInBranchRequest.newBuilder();
    final GetFilesInFolderForkTask testGetFilesInFolderTask =
        new GetFilesInFolderForkTask(null, provider, null, null, null);

    testGetFilesInFolderTask.setNextPage(requestBuilder, filesInBranchResponse);
    assertThat(requestBuilder.getPagination().getPage()).isEqualTo(3);
    assertThat(requestBuilder.getPagination().getSize()).isEqualTo(25);
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testGetAllFilesPresentInFolder() {
    final Provider provider =
        Provider.newBuilder().setBitbucketServer(BitbucketServerProvider.newBuilder().build()).build();
    final FindFilesInBranchRequest.Builder requestBuilder = FindFilesInBranchRequest.newBuilder().setProvider(provider);
    final GetFilesInFolderForkTask testGetFilesInFolderTask =
        new GetFilesInFolderForkTask(null, provider, null, null, scmBlockingStub);
    when(scmBlockingStub.findFilesInBranch(any())).thenReturn(getFindFilesInBranchResponse());

    List<FileChange> fileChangeList = testGetFilesInFolderTask.getAllFilesPresentInFolder(requestBuilder);
    assertThat(fileChangeList.size()).isEqualTo(1);
    assertThat(fileChangeList.get(0).getPath()).isEqualTo("directory/parent/child.yaml");
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testAddFilesOfThisFolder() {
    final Provider provider = Provider.newBuilder().setAzure(AzureProvider.newBuilder().build()).build();
    final FindFilesInBranchRequest.Builder requestBuilder = FindFilesInBranchRequest.newBuilder().setProvider(provider);
    final GetFilesInFolderForkTask testGetFilesInFolderTask =
        new GetFilesInFolderForkTask(null, provider, null, null, scmBlockingStub);
    when(scmBlockingStub.findFilesInBranch(any())).thenReturn(getFindFilesInBranchResponseWithDirectory());

    List<FileChange> fileChangeList = testGetFilesInFolderTask.getAllFilesPresentInFolder(requestBuilder);
    List<FileChange> filePaths = new ArrayList<>();
    testGetFilesInFolderTask.addFilesOfThisFolder(filePaths, fileChangeList);
    assertThat(filePaths.size()).isEqualTo(1);
    assertThat(filePaths.get(0).getPath()).isEqualTo("directory/parent/child.yaml");
  }

  private FindFilesInBranchResponse getFindFilesInBranchResponseWithPage(int page) {
    return FindFilesInBranchResponse.newBuilder()
        .setPagination(PageResponse.newBuilder().setNext(page).build())
        .build();
  }

  private FindFilesInBranchResponse getFindFilesInBranchResponseWithDirectory() {
    return FindFilesInBranchResponse.getDefaultInstance()
        .toBuilder()
        .setPagination(PageResponse.newBuilder().setNext(0).setNextUrl("").build())
        .addFile(FileChange.newBuilder()
                     .setPath("directory/parent")
                     .setContentType(ContentType.DIRECTORY)
                     .setBlobId("123")
                     .setCommitId("123")
                     .build())
        .addFile(FileChange.newBuilder()
                     .setPath("directory/parent/child.yaml")
                     .setContentType(ContentType.FILE)
                     .setBlobId("456")
                     .setCommitId("123")
                     .build())
        .build();
  }

  private FindFilesInBranchResponse getFindFilesInBranchResponse() {
    return FindFilesInBranchResponse.newBuilder()
        .addFile(FileChange.newBuilder()
                     .setPath("directory/parent/child.yaml")
                     .setContentType(ContentType.FILE)
                     .setBlobId("456")
                     .setCommitId("123")
                     .build())
        .setPagination(PageResponse.newBuilder().setNext(0).setNextUrl("").build())
        .build();
  }
}
