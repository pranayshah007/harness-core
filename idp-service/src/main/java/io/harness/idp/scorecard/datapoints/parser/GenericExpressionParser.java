/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.jexl3.JexlException;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class GenericExpressionParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    String outcomeExpression = dataPoint.getOutcomeExpression();
    Map<String, Map<String, Object>> expressionData = new HashMap<>();
    expressionData.put(dataPoint.getDataSourceIdentifier(), data);
    IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(expressionData);

    Map<String, Object> dataPointResponse = new HashMap<>();
    Object value = null;
    dataPointResponse.put(ERROR_MESSAGE_KEY, "");
    try {
      value = evaluator.evaluateExpression(outcomeExpression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
      if (value == null) {
        log.warn(
            "Could not find the required data by evaluating expression for data point {}", dataPoint.getIdentifier());
        dataPointResponse.put(ERROR_MESSAGE_KEY, "Missing Data");
      }
    } catch (JexlException e) {
      log.warn("Datapoint expression evaluation failed for data point {}", dataPoint.getIdentifier(), e);
      dataPointResponse.put(ERROR_MESSAGE_KEY, "Datapoint extraction expression evaluation failed");
    }
    dataPointResponse.put(DATA_POINT_VALUE_KEY, value);
    return dataPointResponse;
  }
}
