/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.kubernetes;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.common.ExpressionMode;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;
import io.harness.idp.scorecard.expression.IdpExpressionEvaluator;
import io.harness.spec.server.idp.v1.model.InputValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public abstract class KubernetesExpressionParser implements DataPointParser {
  public static final String WORKLOAD_PREFIX = "workload";

  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, List<InputValue> inputValues) {
    Map<String, Object> dataPointResponse = new HashMap<>();
    if (!data.containsKey(DSL_RESPONSE) && data.containsKey(ERROR_MESSAGE_KEY)) {
      dataPointResponse.put(DATA_POINT_VALUE_KEY, null);
      dataPointResponse.put(ERROR_MESSAGE_KEY, data.get(ERROR_MESSAGE_KEY));
      return dataPointResponse;
    }
    return parseKubernetesDataPoint(data, dataPoint);
  }

  private Object parseKubernetesDataPoint(Map<String, Object> data, DataPointEntity dataPoint) {
    Map<String, Object> dataPointResponse = new HashMap<>();
    String outcomeExpression = dataPoint.getOutcomeExpression();
    Map<String, List<Object>> clustersData = (Map<String, List<Object>>) data.get(DSL_RESPONSE);
    dataPointResponse.put(ERROR_MESSAGE_KEY, "");
    if (clustersData.containsKey(ERROR_MESSAGE_KEY) && clustersData.get(ERROR_MESSAGE_KEY) != null) {
      dataPointResponse.put(ERROR_MESSAGE_KEY, clustersData.get(ERROR_MESSAGE_KEY));
    }
    Object compareValue = null;

    for (Map.Entry<String, List<Object>> entry : clustersData.entrySet()) {
      if (entry.getKey().equals(ERROR_MESSAGE_KEY)) {
        continue;
      }
      String clusterName = entry.getKey();
      List<Object> clusterData = entry.getValue();
      for (Object item : clusterData) {
        Map<String, Map<String, Object>> expressionData = new HashMap<>();
        expressionData.put(dataPoint.getDataSourceIdentifier(), Map.of(WORKLOAD_PREFIX, item));
        IdpExpressionEvaluator evaluator = new IdpExpressionEvaluator(expressionData);
        try {
          Object value = evaluator.evaluateExpression(outcomeExpression, ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
          if (value == null) {
            log.warn("Could not find the required data by evaluating expression for data point {} for cluster {}",
                dataPoint.getIdentifier(), clusterName);
            continue;
          }
          Object parsedValue = parseValue(value);
          if (parsedValue != null && compare(parsedValue, compareValue)) {
            compareValue = parsedValue;
            dataPointResponse.put(DATA_POINT_VALUE_KEY, parsedValue);
            dataPointResponse.put(ERROR_MESSAGE_KEY,
                String.format("%s is %s in %s cluster", dataPoint.getIdentifier(), parsedValue, clusterName));
          }
        } catch (Exception e) {
          log.warn("Datapoint expression evaluation failed for data point {}", dataPoint.getIdentifier(), e);
        }
      }
      if (!dataPointResponse.containsKey(DATA_POINT_VALUE_KEY)) {
        dataPointResponse.put(ERROR_MESSAGE_KEY, String.format("Missing Data for cluster: %s", clusterName));
      }
    }
    return dataPointResponse;
  }

  abstract Object parseValue(Object value);

  abstract boolean compare(Object value, Object compareValue);
}
