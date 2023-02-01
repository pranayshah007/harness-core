/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.utils;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.yaml.registry.Registry;
import io.harness.yaml.registry.RegistryCredential;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineYamlHelperTest extends CategoryTest {
  private String pipelineYamlV1;

  @Before
  public void before() {
    pipelineYamlV1 = readFile("pipeline-v1.yaml");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRegistry() {
    Optional<Registry> optionalRegistry = PipelineYamlHelper.getRegistry(pipelineYamlV1);
    assertThat(optionalRegistry.isPresent()).isTrue();
    Registry registry = optionalRegistry.get();
    List<RegistryCredential> credentials = registry.getCredentials();
    assertThat(credentials).hasSize(2);
    assertThat(credentials.get(0).getName().fetchFinalValue()).isEqualTo("account.docker");
    assertThat(credentials.get(0).getMatch().fetchFinalValue()).isNull();
    assertThat(credentials.get(1).getName().fetchFinalValue()).isEqualTo("account.dockerhub");
    assertThat(credentials.get(1).getMatch().fetchFinalValue()).isEqualTo("docker.io");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRegistryIfNotPresent() {
    String yaml = "version: 1";
    Optional<Registry> optionalRegistry = PipelineYamlHelper.getRegistry(yaml);
    assertThat(optionalRegistry.isEmpty()).isTrue();
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }
}
