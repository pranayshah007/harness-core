/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.gitsync.gitxwebhooks.utils;

import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.gitxwebhooks.entity.GitXWebhook;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookUtilsTest extends GitSyncTestBase {
  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetClosetGitXWebhookForGivenScope() {
    Scope fileScopePro = Scope.of("acc", "org", "pro");
    Scope fileScopeOrg = Scope.of("acc", "org");
    Scope fileScopeAcc = Scope.of("acc");
    GitXWebhook acc = GitXWebhook.builder().accountIdentifier("acc").build();
    GitXWebhook org = GitXWebhook.builder().accountIdentifier("acc").orgIdentifier("org").build();
    GitXWebhook pro =
        GitXWebhook.builder().accountIdentifier("acc").orgIdentifier("org").projectIdentifier("pro").build();
    List<GitXWebhook> gitXWebhookList = new ArrayList<>();
    gitXWebhookList.add(acc);
    gitXWebhookList.add(org);
    gitXWebhookList.add(pro);

    GitXWebhook res = GitXWebhookUtils.getClosetGitXWebhookForGivenScope(fileScopePro, gitXWebhookList);
    assertEquals(res.getIdentifier(), pro.getIdentifier());

    res = GitXWebhookUtils.getClosetGitXWebhookForGivenScope(fileScopeOrg, gitXWebhookList);
    assertEquals(res.getIdentifier(), org.getIdentifier());

    res = GitXWebhookUtils.getClosetGitXWebhookForGivenScope(fileScopeAcc, gitXWebhookList);
    assertEquals(res.getIdentifier(), acc.getIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetClosetGitXWebhookForGivenScopeCase2() {
    //    when file path is project but no project level webhook
    Scope fileScopePro = Scope.of("acc", "org", "pro");
    GitXWebhook acc = GitXWebhook.builder().accountIdentifier("acc").build();
    GitXWebhook org = GitXWebhook.builder().accountIdentifier("acc").orgIdentifier("org").build();
    List<GitXWebhook> gitXWebhookList = new ArrayList<>();
    gitXWebhookList.add(acc);
    gitXWebhookList.add(org);

    GitXWebhook res = GitXWebhookUtils.getClosetGitXWebhookForGivenScope(fileScopePro, gitXWebhookList);
    assertEquals(res.getIdentifier(), org.getIdentifier());

    //    when file path is project but no project or org level webhook
    gitXWebhookList.remove(org);
    res = GitXWebhookUtils.getClosetGitXWebhookForGivenScope(fileScopePro, gitXWebhookList);
    assertEquals(res.getIdentifier(), acc.getIdentifier());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsBiDirectionalSyncEnabled() {
    //    project level file matching with project scope
    Scope fileScopePro = Scope.of("acc", "org", "pro");
    GitXWebhook acc = GitXWebhook.builder().accountIdentifier("acc").isEnabled(true).build();
    GitXWebhook org =
        GitXWebhook.builder().accountIdentifier("acc").orgIdentifier("org").isEnabled(true).folderPaths(null).build();
    GitXWebhook pro = GitXWebhook.builder()
                          .accountIdentifier("acc")
                          .orgIdentifier("org")
                          .isEnabled(true)
                          .projectIdentifier("pro")
                          .folderPaths(Arrays.asList(".harness/pipeline"))
                          .build();
    List<GitXWebhook> gitXWebhookList = new ArrayList<>();
    gitXWebhookList.add(acc);
    gitXWebhookList.add(org);
    gitXWebhookList.add(pro);

    assertTrue(GitXWebhookUtils.isBiDirectionalSyncEnabled(fileScopePro, gitXWebhookList, ".harness"));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testIsBiDirectionalSyncEnabledCase2() {
    //    project level file matching with org scope
    Scope fileScopePro = Scope.of("acc", "org", "pro");
    GitXWebhook acc = GitXWebhook.builder().accountIdentifier("acc").isEnabled(true).build();
    GitXWebhook org =
        GitXWebhook.builder().accountIdentifier("acc").orgIdentifier("org").isEnabled(true).folderPaths(null).build();
    GitXWebhook pro = GitXWebhook.builder()
                          .accountIdentifier("acc")
                          .orgIdentifier("org")
                          .isEnabled(false)
                          .projectIdentifier("pro")
                          .build();
    List<GitXWebhook> gitXWebhookList = new ArrayList<>();
    gitXWebhookList.add(acc);
    gitXWebhookList.add(org);
    gitXWebhookList.add(pro);

    assertTrue(GitXWebhookUtils.isBiDirectionalSyncEnabled(fileScopePro, gitXWebhookList, ".harness"));
  }
}
