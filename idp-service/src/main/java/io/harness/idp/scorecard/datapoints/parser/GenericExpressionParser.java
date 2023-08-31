/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_FOR_CHECKS_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class GenericExpressionParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    if (dataPoint.getDataSourceIdentifier().equals("catalog")) {
      data = replaceCharsNotSupportedByJexlWithUnderscore(data);
    }
    String outcomeExpression = dataPoint.getOutcomeExpression();
    Map<String, Map<String, Object>> expressionData = new HashMap<>();
    expressionData.put(dataPoint.getDataSourceIdentifier(), data);
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(expressionData);

    Map<String, Object> dataPointResponse = new HashMap<>();
    Object value = evaluator.evaluateExpression(outcomeExpression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    if (value == null) {
      log.warn("Could not evaluate expression for data point {}", dataPoint.getIdentifier());
    }
    dataPointResponse.put(DATA_POINT_VALUE_KEY, value);
    dataPointResponse.put(ERROR_MESSAGE_FOR_CHECKS_KEY, "");
    return dataPointResponse;
  }

  private Map<String, Object> replaceCharsNotSupportedByJexlWithUnderscore(Map<String, Object> map) {
    Map<String, Object> modifiedMap = new HashMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String newKey = entry.getKey().replace("-", "_");
      newKey = newKey.replace(".", "_");
      newKey = newKey.replace("/", "_");
      if (entry.getValue() instanceof Map) {
        modifiedMap.put(newKey, replaceCharsNotSupportedByJexlWithUnderscore((Map<String, Object>) entry.getValue()));
      } else {
        modifiedMap.put(newKey, entry.getValue());
      }
    }
    return modifiedMap;
  }
}
