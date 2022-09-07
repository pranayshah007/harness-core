/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.customDeployment.helper;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class CustomDeploymentYamlHelperImplTest extends CategoryTest {
  @InjectMocks private CustomDeploymentYamlHelperImpl customDeploymentYamlHelper;
  private static final String RESOURCE_PATH_PREFIX = "customDeployment/";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  private String readFile(String filename) {
    String relativePath = RESOURCE_PATH_PREFIX + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(relativePath)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testGetVariablesFromYaml() {
    String template = readFile("template.yaml");
    CustomDeploymentYamlRequestDTO customDeploymentYamlRequestDTO =
        CustomDeploymentYamlRequestDTO.builder().entityYaml(template).build();
    CustomDeploymentVariableResponseDTO customDeploymentVariableResponseDTO =
        customDeploymentYamlHelper.getVariablesFromYaml(customDeploymentYamlRequestDTO);

    YamlField uuidInjectedYaml = YamlUtils.readTree(customDeploymentVariableResponseDTO.getYaml());
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap()).hasSize(4);
    List<YamlNode> variablesNode = uuidInjectedYaml.getNode()
                                       .getField("customDeployment")
                                       .getNode()
                                       .getField("infrastructure")
                                       .getNode()
                                       .getField("variables")
                                       .getNode()
                                       .asArray();

    String clusterUrlUUID = variablesNode.get(0).getField("value").getNode().asText();
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(clusterUrlUUID).getFqn())
        .isEqualTo("customDeployment.infrastructure.variables.clusterUrl");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(clusterUrlUUID).getLocalName())
        .isEqualTo("infra.variables.clusterUrl");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(clusterUrlUUID).getVariableName())
        .isEqualTo("clusterUrl");

    String imageUUID = variablesNode.get(1).getField("value").getNode().asText();
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(imageUUID).getFqn())
        .isEqualTo("customDeployment.infrastructure.variables.image");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(imageUUID).getLocalName())
        .isEqualTo("infra.variables.image");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(imageUUID).getVariableName())
        .isEqualTo("image");

    String instancesListPathUUID = uuidInjectedYaml.getNode()
                                       .getField("customDeployment")
                                       .getNode()
                                       .getField("infrastructure")
                                       .getNode()
                                       .getField("instancesListPath")
                                       .getNode()
                                       .asText();
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(instancesListPathUUID).getFqn())
        .isEqualTo("customDeployment.infrastructure.instancesListPath");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(instancesListPathUUID).getLocalName())
        .isEqualTo("infra.instancesListPath");
    assertThat(customDeploymentVariableResponseDTO.getMetadataMap().get(instancesListPathUUID).getVariableName())
        .isEqualTo("instancesListPath");
  }
}
