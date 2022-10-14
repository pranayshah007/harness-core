/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableProperties;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomDeploymentVariablesUtilsTest extends TemplateServiceTestBase {
  private static final String sampleYaml = "customDeployment:\n"
      + "  infrastructure:\n"
      + "    variables:\n"
      + "      - name: clusterUrl\n"
      + "        type: String\n"
      + "        value: <+input>\n"
      + "      - name: image\n"
      + "        type: Connector\n"
      + "        value: account.harnessImage\n"
      + "        description: \"Connector\"\n"
      + "    fetchInstancesScript:\n"
      + "      store:\n"
      + "        type: Harness\n"
      + "        spec:\n"
      + "          files:\n"
      + "            - account:/manifest.yml\n"
      + "    instancesListPath: instances\n"
      + "    instanceAttributes:\n"
      + "      - name: hostName\n"
      + "        jsonPath: <+input>\n"
      + "        description: \"IP address of the host\"\n"
      + "  execution:\n"
      + "    stepTemplateRefs:\n"
      + "      - org.OpenStackSetup\n";

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetVariablesFromResponse() {
    Map<String, CustomDeploymentVariableProperties> metadataMap = new HashMap<>();
    metadataMap.put("id_1",
        CustomDeploymentVariableProperties.builder()
            .variableName("hostName")
            .fqn("stage.spec.infrastructure.output.variables.clusterUrl")
            .localName("infra.variables.hostName")
            .visible(true)
            .build());
    metadataMap.put("id_without_local_name",
        CustomDeploymentVariableProperties.builder()
            .variableName("hostName")
            .fqn("stage.spec.infrastructure.output.variables.clusterUrl")
            .aliasFqn("some_fqn")
            .visible(false)
            .build());
    VariableMergeServiceResponse variableMergeServiceResponse = CustomDeploymentVariablesUtils.getVariablesFromResponse(
        CustomDeploymentVariableResponseDTO.builder().yaml(sampleYaml).metadataMap(metadataMap).build());
    assertThat(variableMergeServiceResponse.getYaml()).isEqualTo(sampleYaml);
    assertThat(variableMergeServiceResponse.getMetadataMap().get("id_1").getYamlProperties().getFqn())
        .isEqualTo("stage.spec.infrastructure.output.variables.clusterUrl");
    assertThat(variableMergeServiceResponse.getMetadataMap().get("id_1").getYamlProperties().getVariableName())
        .isEqualTo("hostName");
    assertThat(variableMergeServiceResponse.getMetadataMap().get("id_1").getYamlProperties().getLocalName())
        .isEqualTo("infra.variables.hostName");
    assertThat(variableMergeServiceResponse.getMetadataMap().get("id_1").getYamlProperties().getVisible())
        .isEqualTo(true);
    assertThat(variableMergeServiceResponse.getMetadataMap().get("id_without_local_name").getYamlProperties().getFqn())
        .isEqualTo("stage.spec.infrastructure.output.variables.clusterUrl");
    assertThat(variableMergeServiceResponse.getMetadataMap()
                   .get("id_without_local_name")
                   .getYamlProperties()
                   .getVariableName())
        .isEqualTo("hostName");
    assertThat(
        variableMergeServiceResponse.getMetadataMap().get("id_without_local_name").getYamlProperties().getLocalName())
        .isEqualTo("");
    assertThat(
        variableMergeServiceResponse.getMetadataMap().get("id_without_local_name").getYamlProperties().getVisible())
        .isEqualTo(false);
    assertThat(
        variableMergeServiceResponse.getMetadataMap().get("id_without_local_name").getYamlProperties().getAliasFQN())
        .isEqualTo("some_fqn");
  }
}
