/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.mongodb.MongoException;
import java.time.temporal.ChronoUnit;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.TransactionException;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class PersistenceUtils {
  public static final RetryPolicy<Object> DEFAULT_RETRY_POLICY =
      getRetryPolicy("Retrying Operation. Attempt No. {}", "Operation Failed. Attempt No. {}");

  public static RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handleIf(ex -> {
          if ((ex instanceof TransactionException) || (ex instanceof TransientDataAccessException)) {
            return true;
          } else if (ex instanceof MongoException) {
            return ((MongoException) ex).hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
          }
          return false;
        })
        .withBackoff(1, 10, ChronoUnit.SECONDS)
        .withMaxAttempts(3)
        .onFailedAttempt(event -> log.warn(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }
}
