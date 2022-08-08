/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.utils;

import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class GitSyncFilePathUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testFilePath() {
    String completePath = "testHarness/.harness/test.yaml";
    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(completePath);
    assertThat(gitEntityFilePath.getRootFolder()).isEqualTo("/testHarness/.harness/");
    assertThat(gitEntityFilePath.getFilePath()).isEqualTo("test.yaml");

    completePath = "testFolder/folder-temp/.harness/harness/test.yaml";
    gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(completePath);
    assertThat(gitEntityFilePath.getRootFolder()).isEqualTo("/testFolder/folder-temp/.harness/");
    assertThat(gitEntityFilePath.getFilePath()).isEqualTo("harness/test.yaml");
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testCreateFilePath() {
    String folderPath = "testHarness/.harness";
    String filePath = "testConnector.yaml";
    String completeFilePath = GitSyncFilePathUtils.createFilePath(folderPath, filePath);
    assertThat(completeFilePath).isEqualTo("/testHarness/.harness/testConnector.yaml");

    folderPath = "testHarness/tests/.harness";
    filePath = "tests/test-connector.yaml";
    completeFilePath = GitSyncFilePathUtils.createFilePath(folderPath, filePath);
    assertThat(completeFilePath).isEqualTo("/testHarness/tests/.harness/tests/test-connector.yaml");
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testFormatFilePath() {
    String filePath = "filePath";
    String formattedFilePath = GitSyncFilePathUtils.formatFilePath(filePath);
    assertThat(formattedFilePath).isEqualTo("/filePath");

    filePath = "/filePath";
    formattedFilePath = GitSyncFilePathUtils.formatFilePath(filePath);
    assertThat(formattedFilePath).isEqualTo("/filePath");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFilePathWithoutStartingSlash() {
    String filePath = "filePath";
    String formattedFilePath = GitSyncFilePathUtils.getFilePathWithoutStartingSlash(filePath);
    assertThat(formattedFilePath).isEqualTo("filePath");

    filePath = "/////filePath/.harness";
    formattedFilePath = GitSyncFilePathUtils.getFilePathWithoutStartingSlash(filePath);
    assertThat(formattedFilePath).isEqualTo("filePath/.harness");
  }
}