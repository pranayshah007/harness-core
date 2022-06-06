/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.utils.YamlPipelineUtils;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentPlanCreatorHelperTest extends CDNGTestBase {
  private String createYamlFromPath(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testMergeEnvironmentInputs() throws IOException {
    String originalYamlWithRunTimeInputs =
        createYamlFromPath("cdng/plan/environment/originalEnvironmentWithRuntimeValue.yml");
    String inputValueYaml = createYamlFromPath("cdng/plan/environment/runtimeInputValueEnvironment.yml");

    Map<String, Object> read = YamlPipelineUtils.read(inputValueYaml, Map.class);
    assertThatCode(() -> EnvironmentPlanCreatorHelper.mergeEnvironmentInputs(originalYamlWithRunTimeInputs, read))
        .doesNotThrowAnyException();
  }
}