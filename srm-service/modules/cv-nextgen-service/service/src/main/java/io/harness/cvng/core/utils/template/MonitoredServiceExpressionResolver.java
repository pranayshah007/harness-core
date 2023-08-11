/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.template;

import static io.harness.cvng.core.constant.MonitoredServiceConstants.REGULAR_EXPRESSION;

import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceYamlDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.sdk.core.resolver.expressions.EngineGrpcExpressionService;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.annotation.Nullable;
import lombok.SneakyThrows;

@Singleton
public class MonitoredServiceExpressionResolver {
  // option = true when shouldConfigureWithPms is false, and StartupTest
  @Inject(optional = true) private EngineGrpcExpressionService engineGrpcExpressionService;

  private MonitoredServiceYamlExpressionEvaluator getEvaluator(String monitoredServiceYaml) {
    return MonitoredServiceYamlExpressionEvaluator.builder().yaml(monitoredServiceYaml).build();
  }
  @SneakyThrows
  public MonitoredServiceDTO resolve(String monitoredServiceYaml, @Nullable Ambiance ambiance) {
    if (ambiance != null && engineGrpcExpressionService != null) {
      // If ambiance is not null, resolve the pipeline variables first, by keeping the unresolved expressions as it is
      // Unresolved expressions can be monitoredService variables or Pipeline variables, that couldn't be resolved
      monitoredServiceYaml = engineGrpcExpressionService.renderExpression(
          ambiance, monitoredServiceYaml, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
    }
    monitoredServiceYaml = sanitizeTemplateYaml(monitoredServiceYaml);
    MonitoredServiceYamlExpressionEvaluator yamlExpressionEvaluator = getEvaluator(monitoredServiceYaml);
    MonitoredServiceDTO monitoredServiceDTO =
        YamlUtils.read(monitoredServiceYaml, MonitoredServiceYamlDTO.class).getMonitoredServiceDTO();
    return (MonitoredServiceDTO) yamlExpressionEvaluator.resolve(
        monitoredServiceDTO, io.harness.expression.common.ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED);
  }

  private String sanitizeTemplateYaml(String templateResolvedYaml) throws IOException {
    YamlField rootYamlNode = YamlUtils.readTree(templateResolvedYaml);
    JsonNode rootNode = rootYamlNode.getNode().getCurrJsonNode();
    ObjectNode monitoredService = (ObjectNode) rootNode.get("monitoredService");
    monitoredService.put("identifier", REGULAR_EXPRESSION);
    monitoredService.put("name", REGULAR_EXPRESSION);
    return YamlUtils.writeYamlString(rootYamlNode);
  }
}
