/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gitxwebhooks.utils;

import static io.harness.rule.OwnerRule.ADITHYA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitXWebhookUtilsTest extends GitSyncTestBase {
  List<String> webhookFolderPaths1 = new ArrayList<>();
  List<String> webhookFolderPaths2 = new ArrayList<>();

  @Before
  public void setup() {
    webhookFolderPaths1.add(".harness");

    webhookFolderPaths2.add("/.harness");
    webhookFolderPaths2.add("/.harness/pipelines");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSingleFolderMatch() {
    String modifiedFilePath = ".harness/testPipeline.yaml";
    List<String> matchingFolderPaths =
        GitXWebhookUtils.compareFolderPaths(webhookFolderPaths1, Arrays.asList(modifiedFilePath));
    assertNotNull(matchingFolderPaths);
    assertEquals(matchingFolderPaths.get(0), modifiedFilePath);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSingleFolderMatch2() {
    String modifiedFilePath = ".harness/org/project/testPipeline.yaml";
    List<String> matchingFolderPaths =
        GitXWebhookUtils.compareFolderPaths(webhookFolderPaths1, Arrays.asList(modifiedFilePath));
    assertNotNull(matchingFolderPaths);
    assertEquals(matchingFolderPaths.get(0), modifiedFilePath);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSingleFolderMatch3() {
    String modifiedFilePath = "rootFolder/org/project/testPipeline.yaml";
    String webhookFolderPath = "rootFolder";
    List<String> matchingFolderPaths =
        GitXWebhookUtils.compareFolderPaths(Arrays.asList(webhookFolderPath), Arrays.asList(modifiedFilePath));
    assertNotNull(matchingFolderPaths);
    assertEquals(matchingFolderPaths.get(0), modifiedFilePath);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSingleFolderMatchWhenNoMatches() {
    String modifiedFilePath = "/testFolder/testPipeline.yaml";
    List<String> matchingFolderPaths =
        GitXWebhookUtils.compareFolderPaths(webhookFolderPaths1, Arrays.asList(modifiedFilePath));
    assertNotNull(matchingFolderPaths);
    assertTrue(matchingFolderPaths.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testSingleFolderMatchWhenNoMatches2() {
    String modifiedFilePath = "rootFolder/testPipeline.yaml";
    String webhookFolderPath = "rootFolder/org/project";
    List<String> matchingFolderPaths =
        GitXWebhookUtils.compareFolderPaths(Arrays.asList(webhookFolderPath), Arrays.asList(modifiedFilePath));
    assertNotNull(matchingFolderPaths);
    assertTrue(matchingFolderPaths.isEmpty());
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMultipleFolderMatch() {
    List<String> modifiedFolderPaths = new ArrayList<>();
    modifiedFolderPaths.add(".harness/testPipeline.yaml");
    modifiedFolderPaths.add(".harness/testTemplate.yaml");

    List<String> matchingFolderPaths = GitXWebhookUtils.compareFolderPaths(webhookFolderPaths1, modifiedFolderPaths);
    assertNotNull(matchingFolderPaths);
    assertEquals(matchingFolderPaths.get(0), modifiedFolderPaths.get(0));
    assertEquals(matchingFolderPaths.get(1), modifiedFolderPaths.get(1));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testMultipleFolderMatchWhenOneFileMatches() {
    List<String> modifiedFolderPaths = new ArrayList<>();
    modifiedFolderPaths.add(".harness/testPipeline.yaml");
    modifiedFolderPaths.add("testFile/testTemplate.yaml");

    List<String> matchingFolderPaths = GitXWebhookUtils.compareFolderPaths(webhookFolderPaths1, modifiedFolderPaths);
    assertNotNull(matchingFolderPaths);
    assertEquals(matchingFolderPaths.get(0), modifiedFolderPaths.get(0));
  }
}
