/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.ADITHYA;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmConflictException;
import io.harness.gitx.ThreadOperationContext;
import io.harness.gitx.USER_FLOW;
import io.harness.manage.GlobalContextManager;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PipelineGitXHelperTest {
  String branch = "isThisMaster";
  String scmBadRequest = "SCM bad request";

  String fallBackBranch = "main-patch";

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testShouldRetryWithNonDefaultBranch() {
    assertTrue(PipelineGitXHelper.shouldRetryWithFallBackBranch(
        new ScmBadRequestException(scmBadRequest), branch, fallBackBranch));

    assertFalse(
        PipelineGitXHelper.shouldRetryWithFallBackBranch(new ScmBadRequestException(scmBadRequest), branch, branch));

    assertFalse(
        PipelineGitXHelper.shouldRetryWithFallBackBranch(new ScmConflictException(scmBadRequest), branch, branch));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsGetFileContentOnly() {
    assertFalse(PipelineGitXHelper.isExecutionFlow());

    GlobalContextManager.set(new GlobalContext());
    GlobalContextManager.upsertGlobalContextRecord(
        ThreadOperationContext.builder().userFlow(USER_FLOW.EXECUTION).build());
    assertTrue(PipelineGitXHelper.isExecutionFlow());
  }
}
