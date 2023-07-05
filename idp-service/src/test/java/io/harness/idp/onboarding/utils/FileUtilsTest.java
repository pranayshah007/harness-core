/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.utils;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class FileUtilsTest extends CategoryTest {
  static final String TEST_DIRECTORY_PATH = "/tmp/idp/onboarding/utils/test";
  static final String TEST_FILE_PATH = "/tmp/idp/onboarding/utils/test/test.txt";
  static final String TEST_DUMMY_FILE_CONTENT = "dummy";
  static final String TEST_DUMMY_FILE_PATH = "/abcdef/ghijkl/test.txt";

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCreateDirectories() {
    FileUtils.createDirectories(TEST_DIRECTORY_PATH);

    assertTrue(Files.exists(Path.of(TEST_DIRECTORY_PATH)));
    assertThatThrownBy(() -> FileUtils.createDirectories((String) null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteStringInFile() throws IOException {
    FileUtils.createDirectories(TEST_DIRECTORY_PATH);
    assertTrue(Files.exists(Path.of(TEST_DIRECTORY_PATH)));

    FileUtils.writeStringInFile(TEST_DUMMY_FILE_CONTENT, TEST_FILE_PATH);
    Path filePath = Path.of(TEST_FILE_PATH);
    assertTrue(Files.exists(filePath));
    String fileContent = Files.readString(filePath, StandardCharsets.UTF_8);
    assertEquals(TEST_DUMMY_FILE_CONTENT + "\n", fileContent);

    assertThatThrownBy(() -> FileUtils.writeStringInFile(TEST_DUMMY_FILE_CONTENT, TEST_DUMMY_FILE_PATH))
        .isInstanceOf(UnexpectedException.class)
        .hasMessage("Error writing string in file");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteObjectAsYamlInFile() throws IOException {
    FileUtils.createDirectories(TEST_DIRECTORY_PATH);
    assertTrue(Files.exists(Path.of(TEST_DIRECTORY_PATH)));

    FileUtils.writeObjectAsYamlInFile(Collections.singletonList(TEST_DUMMY_FILE_CONTENT), TEST_FILE_PATH);
    Path filePath = Path.of(TEST_FILE_PATH);
    assertTrue(Files.exists(filePath));
    String fileContent = Files.readString(filePath, StandardCharsets.UTF_8);
    assertEquals("  - " + TEST_DUMMY_FILE_CONTENT + "\n\n", fileContent);

    assertThatThrownBy(() -> FileUtils.writeObjectAsYamlInFile(new ArrayList<>(), TEST_DUMMY_FILE_PATH))
        .isInstanceOf(UnexpectedException.class)
        .hasMessage("Error writing object as yaml in file");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCleanUpDirectories() {
    FileUtils.createDirectories(TEST_DIRECTORY_PATH);
    Path directoryPath = Path.of(TEST_DIRECTORY_PATH);

    assertTrue(Files.exists(directoryPath));

    FileUtils.cleanUpDirectories(TEST_DIRECTORY_PATH);
    assertFalse(Files.exists(directoryPath));

    assertThatThrownBy(() -> FileUtils.cleanUpDirectories((String) null)).isInstanceOf(NullPointerException.class);
  }
}
