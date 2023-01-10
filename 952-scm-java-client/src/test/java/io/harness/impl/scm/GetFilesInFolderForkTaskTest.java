/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.product.ci.scm.proto.BitbucketServerProvider;
import io.harness.product.ci.scm.proto.FileChange;
import io.harness.product.ci.scm.proto.FindFilesInBranchRequest;
import io.harness.product.ci.scm.proto.FindFilesInBranchResponse;
import io.harness.product.ci.scm.proto.GithubProvider;
import io.harness.product.ci.scm.proto.PageResponse;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class GetFilesInFolderForkTaskTest extends CategoryTest {
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

  private FindFilesInBranchResponse getFindFilesInBranchResponseWithPage(int page) {
    return FindFilesInBranchResponse.newBuilder()
        .setPagination(PageResponse.newBuilder().setNext(page).build())
        .build();
  }
}
