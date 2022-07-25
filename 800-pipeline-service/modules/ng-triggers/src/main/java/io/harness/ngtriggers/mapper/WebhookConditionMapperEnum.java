/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.CONTAINS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.ENDS_WITH;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.REGEX;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.STARTS_WITH;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.conditionchecker.ConditionOperator;

import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
@OwnedBy(PIPELINE)
public class WebhookConditionMapperEnum {
  private static Map<String, ConditionOperator> conditionOperatorMap;

  static {
    conditionOperatorMap = new HashMap<>();
    conditionOperatorMap.put("in", IN);
    conditionOperatorMap.put("equals", EQUALS);
    conditionOperatorMap.put("not equals", NOT_EQUALS);
    conditionOperatorMap.put("not in", NOT_IN);
    conditionOperatorMap.put("starts with", STARTS_WITH);
    conditionOperatorMap.put("ends with", ENDS_WITH);
    conditionOperatorMap.put("contains", CONTAINS);
    conditionOperatorMap.put("regex", REGEX);
  }

  public static ConditionOperator getCondtionOperationMappingForString(String operator) {
    return conditionOperatorMap.get(operator);
  }
}
