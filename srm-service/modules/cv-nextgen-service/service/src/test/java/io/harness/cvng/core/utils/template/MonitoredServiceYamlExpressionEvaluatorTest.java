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
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceYamlDTO;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MonitoredServiceYamlExpressionEvaluatorTest extends CvNextGenTestBase {
  private String yaml = "monitoredService:\n"
      + "  identifier: identifier\n"
      + "  type: Application\n"
      + "  description: description\n"
      + "  name: <+monitoredService.serviceRef>_<+monitoredService.environmentRef>\n"
      + "  serviceRef: service1\n"
      + "  environmentRef: env1\n"
      + "  environmentRefList: \n"
      + "   - env1\n"
      + "  tags: {}\n"
      + "  sources:\n"
      + "    healthSources:\n"
      + "      - name: appD\n"
      + "        identifier: appd_id_1\n"
      + "        type: AppDynamics\n"
      + "        spec:\n"
      + "          connectorRef: <+monitoredService.description>\n"
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
      + "          connectorRef: <+monitoredService.description>\n"
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
  public void testResolve() throws Exception {
    MonitoredServiceYamlExpressionEvaluator monitoredServiceYamlExpressionEvaluator =
        new MonitoredServiceYamlExpressionEvaluator(yaml);
    MonitoredServiceDTO monitoredServiceDTO =
        YamlUtils.read(yaml, MonitoredServiceYamlDTO.class).getMonitoredServiceDTO();
    monitoredServiceDTO =
        (MonitoredServiceDTO) monitoredServiceYamlExpressionEvaluator.resolve(monitoredServiceDTO, false);
    assertThat(monitoredServiceDTO.getName()).isEqualTo("service1_env1");
    assertThat(monitoredServiceDTO.getSources().getHealthSources().iterator().next().getSpec().getConnectorRef())
        .isEqualTo("description");
  }
}
