/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.EnumSet;

@OwnedBy(HarnessTeam.PIPELINE)
public enum NGFailureType {
  @JsonProperty(NGFailureTypeConstants.UNKNOWN)
  UNKNOWN(NGFailureTypeConstants.UNKNOWN, EnumSet.of(APPLICATION_FAILURE, SKIPPING_FAILURE, UNKNOWN_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.ALL_ERRORS)
  ALL_ERRORS(NGFailureTypeConstants.ALL_ERRORS,
      EnumSet.of(APPLICATION_FAILURE, SKIPPING_FAILURE, UNKNOWN_FAILURE, AUTHENTICATION_FAILURE, AUTHORIZATION_FAILURE,
          CONNECTIVITY_FAILURE, VERIFICATION_FAILURE, TIMEOUT_FAILURE, DELEGATE_PROVISIONING_FAILURE,
          FailureType.POLICY_EVALUATION_FAILURE, FailureType.APPROVAL_REJECTION, DELEGATE_RESTART,
          USER_MARKED_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.AUTHENTICATION_ERROR)
  AUTHENTICATION_ERROR(NGFailureTypeConstants.AUTHENTICATION_ERROR, EnumSet.of(AUTHENTICATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.CONNECTIVITY_ERROR)
  CONNECTIVITY_ERROR(NGFailureTypeConstants.CONNECTIVITY_ERROR, EnumSet.of(CONNECTIVITY_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.TIMEOUT_ERROR)
  TIMEOUT_ERROR(NGFailureTypeConstants.TIMEOUT_ERROR, EnumSet.of(TIMEOUT_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.AUTHORIZATION_ERROR)
  AUTHORIZATION_ERROR(NGFailureTypeConstants.AUTHORIZATION_ERROR, EnumSet.of(AUTHORIZATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.VERIFICATION_ERROR)
  VERIFICATION_ERROR(NGFailureTypeConstants.VERIFICATION_ERROR, EnumSet.of(VERIFICATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.DELEGATE_PROVISIONING_ERROR)
  DELEGATE_PROVISIONING_ERROR(
      NGFailureTypeConstants.DELEGATE_PROVISIONING_ERROR, EnumSet.of(DELEGATE_PROVISIONING_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.POLICY_EVALUATION_FAILURE)
  POLICY_EVALUATION_FAILURE(
      NGFailureTypeConstants.POLICY_EVALUATION_FAILURE, EnumSet.of(FailureType.POLICY_EVALUATION_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.INPUT_TIMEOUT_ERROR)
  INPUT_TIMEOUT_FAILURE(NGFailureTypeConstants.INPUT_TIMEOUT_ERROR, EnumSet.of(FailureType.INPUT_TIMEOUT_FAILURE)),
  @JsonProperty(NGFailureTypeConstants.APPROVAL_REJECTION)
  APPROVAL_REJECTION(NGFailureTypeConstants.APPROVAL_REJECTION, EnumSet.of(FailureType.APPROVAL_REJECTION)),
  @JsonProperty(NGFailureTypeConstants.DELEGATE_RESTART_ERROR)
  DELEGATE_RESTART_ERROR(NGFailureTypeConstants.DELEGATE_RESTART_ERROR, EnumSet.of(DELEGATE_RESTART)),
  @JsonProperty(NGFailureTypeConstants.USER_MARKED_FAILURE_ERROR)
  USER_MARKED_AS_FAILURE(NGFailureTypeConstants.USER_MARKED_FAILURE_ERROR, EnumSet.of(USER_MARKED_FAILURE));

  private final String yamlName;
  private final EnumSet<FailureType> failureType;

  NGFailureType(String yamlName, EnumSet<FailureType> failureType) {
    this.yamlName = yamlName;
    this.failureType = failureType;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static NGFailureType getFailureTypes(String yamlName) {
    for (NGFailureType value : NGFailureType.values()) {
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

  public EnumSet<FailureType> getFailureTypes() {
    return failureType;
  }

  public static EnumSet<FailureType> getAllFailureTypes() {
    EnumSet<FailureType> allFailures = EnumSet.noneOf(FailureType.class);
    Arrays.stream(NGFailureType.values()).map(NGFailureType::getFailureTypes).forEach(allFailures::addAll);
    return allFailures;
  }
}
