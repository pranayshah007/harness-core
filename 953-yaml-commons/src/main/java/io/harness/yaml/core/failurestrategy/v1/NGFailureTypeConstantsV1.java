/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.core.failurestrategy.v1;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PIPELINE)
public interface NGFailureTypeConstantsV1 {
  String ALL_ERRORS = "all";
  String UNKNOWN = "unknown";
  String AUTHENTICATION_ERROR = "authentication";
  String CONNECTIVITY_ERROR = "connectivity";
  String TIMEOUT_ERROR = "timeout";
  String AUTHORIZATION_ERROR = "authorization";
  String VERIFICATION_ERROR = "verification";
  String DELEGATE_PROVISIONING_ERROR = "delegate-provisioning";
  String POLICY_EVALUATION_FAILURE = "policy-evaluation";
  String INPUT_TIMEOUT_ERROR = "input-timeout";
  String APPROVAL_REJECTION = "approval-rejection";
  String DELEGATE_RESTART_ERROR = "delegate-restart";
  String USER_MARKED_FAILURE_ERROR = "user-mark-fail";
}
