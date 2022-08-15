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
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@OwnedBy(PIPELINE)
@Data
@Builder
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.plancreator.strategy.ExcludeConfig")
public class ExcludeConfig {
  Map<String, String> exclude;

  @JsonAnySetter
  void setAxis(String key, Object value) {
    if (this.exclude == null) {
      this.exclude = new HashMap<>();
    }
    if (key.equals(YamlNode.UUID_FIELD_NAME)) {
      return;
    }
    try {
      Map<String, Object> map = (Map<String, Object>) value;
      map.remove(YamlNode.UUID_FIELD_NAME);
      this.exclude.put(key, JsonUtils.asJson(map));
    } catch (Exception ex) {
      this.exclude.put(key, String.valueOf(value));
    }
  }

  public ExcludeConfig(Map<String, String> axisValue) {
    axisValue.remove(YamlNode.UUID_FIELD_NAME);
    this.exclude = axisValue;
  }

  @JsonValue
  public Map<String, String> toJson() {
    return exclude;
  }
}
