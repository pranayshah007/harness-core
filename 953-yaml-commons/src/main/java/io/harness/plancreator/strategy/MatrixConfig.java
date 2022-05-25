/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Data
@Builder
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.plancreator.strategy.MatrixConfig")
public class MatrixConfig {
  private static String EXCLUDE_KEYWORD = "exclude";
  private static String BATCH_SIZE = "batchSize";

  @ApiModelProperty(hidden = true) @Builder.Default Map<String, AxisConfig> axes = new LinkedHashMap<>();
  List<ExcludeConfig> exclude;
  long batchSize;

  @JsonAnySetter
  void setAxis(String key, Object value) {
    if (axes == null) {
      axes = new HashMap<>();
    }
    if (key.equals(YamlNode.UUID_FIELD_NAME)) {
      return;
    }
    if (value instanceof List) {
      axes.put(key, new AxisConfig(ParameterField.createValueField((List<String>) value)));
    } else if (value instanceof String) {
      axes.put(key, new AxisConfig(ParameterField.createExpressionField(true, (String) value, null, false)));
    }
  }
}
