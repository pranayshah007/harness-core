/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;

@OwnedBy(HarnessTeam.PIPELINE)
public enum NGFailureActionTypeV1 {
  @JsonProperty(NGFailureActionTypeConstantsV1.IGNORE) IGNORE(NGFailureActionTypeConstantsV1.IGNORE),
  @JsonProperty(NGFailureActionTypeConstantsV1.RETRY) RETRY(NGFailureActionTypeConstantsV1.RETRY),
  @JsonProperty(NGFailureActionTypeConstantsV1.MARK_AS_SUCCESS)
  MARK_AS_SUCCESS(NGFailureActionTypeConstantsV1.MARK_AS_SUCCESS),
  @JsonProperty(NGFailureActionTypeConstantsV1.ABORT) ABORT(NGFailureActionTypeConstantsV1.ABORT),
  @JsonProperty(NGFailureActionTypeConstantsV1.STAGE_ROLLBACK)
  STAGE_ROLLBACK(NGFailureActionTypeConstantsV1.STAGE_ROLLBACK),
  @JsonProperty(NGFailureActionTypeConstantsV1.PIPELINE_ROLLBACK)
  PIPELINE_ROLLBACK(NGFailureActionTypeConstantsV1.PIPELINE_ROLLBACK),
  @JsonProperty(NGFailureActionTypeConstantsV1.MANUAL_INTERVENTION)
  MANUAL_INTERVENTION(NGFailureActionTypeConstantsV1.MANUAL_INTERVENTION),
  @JsonProperty(NGFailureActionTypeConstantsV1.MARK_AS_FAILURE)
  MARK_AS_FAILURE(NGFailureActionTypeConstantsV1.MARK_AS_FAILURE),
  @JsonProperty(NGFailureActionTypeConstantsV1.RETRY_STEP_GROUP)
  RETRY_STEP_GROUP(NGFailureActionTypeConstantsV1.RETRY_STEP_GROUP);

  String yamlName;

  NGFailureActionTypeV1(String yamlName) {
    this.yamlName = yamlName;
  }
}
