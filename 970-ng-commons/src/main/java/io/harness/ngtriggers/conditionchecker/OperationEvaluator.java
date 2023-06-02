/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.conditionchecker;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PIPELINE)
public interface OperationEvaluator {
  String EQUALS_OPERATOR = "Equals";
  String NOT_EQUALS_OPERATOR = "NotEquals";
  String STARTS_WITH_OPERATOR = "StartsWith";
  String ENDS_WITH_OPERATOR = "EndsWith";
  String CONTAINS_OPERATOR = "Contains";
  String DOES_NOT_CONTAIN_OPERATOR = "DoesNotContain";
  String REGEX_OPERATOR = "Regex";
  String IN_OPERATOR = "In";
  String NOT_IN_OPERATOR = "NotIn";

  boolean evaluate(String input, String standard);
}
