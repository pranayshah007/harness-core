/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.totp;

import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;

import software.wings.security.authentication.TotpChecker;

import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import java.security.GeneralSecurityException;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTimeUtils;

@Slf4j
public class SimpleTotpChecker<T extends SimpleTotpChecker.Request> implements TotpChecker<T> {
  @Override
  public boolean check(T request) {
    String totpSecret = request.getSecret();
    int code = request.getCode();
    final long currentTime = DateTimeUtils.currentTimeMillis();
    if (request instanceof RateLimitedTotpChecker.Request) {
      RateLimitedTotpChecker.Request totpCheckerRequest = (RateLimitedTotpChecker.Request) request;
      log.info("Checking TOTP for user {}", totpCheckerRequest.getUserUuid());
    }
    try {
      boolean isTotpValidInPresent = TimeBasedOneTimePasswordUtil.validateCurrentNumber(
          totpSecret, code, 0, currentTime, TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS);
      boolean isTotpValidInPast = TimeBasedOneTimePasswordUtil.validateCurrentNumber(
          totpSecret, code, 0, currentTime - 10000, TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS);
      boolean isTotpValidInFuture = TimeBasedOneTimePasswordUtil.validateCurrentNumber(
          totpSecret, code, 0, currentTime + 10000, TimeBasedOneTimePasswordUtil.DEFAULT_TIME_STEP_SECONDS);
      log.info("Checking TOTP, isValidInPresent: {}, isValidInPast: {}, isValidInFuture: {}", isTotpValidInPresent,
          isTotpValidInPast, isTotpValidInFuture);
      if (!isTotpValidInPresent && !isTotpValidInPast && !isTotpValidInFuture) {
        return false;
      }
    } catch (GeneralSecurityException e) {
      throw new WingsException(ErrorCode.UNKNOWN_ERROR, e);
    }
    return true;
  }

  @Getter
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Request {
    private final String secret;
    private final int code;
  }
}
