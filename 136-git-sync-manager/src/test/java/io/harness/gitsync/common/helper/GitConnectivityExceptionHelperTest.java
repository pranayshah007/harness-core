/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.logging.LoggingInitializer.initializeLogging;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitConnectivityExceptionHelperTest extends CategoryTest {
  private final String errorMessage = "Unknown error";

  @Before
  public void setup() {
    initializeLogging();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetErrorMessage_whenDelegateDriverExceptionIsThrown() {
    Exception ex = new DelegateServiceDriverException(errorMessage);
    assertThat(GitConnectivityExceptionHelper.getErrorMessage(ex))
        .isEqualTo(GitConnectivityExceptionHelper.CONNECTIVITY_ERROR + errorMessage);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetErrorMessage_whenNullPointerExceptionIsThrown() {
    Exception ex = new NullPointerException(errorMessage);
    assertThat(GitConnectivityExceptionHelper.getErrorMessage(ex)).isEqualTo(NGErrorHelper.DEFAULT_ERROR_MESSAGE);
  }
}
