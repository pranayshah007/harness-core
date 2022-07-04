/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.limits;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.limits.checker.StaticLimitCheckerWithDecrement;
import io.harness.limits.checker.UsageLimitExceededException;
import io.harness.limits.lib.DeploymentErrorType;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.service.impl.StaticLimitActionTypeLogContext;

import java.util.function.Supplier;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@UtilityClass
@Slf4j
@TargetModule(HarnessModule._955_ACCOUNT_MGMT)
public class LimitEnforcementUtils {
  /**
   * execute the given function while enforcing usage limits.
   * Consumes a permit if the function is successful, decrements in case any error is thrown in function execution.
   *
   * @param checker limit checker instance
   * @param fn function to execute. Usually this will be something that creates a resource. (example: create an
   * application)
   * @param <T> return type of function (example: {@link software.wings.beans.Application})
   */
  public static <T> T withLimitCheck(StaticLimitCheckerWithDecrement checker, Supplier<T> fn) {
    try (AutoLogContext ignore1 = new AccountLogContext(checker.getAction().getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 =
             new StaticLimitActionTypeLogContext(checker.getAction().getActionType().name(), OVERRIDE_ERROR)) {
      boolean allowed = checker.checkAndConsume();
      if (!allowed) {
        log.info("Resource Usage Limit Reached. Limit: {}", checker.getLimit());
        DeploymentErrorType errorType = DeploymentErrorType.builder()
                                            .errorType(DeploymentErrorType.ErrorType.LONG_TERM_DEPLOYMENT_ERROR)
                                            .limit(1.0)
                                            .build();
        throw new UsageLimitExceededException(ErrorCode.USAGE_LIMITS_EXCEEDED, Level.ERROR, WingsException.USER,
            checker.getLimit(), checker.getAction().getAccountId(), errorType);
      }

      try {
        return fn.get();
      } catch (Exception e) {
        checker.decrement();
        throw e;
      }
    }
  }

  /**
   * executes a function while decrementing the counter.
   * @param checker limit checker instance
   * @param fn fn to execute. This will typically delete a resource (example: delete an application)
   */
  public static void withCounterDecrement(StaticLimitCheckerWithDecrement checker, Runnable fn) {
    fn.run();
    checker.decrement();
  }

  /**
   * executes a function while decrementing the counter.
   * @param checker limit checker instance
   * @param fn fn to execute. This will typically delete a resource (example: delete an application)
   * @param <T> return type of fn execution
   */
  public static <T> T withCounterDecrement(StaticLimitCheckerWithDecrement checker, Supplier<T> fn) {
    T value = fn.get();
    checker.decrement();
    return value;
  }
}
