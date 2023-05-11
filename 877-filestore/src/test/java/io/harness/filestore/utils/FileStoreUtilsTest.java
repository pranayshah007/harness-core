/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileStoreUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSubPaths() {
    assertThat(FileStoreUtils.getSubPaths("").isPresent()).isFalse();
    assertThat(FileStoreUtils.getSubPaths("/").isPresent()).isFalse();
    assertThat(FileStoreUtils.getSubPaths("folder").isPresent()).isFalse();

    Optional<List<String>> subPaths = FileStoreUtils.getSubPaths("/file.txt");
    List<String> subPathsList = subPaths.get();
    assertThat(subPathsList).containsExactly("/file.txt");

    Optional<List<String>> subPaths2 = FileStoreUtils.getSubPaths("/folder1/file.txt");
    List<String> subPathsList2 = subPaths2.get();
    assertThat(subPathsList2).containsExactly("/folder1", "/folder1/file.txt");

    Optional<List<String>> subPaths3 = FileStoreUtils.getSubPaths("/folder1/folder2/folder3/file.txt");
    List<String> subPathsList3 = subPaths3.get();
    assertThat(subPathsList3)
        .containsExactly(
            "/folder1", "/folder1/folder2", "/folder1/folder2/folder3", "/folder1/folder2/folder3/file.txt");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsPathValid() {
    assertThat(FileStoreUtils.isPathValid("")).isFalse();
    assertThat(FileStoreUtils.isPathValid(null)).isFalse();
    assertThat(FileStoreUtils.isPathValid("notValid")).isFalse();
    assertThat(FileStoreUtils.isPathValid("/")).isFalse();

    assertThat(FileStoreUtils.isPathValid("/folder")).isTrue();
    assertThat(FileStoreUtils.isPathValid("/file.txt")).isTrue();
    assertThat(FileStoreUtils.isPathValid("/folder1/folder2")).isTrue();
    assertThat(FileStoreUtils.isPathValid("/folder1/file.txt")).isTrue();
  }
}
