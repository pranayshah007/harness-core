/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filter;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(DX)
public enum FilterType {
  @JsonProperty("Connector") CONNECTOR("Connector"),
  @JsonProperty("DelegateProfile") DELEGATEPROFILE("DelegateProfile"),
  @JsonProperty("Delegate") DELEGATE("Delegate"),
  @JsonProperty("PipelineSetup") PIPELINESETUP("PipelineSetup"),
  @JsonProperty("PipelineExecution") PIPELINEEXECUTION("PipelineExecution"),
  @JsonProperty("Deployment") DEPLOYMENT("Deployment"),
  @JsonProperty("Audit") AUDIT("Audit"),
  @JsonProperty("Template") TEMPLATE("Template"),
  @JsonProperty("EnvironmentGroup") ENVIRONMENTGROUP("EnvironmentGroup"),
  @JsonProperty("FileStore") FILESTORE("FileStore"),
  @JsonProperty("CCMRecommendation") CCMRECOMMENDATION("CCMRecommendation"),
  @JsonProperty("Anomaly") ANOMALY("Anomaly"),
  @JsonProperty("Environment") ENVIRONMENT("Environment");

  private String value;

  FilterType(String value) {
    this.value = value;
  }

  @Override
  @JsonValue
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static FilterType fromValue(String text) {
    for (FilterType b : FilterType.values()) {
      if (String.valueOf(b.value).equals(text)) {
        return b;
      }
    }
    return null;
  }
}
