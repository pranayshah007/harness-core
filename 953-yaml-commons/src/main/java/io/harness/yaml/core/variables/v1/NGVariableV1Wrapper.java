/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.variables.v1;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.core.variables.NGVariableV1;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Value
@OwnedBy(HarnessTeam.PIPELINE)
public class NGVariableV1Wrapper {
  Map<String, NGVariableV1> map = new LinkedHashMap<>();

  @JsonAnySetter
  void setNGVariableV1(String key, Object value) throws IOException {
    if (key.equals(YamlNode.UUID_FIELD_NAME)) {
      return;
    }
    NGVariableV1 variableV1 = YamlUtils.convert(value, NGVariableV1.class);
    map.put(key, variableV1);
  }
}
