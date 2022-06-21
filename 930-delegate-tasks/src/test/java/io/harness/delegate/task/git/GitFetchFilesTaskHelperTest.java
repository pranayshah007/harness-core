/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.YamlException;
import io.harness.rule.Owner;

import java.nio.file.NoSuchFileException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitFetchFilesTaskHelperTest extends CategoryTest {
  GitFetchFilesTaskHelper gitFetchFilesTaskHelper = new GitFetchFilesTaskHelper();

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testErrorMessageExtraction() {
    NoSuchFileException rootCause = new NoSuchFileException("rootCause");
    YamlException reason = new YamlException("exception reason", rootCause, WingsException.USER);
    InvalidRequestException exception = new InvalidRequestException("exception", reason);

    String extractedMessage = gitFetchFilesTaskHelper.extractErrorMessage(exception);
    assertThat(extractedMessage).contains("Reason: exception, exception reason");
    assertThat(extractedMessage).contains("Root Cause: java.nio.file.NoSuchFileException: rootCause");

    InvalidRequestException singleLevelException = new InvalidRequestException("exception");
    extractedMessage = gitFetchFilesTaskHelper.extractErrorMessage(singleLevelException);
    assertThat(extractedMessage).isEqualTo("Reason: exception, ");
  }
}
