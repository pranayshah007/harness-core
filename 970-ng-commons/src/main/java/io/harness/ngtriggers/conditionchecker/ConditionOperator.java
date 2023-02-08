/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.conditionchecker;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.CONTAINS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.DOES_NOT_CONTAIN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.ENDS_WITH_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.EQUALS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.IN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.NOT_EQUALS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.NOT_IN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.REGEX_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.STARTS_WITH_OPERATOR;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("operator")
@OwnedBy(PIPELINE)
public enum ConditionOperator {
  @JsonProperty(IN_OPERATOR) IN(IN_OPERATOR),
  @JsonProperty(EQUALS_OPERATOR) EQUALS(EQUALS_OPERATOR),
  @JsonProperty(NOT_EQUALS_OPERATOR) NOT_EQUALS(NOT_EQUALS_OPERATOR),
  @JsonProperty(NOT_IN_OPERATOR) NOT_IN(NOT_IN_OPERATOR),
  @JsonProperty(REGEX_OPERATOR) REGEX(REGEX_OPERATOR),
  @JsonProperty(ENDS_WITH_OPERATOR) ENDS_WITH(ENDS_WITH_OPERATOR),
  @JsonProperty(STARTS_WITH_OPERATOR) STARTS_WITH(STARTS_WITH_OPERATOR),
  @JsonProperty(CONTAINS_OPERATOR) CONTAINS(CONTAINS_OPERATOR),
  @JsonProperty(DOES_NOT_CONTAIN_OPERATOR) DOES_NOT_CONTAIN(DOES_NOT_CONTAIN_OPERATOR);

  private static final Set<ConditionOperator> listOperators =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(IN, NOT_IN)));
  private String value;

  ConditionOperator(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static Set<ConditionOperator> fetchListOperators() {
    return listOperators;
  }
}
