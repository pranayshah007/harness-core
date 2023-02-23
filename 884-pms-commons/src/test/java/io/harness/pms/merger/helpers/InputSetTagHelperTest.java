/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class InputSetTagHelperTest extends CategoryTest {
  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetTagsFromYaml_withValidYaml() {
    String filename = "mergedTagsPipeline.yaml";
    String yaml = readFile(filename);
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("planExecutionId")
                            .setPlanId("planId")
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();
    List<NGTag> tagList = InputSetTagsHelper.getTagsFromYaml(yaml, ambiance);
    assertThat(tagList.size()).isEqualTo(3);
    assertThat(tagList.get(0).getKey()).isEqualTo("reverted_execution_id");
    assertThat(tagList.get(1).getKey()).isEqualTo("foo");
    assertThat(tagList.get(2).getKey()).isEqualTo("run");
    assertThat(tagList.get(0).getValue()).isEqualTo("id");
    assertThat(tagList.get(1).getValue()).isEqualTo("bar");
    assertThat(tagList.get(2).getValue()).isEmpty();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetTagsFromYaml_withInValidYaml() {
    String filename = "mergedTagsPipeline.yaml";
    String yaml = readFile(filename);
    yaml = yaml + "\n - stage:";
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId("planExecutionId")
                            .setPlanId("planId")
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
                            .build();
    List<NGTag> tagList = InputSetTagsHelper.getTagsFromYaml(yaml, ambiance);
    assertThat(tagList).isEmpty();
  }
}
