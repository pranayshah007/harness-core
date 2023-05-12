/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.utils;

import static io.harness.rule.OwnerRule.ADITHYA;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitProviderUtilsTest extends GitSyncTestBase {
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testBuildRepoForGitlabWhenSubGroupsArePresent() {
    String repo = "testRepo";
    String namespace = "gitlab160412/demo2/demo3";
    String res = GitProviderUtils.buildRepoForGitlab(namespace, repo);
    assertEquals("demo2/demo3/testRepo", res);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testBuildRepoForGitlabWhenSubGroupsAreNotPresent() {
    String repo = "testRepo";
    String namespace = "gitlab160412";
    String res = GitProviderUtils.buildRepoForGitlab(namespace, repo);
    assertEquals("testRepo", res);
  }
}
