/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression.functors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.text.similarity.LevenshteinDistance;

public class ExpressionChecker {
  public static String checkExpression(String inputExpression, Map<String, Object> map) {
    List<String> availableExpressions = new ArrayList<>();
    getAllExpressions(availableExpressions, map, "");
    int minDistance = Integer.MAX_VALUE;
    String closestMatch = null;
    LevenshteinDistance levenshteinDistance = new LevenshteinDistance();

    for (String availableExpression : availableExpressions) {
      int distance = levenshteinDistance.apply(inputExpression, availableExpression);
      if (distance < minDistance) {
        minDistance = distance;
        closestMatch = availableExpression;
      }
    }

    if (minDistance <= 2) {
      return closestMatch;
    } else {
      return null;
    }
  }

  private static void getAllExpressions(List<String> expressions, Map<String, Object> map, String partialExpression) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String tmpExpression = partialExpression + "." + entry.getKey();
      if (entry.getValue() instanceof Map) {
        getAllExpressions(expressions, map, tmpExpression);
      } else {
        expressions.add(tmpExpression);
      }
    }
  }
}
