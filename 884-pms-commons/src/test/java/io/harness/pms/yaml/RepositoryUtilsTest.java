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
import io.harness.yaml.repository.Reference;
import io.harness.yaml.repository.ReferenceType;
import io.harness.yaml.repository.Repository;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CI)
public class RepositoryUtilsTest extends CategoryTest {
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
  public void testGetRepositoryFromPipelineYaml() {
    String pipelineYaml = readFile("pipeline-v1.yaml");
    Optional<Repository> optionalRepository = RepositoryUtils.getRepositoryFromPipelineYaml(pipelineYaml);
    assertThat(optionalRepository.isPresent()).isTrue();
    Repository repository = optionalRepository.get();
    assertThat(repository).isNotNull();

    assertThat(repository.getConnector()).isNotNull();
    assertThat(repository.getConnector().fetchFinalValue()).isEqualTo("account.GitHub");

    assertThat(repository.getName()).isNotNull();
    assertThat(repository.getName().fetchFinalValue()).isEqualTo("<+inputs.repo>");

    assertThat(repository.getDisabled()).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetRepositoryFromPipelineYamlWithoutRepositoryNode() {
    String pipelineYaml = readFile("pipeline-v1-without-repository.yaml");
    Optional<Repository> optionalRepository = RepositoryUtils.getRepositoryFromPipelineYaml(pipelineYaml);
    assertThat(optionalRepository.isEmpty()).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetReferenceFromInputsPayload() {
    String inputsPayload = readFile("inputs-payload.json");
    Optional<Reference> optionalReference = RepositoryUtils.getReferenceFromInputPayload(inputsPayload);
    assertThat(optionalReference.isPresent()).isTrue();
    Reference reference = optionalReference.get();
    assertThat(reference.getValue()).isEqualTo("main");
    assertThat(reference.getType()).isEqualTo(ReferenceType.BRANCH);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetReferenceFromInputsYamlPayload() {
    String inputsPayload = readFile("inputs-yaml-payload.yaml");
    Optional<Reference> optionalReference = RepositoryUtils.getReferenceFromInputPayload(inputsPayload);
    assertThat(optionalReference.isPresent()).isTrue();
    Reference reference = optionalReference.get();
    assertThat(reference.getValue()).isEqualTo("main");
    assertThat(reference.getType()).isEqualTo(ReferenceType.BRANCH);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetReferenceFromInputsPayloadWithoutRepository() {
    String inputsPayload = readFile("inputs-payload-without-repository.json");
    Optional<Reference> optionalReference = RepositoryUtils.getReferenceFromInputPayload(inputsPayload);
    assertThat(optionalReference.isEmpty()).isTrue();
  }
}
