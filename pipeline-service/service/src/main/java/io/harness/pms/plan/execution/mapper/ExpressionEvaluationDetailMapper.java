/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.plan.execution.beans.dto.ExpressionEvaluationDetail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ExpressionEvaluationDetailMapper {
  public static Map<String, ExpressionEvaluationDetail> toEvaluationDetailDto(String yaml) {
    Map<FQN, String> fqnObjectMap = RuntimeInputFormHelper.fetchExpressionAndFqnFromYaml(yaml);

    Map<String, ExpressionEvaluationDetail> mapData = new HashMap<>();
    for (Map.Entry<FQN, String> entry : fqnObjectMap.entrySet()) {
      String key = entry.getKey().getExpressionFqn();
      String value = entry.getValue();
      List<String> expressions = EngineExpressionEvaluator.findExpressions(value);
      expressions.forEach(expression
          -> mapData.put(key + "+" + expression,
              ExpressionEvaluationDetail.builder()
                  .originalExpression(expression)
                  .resolvedValue("dummy")
                  .fqn(key)
                  .build()));
    }
    return mapData;
  }
}
