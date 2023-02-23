/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.deploymentstage;

import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.CdDeployStageMetadataRequestDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class DeploymentStageConfigResourceTest extends CategoryTest {
  private final DeploymentStageConfigResource deploymentStageConfigResource = new DeploymentStageConfigResource();

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  @Parameters(method = "data")
  public void testGetCdStageMetadata(String cdStageYamlFilePath, String expectedSvcRef, String expectedEnvRef)
      throws IOException {
    final String cdStageYaml = readFile(cdStageYamlFilePath);
    CdDeployStageMetadataRequestDTO requestDTO =
        CdDeployStageMetadataRequestDTO.builder().stageIdentifier("S2").pipelineYaml(cdStageYaml).build();

    final ResponseDTO<CDStageMetaDataDTO> cdStageMetadata =
        deploymentStageConfigResource.getCdDeployStageMetadata(requestDTO);

    assertThat(cdStageMetadata.getData().getServiceEnvRefList().get(0))
        .isEqualTo(CDStageMetaDataDTO.ServiceEnvRef.builder()
                       .serviceRef(expectedSvcRef)
                       .environmentRef(expectedEnvRef)
                       .build());
  }

  private Object[][] data() {
    return new Object[][] {{"deploymentstage/cdStageWithSvcEnvV1.yaml", "service1a", "environment1a"},
        {"deploymentstage/cdParallelStagesWithInheritedService.yaml", "S1", "EnvFromStage2"},
        {"deploymentstage/cdStageWithSvcEnvV2.yaml", "S2", "Env2"},
        {"deploymentstage/cdStageWithSvcEnvV1WithRuntime.yaml", "<+input>", "environment1a"},
        {"deploymentstage/cdParallelStagesWithInheritedServiceWithRuntime.yaml", "<+input>", "<+variable>"},
        {"deploymentstage/cdStageWithSvcEnvV2WithRuntime.yaml", "<+variable>", "Env2"},
        {"deploymentstage/cdStageWithMultiServiceEnvironment.yaml", "svc2one", "stagingInfra"},
        {"deploymentstage/cdStageWithServicesAndEnvironmentsAsExpression.yaml", "<+input>", "<+input>"}};
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
