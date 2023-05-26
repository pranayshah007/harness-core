/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@OwnedBy(HarnessTeam.PIPELINE)
public enum NGFailureActionType {
  @JsonProperty(NGFailureActionTypeConstants.IGNORE) IGNORE(NGFailureActionTypeConstants.IGNORE),
  @JsonProperty(NGFailureActionTypeConstants.RETRY) RETRY(NGFailureActionTypeConstants.RETRY),
  @JsonProperty(NGFailureActionTypeConstants.MARK_AS_SUCCESS)
  MARK_AS_SUCCESS(NGFailureActionTypeConstants.MARK_AS_SUCCESS),
  @JsonProperty(NGFailureActionTypeConstants.ABORT) ABORT(NGFailureActionTypeConstants.ABORT),
  @JsonProperty(NGFailureActionTypeConstants.STAGE_ROLLBACK)
  STAGE_ROLLBACK(NGFailureActionTypeConstants.STAGE_ROLLBACK),
  @JsonProperty(NGFailureActionTypeConstants.STEP_GROUP_ROLLBACK)
  STEP_GROUP_ROLLBACK(NGFailureActionTypeConstants.STEP_GROUP_ROLLBACK),
  @JsonProperty(NGFailureActionTypeConstants.PIPELINE_ROLLBACK)
  PIPELINE_ROLLBACK(NGFailureActionTypeConstants.PIPELINE_ROLLBACK),
  @JsonProperty(NGFailureActionTypeConstants.MANUAL_INTERVENTION)
  MANUAL_INTERVENTION(NGFailureActionTypeConstants.MANUAL_INTERVENTION),
  @JsonProperty(NGFailureActionTypeConstants.PROCEED_WITH_DEFAULT_VALUES)
  PROCEED_WITH_DEFAULT_VALUES(NGFailureActionTypeConstants.PROCEED_WITH_DEFAULT_VALUES),
  @JsonProperty(NGFailureActionTypeConstants.MARK_AS_FAILURE)
  MARK_AS_FAILURE(NGFailureActionTypeConstants.MARK_AS_FAILURE);

  String yamlName;

  NGFailureActionType(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static NGFailureActionType getFailureActionType(@JsonProperty("action") String yamlName) {
    for (NGFailureActionType value : NGFailureActionType.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }
}
