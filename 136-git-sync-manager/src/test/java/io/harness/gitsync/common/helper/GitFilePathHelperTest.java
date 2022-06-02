/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class GitFilePathHelperTest extends CategoryTest {
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testNullFilePath() {
    try {
      GitFilePathHelper.validateFilePath(null);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(GitFilePathHelper.NULL_FILE_PATH_ERROR_MESSAGE);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateFilePath_whenFilePathDoesNotHaveCorrectFormat() {
    String filePath = "//.harness/abc.yaml////";
    try {
      GitFilePathHelper.validateFilePath(filePath);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(String.format(GitFilePathHelper.INVALID_FILE_PATH_FORMAT_ERROR_MESSAGE, filePath));
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateFilePath_whenFilePathIsNotInHarnessDirectory() {
    String filePath = "abc.yaml";
    try {
      GitFilePathHelper.validateFilePath(filePath);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(String.format(GitFilePathHelper.FILE_PATH_INVALID_DIRECTORY_ERROR_FORMAT, filePath));
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateFilePath_whenFilePathHasInvalidExtension() {
    String filePath = ".harness/abc.py";
    try {
      GitFilePathHelper.validateFilePath(filePath);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(String.format(GitFilePathHelper.FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT, filePath));
    }
  }
}
