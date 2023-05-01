/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.IVAN;
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
public class FilePathUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testAddEndingSlashIfMissing() {
    assertThat(FilePathUtils.addEndingSlashIfMissing("abc")).isEqualTo("abc/");
    assertThat(FilePathUtils.addEndingSlashIfMissing("abc/")).isEqualTo("abc/");
    assertThat(FilePathUtils.addEndingSlashIfMissing("abc//")).isEqualTo("abc//");
    assertThat(FilePathUtils.addEndingSlashIfMissing("")).isEqualTo("/");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testIsScopedFilePath() {
    assertThat(FilePathUtils.isScopedFilePath("account:/folder1/folder2/configFile")).isTrue();
    assertThat(FilePathUtils.isScopedFilePath("org:/folder1/folder2/configFile")).isTrue();
    assertThat(FilePathUtils.isScopedFilePath("/folder1/folder2/configFile")).isTrue();
    assertThat(FilePathUtils.isScopedFilePath("/folder1/folder2/")).isTrue();
    assertThat(FilePathUtils.isScopedFilePath("/folder1")).isTrue();

    assertThat(FilePathUtils.isScopedFilePath("folder1/folder2/configFile")).isFalse();
    assertThat(FilePathUtils.isScopedFilePath("configFile")).isFalse();
    assertThat(FilePathUtils.isScopedFilePath(":/folder1/folder2/configFile")).isFalse();
  }
}
