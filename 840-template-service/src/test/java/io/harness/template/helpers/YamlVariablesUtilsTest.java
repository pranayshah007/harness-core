/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.TemplateServiceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class YamlVariablesUtilsTest extends TemplateServiceTestBase {
  private static final String sampleYaml = "monitoredService:\n"
      + "  type: Application\n"
      + "  identifier: identifier\n"
      + "  serviceRef: serviceRef\n"
      + "  sources:\n"
      + "    healthSources:\n"
      + "      - name: appD\n"
      + "        identifier: appd_id_1\n"
      + "        type: AppDynamics\n"
      + "  variables:\n"
      + "    - name: environmentIdentifier\n"
      + "      type: String\n"
      + "      value: <+input>";

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  @SneakyThrows
  public void testConvertStepTemplateYamlToPMSUnderstandableYaml() {
    VariableMergeServiceResponse variableMergeServiceResponse =
        YamlVariablesUtils.getVariablesFromYaml(sampleYaml, TemplateEntityType.MONITORED_SERVICE_TEMPLATE);
    YamlField uuidInjectedYaml = YamlUtils.readTree(variableMergeServiceResponse.getYaml());
    assertThat(variableMergeServiceResponse.getMetadataMap()).hasSize(3);
    String serviceRefUuid =
        uuidInjectedYaml.getNode().getField("monitoredService").getNode().getField("serviceRef").getNode().asText();
    assertThat(variableMergeServiceResponse.getMetadataMap().get(serviceRefUuid).getYamlProperties().getFqn())
        .isEqualTo("monitoredService.serviceRef");
    String healthSourceNameUuid = uuidInjectedYaml.getNode()
                                      .getField("monitoredService")
                                      .getNode()
                                      .getField("sources")
                                      .getNode()
                                      .getField("healthSources")
                                      .getNode()
                                      .asArray()
                                      .get(0)
                                      .getField("name")
                                      .getNode()
                                      .asText();
    assertThat(variableMergeServiceResponse.getMetadataMap().get(healthSourceNameUuid).getYamlProperties().getFqn())
        .isEqualTo("monitoredService.sources.healthSources.appd_id_1.name");
    String variableUuid = uuidInjectedYaml.getNode()
                              .getField("monitoredService")
                              .getNode()
                              .getField("variables")
                              .getNode()
                              .asArray()
                              .get(0)
                              .getField("value")
                              .getNode()
                              .asText();
    assertThat(variableMergeServiceResponse.getMetadataMap().get(variableUuid).getYamlProperties().getFqn())
        .isEqualTo("monitoredService.variables.environmentIdentifier");
  }
}
