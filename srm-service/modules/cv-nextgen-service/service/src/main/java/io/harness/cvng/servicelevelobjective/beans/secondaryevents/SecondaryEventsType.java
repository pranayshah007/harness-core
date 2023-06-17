/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.secondaryevents;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum SecondaryEventsType {
  @JsonProperty("Downtime") DOWNTIME("Downtime"),
  @JsonProperty("DataCollectionFailure") DATA_COLLECTION_FAILURE("DataCollectionFailure"),
  @JsonProperty("Annotation") ANNOTATION("Annotation"),
  @JsonProperty("ErrorBudgetReset") ERROR_BUDGET_RESET("ErrorBudgetReset");

  private String value;

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
