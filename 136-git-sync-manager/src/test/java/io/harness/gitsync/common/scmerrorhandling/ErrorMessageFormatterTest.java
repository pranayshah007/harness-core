/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling;

import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.util.ErrorMessageFormatter;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class ErrorMessageFormatterTest extends CategoryTest {
  private ErrorMetadata errorMetadata;
  private static final String branchName = "Main";
  private static final String repoName = "Github-Repo";
  private static final String filePath = ".harness/pipeline.yaml";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    errorMetadata = ErrorMetadata.builder().repoName(repoName).filepath(filePath).branchName(branchName).build();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testBranchKey() {
    String message = "branch<BRANCH> is invalid";
    String expectedMessage = "branch " + getFormattedValue(branchName) + " is invalid";
    String formattedMessage = ErrorMessageFormatter.formatMessage(message, errorMetadata);
    assertThat(formattedMessage).isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testRepoKey() {
    String message = "repo<REPO> is invalid";
    String expectedMessage = "repo " + getFormattedValue(repoName) + " is invalid";
    String formattedMessage = ErrorMessageFormatter.formatMessage(message, errorMetadata);
    assertThat(formattedMessage).isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testFilepathKey() {
    String message = "filepath<FILEPATH> is invalid";
    String expectedMessage = "filepath " + getFormattedValue(filePath) + " is invalid";
    String formattedMessage = ErrorMessageFormatter.formatMessage(message, errorMetadata);
    assertThat(formattedMessage).isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testComplexMessages() {
    String message = "filepath<FILEPATH> in given branch<BRANCH> and repo<REPO> is invalid";
    String expectedMessage = "filepath " + getFormattedValue(filePath) + " in given branch "
        + getFormattedValue(branchName) + " and repo " + getFormattedValue(repoName) + " is invalid";
    String formattedMessage = ErrorMessageFormatter.formatMessage(message, errorMetadata);
    assertThat(formattedMessage).isEqualTo(expectedMessage);

    message = "filepath<FILEPATH> filepath<FILEPATH> filepath<FILEPATH> is invalid";
    expectedMessage = "filepath " + getFormattedValue(filePath) + " filepath " + getFormattedValue(filePath)
        + " filepath " + getFormattedValue(filePath) + " is invalid";
    formattedMessage = ErrorMessageFormatter.formatMessage(message, errorMetadata);
    assertThat(formattedMessage).isEqualTo(expectedMessage);

    message = "repo<REPO> repo<REPO> repo<REPO> is invalid";
    expectedMessage = "repo " + getFormattedValue(repoName) + " repo " + getFormattedValue(repoName) + " repo "
        + getFormattedValue(repoName) + " is invalid";
    formattedMessage = ErrorMessageFormatter.formatMessage(message, errorMetadata);
    assertThat(formattedMessage).isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testIfEmptyMetadataPassed() {
    String message = "filepath<FILEPATH> in given branch<BRANCH> and repo<REPO> is invalid";
    String expectedMessage = "filepath in given branch and repo is invalid";
    String formattedMessage = ErrorMessageFormatter.formatMessage(message, ErrorMetadata.builder().build());
    assertThat(formattedMessage).isEqualTo(expectedMessage);

    message = "filepath<FILEPATH> filepath<FILEPATH> filepath<FILEPATH> is invalid";
    expectedMessage = "filepath filepath filepath is invalid";
    formattedMessage = ErrorMessageFormatter.formatMessage(message, ErrorMetadata.builder().build());
    assertThat(formattedMessage).isEqualTo(expectedMessage);

    message = "repo<REPO> repo<REPO> repo<REPO> is invalid";
    expectedMessage = "repo repo repo is invalid";
    formattedMessage = ErrorMessageFormatter.formatMessage(message, ErrorMetadata.builder().build());
    assertThat(formattedMessage).isEqualTo(expectedMessage);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testIfPartialMetadataPassed() {
    String message = "filepath<FILEPATH> in given branch<BRANCH> and repo<REPO> is invalid";
    String expectedMessage = "filepath in given branch " + getFormattedValue(branchName) + " and repo is invalid";
    String formattedMessage =
        ErrorMessageFormatter.formatMessage(message, ErrorMetadata.builder().branchName(branchName).build());
    assertThat(formattedMessage).isEqualTo(expectedMessage);

    expectedMessage = "filepath " + getFormattedValue(filePath) + " in given branch and repo is invalid";
    formattedMessage = ErrorMessageFormatter.formatMessage(message, ErrorMetadata.builder().filepath(filePath).build());
    assertThat(formattedMessage).isEqualTo(expectedMessage);

    expectedMessage = "filepath in given branch and repo " + getFormattedValue(repoName) + " is invalid";
    formattedMessage = ErrorMessageFormatter.formatMessage(message, ErrorMetadata.builder().repoName(repoName).build());
    assertThat(formattedMessage).isEqualTo(expectedMessage);
  }

  private String getFormattedValue(String value) {
    return "[" + value + "]";
  }
}
