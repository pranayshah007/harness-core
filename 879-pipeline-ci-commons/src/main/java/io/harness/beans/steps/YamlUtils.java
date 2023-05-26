/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CI)
public class YamlUtils {
  public static Map<String, ParameterField<String>> extractBaseImageConnectorRefs(
      ParameterField<List<String>> baseImageConnectorRefs) {
    Map<String, ParameterField<String>> baseConnectorRefMap = new HashMap<>();
    if (baseImageConnectorRefs != null && baseImageConnectorRefs.isExpression()) {
      baseConnectorRefMap.put(YAMLFieldNameConstants.BASE_IMAGE_CONNECTOR_REFS,
          new ParameterField<>(NGExpressionUtils.DEFAULT_INPUT_SET_EXPRESSION, true, null, false, null, false, null));
    } else if (baseImageConnectorRefs != null && baseImageConnectorRefs.getValue() != null) {
      List<String> baseImageConnectorRefsValues = baseImageConnectorRefs.getValue();
      if (baseImageConnectorRefsValues.size() > 1) {
        throw new InvalidArgumentsException("Only a single base image connector is allowed.");
      }
      String baseImageConnectorRef = baseImageConnectorRefsValues.get(0);
      baseConnectorRefMap.put(YAMLFieldNameConstants.BASE_IMAGE_CONNECTOR_REFS,
          new ParameterField<>(null, false, baseImageConnectorRef, true, null, false, null));
    }
    return baseConnectorRefMap;
  }
}
