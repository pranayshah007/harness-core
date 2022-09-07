/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.deploymentstage;

import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.creator.plan.stage.CDStageMetaDataDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Objects;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DeploymentStageConfigResourceTest extends CategoryTest {
  public static final String SVC_REF = "SVC_REF";
  private final DeploymentStageConfigResource deploymentStageConfigResource = new DeploymentStageConfigResource();

  private String cdStageYamlFilePath;
  private String expectedSvcRef;
  private String expectedEnvRef;

  public DeploymentStageConfigResourceTest(String cdStageYamlFilePath, String expectedSvcRef, String expectedEnvRef) {
    this.cdStageYamlFilePath = cdStageYamlFilePath;
    this.expectedSvcRef = expectedSvcRef;
    this.expectedEnvRef = expectedEnvRef;
  }

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return asList(new Object[][] {{"deploymentstage/cdStageWithSvcEnvV1.yaml", "service1a", "environment1a"},
        {"deploymentstage/cdStageWithSvcEnvV2.yaml", "service2", "environment2"}});
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetCdStageMetadata() throws IOException {
    final String cdStageYaml = readFile(cdStageYamlFilePath);
    final ResponseDTO<CDStageMetaDataDTO> cdStageMetadata =
        deploymentStageConfigResource.getCdDeployStageMetadata(cdStageYaml);

    assertThat(cdStageMetadata.getData().getServiceRef()).isEqualTo(expectedSvcRef);
    assertThat(cdStageMetadata.getData().getEnvironmentRef()).isEqualTo(expectedEnvRef);
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
