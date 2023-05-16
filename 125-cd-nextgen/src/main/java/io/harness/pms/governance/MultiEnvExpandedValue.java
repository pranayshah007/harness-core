/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.ExpansionKeysConstants;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
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

  private Map<String, Object> metadata;
  @Override
  public String getKey() {
    return ExpansionKeysConstants.MULTI_ENV_EXPANSION_KEY;
  }

  @SneakyThrows
  @Override
  public String toJson() {
    Map<String, Object> map = new HashMap<>();
    if (metadata != null) {
      map.put(VALUES, environments);
      map.put("metadata", metadata);
    }
    String json = JsonPipelineUtils.writeJsonString(map);
    YamlConfig yamlConfig = new YamlConfig(json);
    JsonNode parentNode = yamlConfig.getYamlMap();
    JsonNode node = parentNode.get(VALUES);
    if (node.isArray() && node.size() > 0) {
      node.forEach(EnvironmentExpansionUtils::processSingleEnvNode);
      return parentNode.toPrettyString();
    }
    return json;
  }
}
