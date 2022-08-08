/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.template;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MonitoredServiceYamlExpressionFunctorTest extends CvNextGenTestBase {
  private String yaml = "monitoredService:\n"
      + "  identifier: identifier\n"
      + "  type: Application\n"
      + "  description: description\n"
      + "  name: test\n"
      + "  serviceRef: service1\n"
      + "  environmentRefList: env1\n"
      + "  tags: {}\n"
      + "  sources:\n"
      + "    healthSources:\n"
      + "      - name: appD\n"
      + "        identifier: appd_id_1\n"
      + "        type: AppDynamics\n"
      + "        spec:\n"
      + "          connectorRef: connector1\n"
      + "          feature:  Application Monitoring\n"
      + "          applicationName: prod\n"
      + "          tierName: cv-nextgen\n"
      + "          metricPacks: \n"
      + "              - identifier: Errors\n"
      + "          metricDefinitions:\n"
      + "      - name: appD\n"
      + "        identifier: appd_id_2\n"
      + "        type: AppDynamics\n"
      + "        spec:\n"
      + "          connectorRef: conntector2\n"
      + "          feature:  Application Monitoring\n"
      + "          applicationName: prod\n"
      + "          tierName: cv-nextgen\n"
      + "          metricPacks: \n"
      + "              - identifier: Errors\n"
      + "          metricDefinitions:\n"
      + "    changeSources: \n";

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGet() {
    MonitoredServiceYamlExpressionFunctor monitoredServiceYamlExpressionFunctor =
        MonitoredServiceYamlExpressionFunctor.builder().rootYamlField(getYamlField()).build();
    String secondConnector = getFromFunctor(monitoredServiceYamlExpressionFunctor, "monitoredService", "sources",
        "healthSources", "appd_id_2", "spec", "connectorRef");
    assertThat(secondConnector).isEqualTo("conntector2");
  }

  @SneakyThrows
  private YamlField getYamlField() {
    YamlField yamlField = YamlUtils.readTree(yaml);
    return yamlField.getNode().getField("monitoredService");
  }

  private String getFromFunctor(
      MonitoredServiceYamlExpressionFunctor monitoredServiceYamlExpressionFunctor, String... path) {
    Map<String, Object> objectMap = (Map<String, Object>) monitoredServiceYamlExpressionFunctor.get(path[0]);
    for (int i = 1; i < path.length - 1; i++) {
      objectMap = (Map<String, Object>) objectMap.get(path[i]);
    }
    return (String) objectMap.get(path[path.length - 1]);
  }
}
