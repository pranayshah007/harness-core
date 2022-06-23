/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.services.impl;

import static io.harness.rule.OwnerRule.HINGER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGCoreTestBase;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class InfrastructureEntityServiceImplTest extends NGCoreTestBase {
  @Inject InfrastructureEntityServiceImpl infrastructureEntityService;

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(() -> infrastructureEntityService.validatePresenceOfRequiredFields("", null, "2"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("One of the required fields is null.");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateInfrastructureInputs() throws IOException {
    String filename = "infrastructure-with-runtime-inputs.yaml";
    String yaml = readFile(filename);
    InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .identifier("IDENTIFIER")
                                                  .orgIdentifier("ORG_ID")
                                                  .projectIdentifier("PROJECT_ID")
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .yaml(yaml)
                                                  .build();

    infrastructureEntityService.create(createInfraRequest);

    String infrastructureInputsFromYaml = infrastructureEntityService.createInfrastructureInputsFromYaml(
        "ACCOUNT_ID", "PROJECT_ID", "ORG_ID", "ENV_IDENTIFIER", Arrays.asList("IDENTIFIER"), false);
    String resFile = "infrastructure-with-runtime-inputs-res.yaml";
    String resInputs = readFile(resFile);
    assertThat(infrastructureInputsFromYaml).isEqualTo(resInputs);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateInfrastructureInputsWithoutRuntimeInputs() throws IOException {
    String filename = "infrastructure-without-runtime-inputs.yaml";
    String yaml = readFile(filename);
    InfrastructureEntity createInfraRequest = InfrastructureEntity.builder()
                                                  .accountId("ACCOUNT_ID")
                                                  .identifier("IDENTIFIER1")
                                                  .orgIdentifier("ORG_ID")
                                                  .projectIdentifier("PROJECT_ID")
                                                  .envIdentifier("ENV_IDENTIFIER")
                                                  .yaml(yaml)
                                                  .build();

    infrastructureEntityService.create(createInfraRequest);

    String infrastructureInputsFromYaml = infrastructureEntityService.createInfrastructureInputsFromYaml(
        "ACCOUNT_ID", "PROJECT_ID", "ORG_ID", "ENV_IDENTIFIER", Arrays.asList("IDENTIFIER1"), false);

    assertThat(infrastructureInputsFromYaml).isNull();
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
