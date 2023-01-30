/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VLICA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ProvisionerExceptionHelperTest {
  private static final String TEST_ERROR_NO_MODULE =
      "\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mUnreadable module directory\u001B[0m\u001B[0mUnable to evaluate directory symlink: lstat modues: no such file or directory\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mFailed to read module directory\u001B[0m\u001B[0mModule directory  does not exist or cannot be read.\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mUnreadable module directory\u001B[0m\u001B[0mUnable to evaluate directory symlink: lstat modues: no such file or directory\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mFailed to read module directory\u001B[0m\u001B[0mModule directory  does not exist or cannot be read.\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mUnreadable module directory\u001B[0m\u001B[0mUnable to evaluate directory symlink: lstat modues: no such file or directory\u001B[0m\u001B[0m\u001B[31m\u001B[1m\u001B[31mError: \u001B[0m\u001B[0m\u001B[1mFailed to read module directory\u001B[0m\u001B[0mModule directory  does not exist or cannot be read.\u001B[0m\u001B[0m";
  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetAllErrorsSample1() {
    Set<String> errors = TerraformExceptionHelper.getAllErrors(TEST_ERROR_NO_MODULE);
    // only 2 unique errors
    assertThat(errors).hasSize(2);
    assertThat(errors).containsExactlyInAnyOrder(
        "[0m\u001B[0m\u001B[1mUnreadable module directory\u001B[0m\u001B[0mUnable to evaluate directory symlink: lstat modues: no such file or directory\u001B[0m\u001B[0m",
        "[0m\u001B[0m\u001B[1mFailed to read module directory\u001B[0m\u001B[0mModule directory  does not exist or cannot be read.\u001B[0m\u001B[0m");
  }
}
