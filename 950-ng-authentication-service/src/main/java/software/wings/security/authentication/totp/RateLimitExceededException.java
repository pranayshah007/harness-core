/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.totp;

import static io.harness.eraro.ErrorCode.USAGE_LIMITS_EXCEEDED;
import static io.harness.eraro.Level.ERROR;
import static io.harness.exception.FailureType.AUTHENTICATION;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;

import java.util.EnumSet;

@OwnedBy(HarnessTeam.PL)
public class RateLimitExceededException extends WingsException {
  RateLimitExceededException(String userUuid) {
    super(errorMessage(userUuid), null, USAGE_LIMITS_EXCEEDED, ERROR, USER, EnumSet.of(AUTHENTICATION));
  }

  private static String errorMessage(String userUuid) {
    String format = "Rate limit breached for user with UUID of %s";
    return String.format(format, userUuid);
  }
}
