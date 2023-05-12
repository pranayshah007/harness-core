/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public interface NGFailureTypeConstants {
  String ALL_ERRORS = "AllErrors";
  String UNKNOWN = "Unknown";
  String AUTHENTICATION_ERROR = "Authentication";
  String CONNECTIVITY_ERROR = "Connectivity";
  String TIMEOUT_ERROR = "Timeout";
  String AUTHORIZATION_ERROR = "Authorization";
  String VERIFICATION_ERROR = "Verification";
  String DELEGATE_PROVISIONING_ERROR = "DelegateProvisioning";
  String POLICY_EVALUATION_FAILURE = "PolicyEvaluationFailure";
  String INPUT_TIMEOUT_ERROR = "InputTimeoutError";
  String APPROVAL_REJECTION = "ApprovalRejection";
  String DELEGATE_RESTART_ERROR = "DelegateRestart";
  String USER_MARKED_FAILURE_ERROR = "UserMarkedFailure";
}
