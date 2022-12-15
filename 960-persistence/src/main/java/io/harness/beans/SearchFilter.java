/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SearchFilter {
  public enum Operator {
    EQ,
    NOT_EQ,
    LT,
    LT_EQ,
    GE,
    GT,
    CONTAINS,
    STARTS_WITH,
    HAS,
    IN,
    NOT_IN,
    EXISTS,
    NOT_EXISTS,
    HAS_ALL,
    OR,
    AND,
    ELEMENT_MATCH,
    TEXT_SEARCH;
  }

  private String fieldName;
  private Object[] fieldValues;
  private Operator op;
}
