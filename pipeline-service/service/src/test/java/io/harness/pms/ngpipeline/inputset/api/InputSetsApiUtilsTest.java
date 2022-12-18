/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.ngpipeline.inputset.api;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.MANKRIT;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.pipeline.v1.model.GitDetails;
import io.harness.spec.server.pipeline.v1.model.InputSetResponseBody;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InputSetsApiUtilsTest extends CategoryTest {
  private InputSetsApiUtils inputSetsApiUtils;
  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);

  @Before
  public void setup() {
    inputSetsApiUtils = new InputSetsApiUtils();
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetGitDetails() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .objectIdOfYaml("objectId")
                                        .branch("branch")
                                        .repo("repoName")
                                        .repoURL("repoURL")
                                        .filePath("filePath")
                                        .build();
    GitDetails gitDetails = inputSetsApiUtils.getGitDetails(inputSetEntity);
    assertEquals("objectId", gitDetails.getObjectId());
    assertEquals("branch", gitDetails.getBranchName());
    assertEquals("filePath", gitDetails.getFilePath());
    assertEquals("repoURL", gitDetails.getRepoUrl());
    assertEquals("repoName", gitDetails.getRepoName());
  }

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testGetResponseBody() {
    InputSetEntity inputSetEntity = InputSetEntity.builder()
                                        .yaml("yaml")
                                        .identifier(slug)
                                        .name(name)
                                        .createdAt(123456L)
                                        .lastUpdatedAt(987654L)
                                        .build();
    InputSetResponseBody responseBody = inputSetsApiUtils.getInputSetResponse(inputSetEntity);
    assertEquals("yaml", responseBody.getInputSetYaml());
    assertEquals(slug, responseBody.getSlug());
    assertEquals(name, responseBody.getName());
    assertEquals(123456L, responseBody.getCreated().longValue());
    assertEquals(987654L, responseBody.getUpdated().longValue());
  }
}
