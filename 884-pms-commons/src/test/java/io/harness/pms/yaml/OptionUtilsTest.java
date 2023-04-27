/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.yaml.options.Options;
import io.harness.yaml.repository.Repository;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class OptionUtilsTest extends CategoryTest {
  String pipelineYamlV1;

  @Before
  public void before() {
    pipelineYamlV1 = readFile("pipeline-v1.yaml");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetOptions() {
    Optional<Options> optionalOptions = OptionUtils.getOptions(pipelineYamlV1);
    assertThat(optionalOptions.isPresent()).isTrue();
    Options options = optionalOptions.get();
    assertThat(options).isNotNull();

    Repository repository = options.getRepository();
    assertThat(repository.getConnector()).isNotNull();
    assertThat(repository.getConnector().fetchFinalValue()).isEqualTo("account.GitHub");
    assertThat(repository.getName()).isNotNull();
    assertThat(repository.getName().fetchFinalValue()).isEqualTo("<+inputs.repo>");
  }
}
