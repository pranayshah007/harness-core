/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@JsonInclude(Include.NON_NULL)
@Data
@FieldNameConstants(innerTypeName = "TimeSeriesThresholdCriteriaKeys")
public class TimeSeriesThresholdCriteria {
  private Double value;
  private TimeSeriesThresholdComparisonType type;
  private TimeSeriesCustomThresholdActions action;
  private Integer occurrenceCount;
  private TimeSeriesThresholdType thresholdType;
  private String criteria;

  @JsonCreator
  @Builder
  public TimeSeriesThresholdCriteria(@JsonProperty(TimeSeriesThresholdCriteriaKeys.value) double value,
      @JsonProperty(TimeSeriesThresholdCriteriaKeys.type) TimeSeriesThresholdComparisonType type,
      @JsonProperty(TimeSeriesThresholdCriteriaKeys.action) TimeSeriesCustomThresholdActions action,
      @JsonProperty(TimeSeriesThresholdCriteriaKeys.occurrenceCount) Integer occurrenceCount,
      @JsonProperty(TimeSeriesThresholdCriteriaKeys.thresholdType) TimeSeriesThresholdType thresholdType,
      @JsonProperty(TimeSeriesThresholdCriteriaKeys.criteria) String criteria) {
    this.type = type;
    this.action = action;
    this.occurrenceCount = occurrenceCount;
    setCriteria(criteria);
  }

  @JsonIgnore
  public double getValue() {
    return value;
  }

  @JsonIgnore
  public TimeSeriesThresholdType getThresholdType() {
    return thresholdType;
  }

  public final void setCriteria(String criteria) {
    final String trimmed = criteria.trim();
    if (trimmed.charAt(0) != '>' && trimmed.charAt(0) != '<') {
      throw new IllegalArgumentException("criteria has to start with '> ' or '< '");
    }
    final String[] tokens = trimmed.split("\\s+");
    String operator;
    String criteriaValue;
    if (tokens.length == 1) {
      operator = String.valueOf(tokens[0].charAt(0));
      criteriaValue = tokens[0].substring(1);
    } else if (tokens.length == 2) {
      operator = tokens[0];
      criteriaValue = tokens[1];
    } else {
      throw new IllegalArgumentException("the criteria has to be defined like '> 5.0' or '< 5.0");
    }

    this.thresholdType = TimeSeriesThresholdType.valueFromSymbol(operator);
    this.value = Double.valueOf(criteriaValue);
    this.criteria = criteria;
  }
}
