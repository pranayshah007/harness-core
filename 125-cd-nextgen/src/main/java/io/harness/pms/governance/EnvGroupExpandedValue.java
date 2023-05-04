/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity.EnvironmentGroupKeys;
import io.harness.governance.ExpansionKeysConstants;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "EnvGroupExpandedValueKeys")
public class EnvGroupExpandedValue implements ExpandedValue {
  private static final String ENVIRONMENTS = "environments";
  private String name;
  private String identifier;
  private String envGroupRef;
  private Map<String, Object> metadata;
  private Boolean deployToAll;
  private List<SingleEnvironmentExpandedValue> environments;
  @Override
  public String getKey() {
    return ExpansionKeysConstants.ENV_GROUP_EXPANSION_KEY;
  }

  @Override
  @SneakyThrows
  public String toJson() {
    Map<String, Object> map = new HashMap<>();
    map.put(EnvironmentGroupKeys.name, name);
    map.put(EnvironmentGroupKeys.identifier, identifier);
    map.put(EnvGroupExpandedValueKeys.envGroupRef, envGroupRef);
    map.put(EnvGroupExpandedValueKeys.metadata, metadata);
    map.put(EnvGroupExpandedValueKeys.deployToAll, deployToAll);
    map.put(ENVIRONMENTS, environments);
    String json = JsonPipelineUtils.writeJsonString(map);
    YamlConfig yamlConfig = new YamlConfig(json);
    JsonNode parentNode = yamlConfig.getYamlMap();
    JsonNode node = parentNode.get(ENVIRONMENTS);
    if (node != null && node.isArray() && node.size() > 0) {
      node.forEach(EnvironmentExpansionUtils::processSingleEnvNode);
      return parentNode.toPrettyString();
    }
    return json;
  }
}
