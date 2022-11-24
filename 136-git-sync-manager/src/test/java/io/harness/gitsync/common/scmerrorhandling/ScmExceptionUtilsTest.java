/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.FileReadException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.dtos.GitErrorMetadata;
import io.harness.gitsync.common.helper.ScmExceptionUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ScmExceptionUtilsTest extends CategoryTest {
  private static final String branch = "branch";
  private static final String filepath = "filepath";
  private static final String error_message = "error message";

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetGitErrorMetadata() {
    WingsException exception = new FileReadException(error_message);
    GitErrorMetadata gitErrorMetadata = ScmExceptionUtils.getGitErrorMetadata(exception);
    assertThat(gitErrorMetadata).isNotNull();

    exception.setMetadata(GitErrorMetadata.builder().branch(branch).filepath(filepath).build());
    gitErrorMetadata = ScmExceptionUtils.getGitErrorMetadata(exception);
    assertThat(gitErrorMetadata).isNotNull();
    assertThat(gitErrorMetadata.getBranch()).isEqualTo(branch);
    assertThat(gitErrorMetadata.getFilepath()).isEqualTo(filepath);

    exception = new FileReadException(error_message, exception);
    gitErrorMetadata = ScmExceptionUtils.getGitErrorMetadata(exception);
    assertThat(gitErrorMetadata).isNotNull();
    assertThat(gitErrorMetadata.getBranch()).isEqualTo(branch);
    assertThat(gitErrorMetadata.getFilepath()).isEqualTo(filepath);

    exception.setMetadata(GitErrorMetadata.builder().branch(branch).build());
    gitErrorMetadata = ScmExceptionUtils.getGitErrorMetadata(exception);
    assertThat(gitErrorMetadata).isNotNull();
    assertThat(gitErrorMetadata.getBranch()).isEqualTo(branch);
    assertThat(gitErrorMetadata.getFilepath()).isNull();
  }
}
