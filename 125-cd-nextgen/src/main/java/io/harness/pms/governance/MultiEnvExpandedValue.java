/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.governance.ExpansionKeysConstants;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.SneakyThrows;

@OwnedBy(HarnessTeam.CDC)
@Builder
public class MultiEnvExpandedValue implements ExpandedValue {
  private static final String SPEC = "spec";
  private static final String VALUES = "values";
  private List<SingleEnvironmentExpandedValue> environments;
  @Override
  public String getKey() {
    return ExpansionKeysConstants.MULTI_ENV_EXPANSION_KEY;
  }

  @SneakyThrows
  @Override
  public String toJson() {
    Map<String, Object> map = Map.of(VALUES, environments);
    String json = JsonPipelineUtils.writeJsonString(map);
    YamlConfig yamlConfig = new YamlConfig(json);
    JsonNode node = yamlConfig.getYamlMap().get(VALUES);
    if (node.isArray() && node.size() > 0) {
      node.forEach(this::processSingleEnvNode);
      return node.toPrettyString();
    }
    return json;
  }

  private void processSingleEnvNode(JsonNode value) {
    if (value.isObject() && value.get(SingleEnvironmentExpandedValue.keys.infrastructures) != null) {
      JsonNode infrastructuresNode = value.get(SingleEnvironmentExpandedValue.keys.infrastructures);
      if (infrastructuresNode.isArray() && infrastructuresNode.size() > 0) {
        final List<JsonNode> nodes = new ArrayList<>();
        for (JsonNode jsonNode : infrastructuresNode) {
          nodes.add(processInfrastructureNode(jsonNode));
        }
        ArrayNode finalNode = new ArrayNode(JsonNodeFactory.instance, nodes);
        ((ObjectNode) value).remove(SingleEnvironmentExpandedValue.keys.infrastructures);
        ((ObjectNode) value).set(SingleEnvironmentExpandedValue.keys.infrastructures, finalNode);
      }
    }
  }

  // replace connectorRef by connector spec and also move out infrastructure type and spec to upper level to keep the
  // paths less verbose
  private JsonNode processInfrastructureNode(JsonNode node) {
    if (!node.isObject()) {
      return NullNode.instance;
    }
    ObjectNode infraNode = (ObjectNode) node.get(InfrastructureExpandedValue.keys.infrastructureDefinition);
    ObjectNode connectorNode = (ObjectNode) node.get(InfrastructureExpandedValue.keys.infrastructureConnectorNode);
    ObjectNode spec = (ObjectNode) infraNode.get(YAMLFieldNameConstants.SPEC);
    if (spec.get(YamlTypes.CONNECTOR_REF) != null && connectorNode != null) {
      spec.set(ExpansionConstants.CONNECTOR_PROP_NAME, connectorNode);
      spec.remove(YamlTypes.CONNECTOR_REF);
    }
    ObjectNode finalNode = new ObjectNode(JsonNodeFactory.instance);
    finalNode.set(YAMLFieldNameConstants.TYPE, infraNode.get(YAMLFieldNameConstants.TYPE));
    finalNode.set(SPEC, infraNode.get(YAMLFieldNameConstants.SPEC));

    return finalNode;
  }
}
