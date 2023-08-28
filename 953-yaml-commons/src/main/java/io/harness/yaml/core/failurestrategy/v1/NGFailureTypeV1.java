/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.v1;

import static io.harness.pms.contracts.execution.failure.FailureType.APPLICATION_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.AUTHENTICATION_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.AUTHORIZATION_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.CONNECTIVITY_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.DELEGATE_PROVISIONING_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.DELEGATE_RESTART;
import static io.harness.pms.contracts.execution.failure.FailureType.SKIPPING_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.TIMEOUT_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.UNKNOWN_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.USER_MARKED_FAILURE;
import static io.harness.pms.contracts.execution.failure.FailureType.VERIFICATION_FAILURE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.failure.FailureType;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.EnumSet;

@OwnedBy(HarnessTeam.PIPELINE)
public enum NGFailureTypeV1 {
  @JsonProperty(NGFailureTypeConstantsV1.UNKNOWN)
  UNKNOWN(NGFailureTypeConstantsV1.UNKNOWN, EnumSet.of(APPLICATION_FAILURE, SKIPPING_FAILURE, UNKNOWN_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.ALL_ERRORS)
  ALL_ERRORS(NGFailureTypeConstantsV1.ALL_ERRORS,
      EnumSet.of(APPLICATION_FAILURE, SKIPPING_FAILURE, UNKNOWN_FAILURE, AUTHENTICATION_FAILURE, AUTHORIZATION_FAILURE,
          CONNECTIVITY_FAILURE, VERIFICATION_FAILURE, TIMEOUT_FAILURE, DELEGATE_PROVISIONING_FAILURE,
          FailureType.POLICY_EVALUATION_FAILURE, FailureType.APPROVAL_REJECTION, DELEGATE_RESTART,
          USER_MARKED_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.AUTHENTICATION_ERROR)
  AUTHENTICATION_ERROR(NGFailureTypeConstantsV1.AUTHENTICATION_ERROR, EnumSet.of(AUTHENTICATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.CONNECTIVITY_ERROR)
  CONNECTIVITY_ERROR(NGFailureTypeConstantsV1.CONNECTIVITY_ERROR, EnumSet.of(CONNECTIVITY_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.TIMEOUT_ERROR)
  TIMEOUT_ERROR(NGFailureTypeConstantsV1.TIMEOUT_ERROR, EnumSet.of(TIMEOUT_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.AUTHORIZATION_ERROR)
  AUTHORIZATION_ERROR(NGFailureTypeConstantsV1.AUTHORIZATION_ERROR, EnumSet.of(AUTHORIZATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.VERIFICATION_ERROR)
  VERIFICATION_ERROR(NGFailureTypeConstantsV1.VERIFICATION_ERROR, EnumSet.of(VERIFICATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.DELEGATE_PROVISIONING_ERROR)
  DELEGATE_PROVISIONING_ERROR(
      NGFailureTypeConstantsV1.DELEGATE_PROVISIONING_ERROR, EnumSet.of(DELEGATE_PROVISIONING_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.POLICY_EVALUATION_FAILURE)
  POLICY_EVALUATION_FAILURE(
      NGFailureTypeConstantsV1.POLICY_EVALUATION_FAILURE, EnumSet.of(FailureType.POLICY_EVALUATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.INPUT_TIMEOUT_ERROR)
  INPUT_TIMEOUT_FAILURE(NGFailureTypeConstantsV1.INPUT_TIMEOUT_ERROR, EnumSet.of(FailureType.INPUT_TIMEOUT_FAILURE)),
  @JsonProperty(NGFailureTypeConstantsV1.APPROVAL_REJECTION)
  APPROVAL_REJECTION(NGFailureTypeConstantsV1.APPROVAL_REJECTION, EnumSet.of(FailureType.APPROVAL_REJECTION)),
  @JsonProperty(NGFailureTypeConstantsV1.DELEGATE_RESTART_ERROR)
  DELEGATE_RESTART_ERROR(NGFailureTypeConstantsV1.DELEGATE_RESTART_ERROR, EnumSet.of(DELEGATE_RESTART)),
  @JsonProperty(NGFailureTypeConstantsV1.USER_MARKED_FAILURE_ERROR)
  USER_MARKED_AS_FAILURE(NGFailureTypeConstantsV1.USER_MARKED_FAILURE_ERROR, EnumSet.of(USER_MARKED_FAILURE));

  private final String yamlName;
  private final EnumSet<FailureType> failureType;

  NGFailureTypeV1(String yamlName, EnumSet<FailureType> failureType) {
    this.yamlName = yamlName;
    this.failureType = failureType;
  }
}
