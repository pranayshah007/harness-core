/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.deployment.checks;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.configuration.DeployMode;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.checker.LimitApproachingException;
import io.harness.limits.checker.UsageLimitExceededException;
import io.harness.limits.checker.rate.MongoSlidingWindowRateLimitChecker;
import io.harness.limits.checker.rate.RateLimitVicinityChecker;
import io.harness.limits.configuration.NoLimitConfiguredException;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.lib.DeploymentErrorType;
import io.harness.limits.lib.RateLimitChecker;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.deployment.PreDeploymentChecker;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@ParametersAreNonnullByDefault
public class DeploymentRateLimitChecker implements PreDeploymentChecker {
  private static final int PERCENT_TO_WARN_ON_DEFAULT = 85;
  private static final int LONG_LIMIT_DURATION = 24;
  private static final TimeUnit LONG_LIMIT_DURATION_UNIT = TimeUnit.HOURS;
  private static final double SHORT_LIMIT_PERCENT = 0.4D;

  private LimitCheckerFactory limitCheckerFactory;
  private WingsPersistence persistence;

  @Inject
  public DeploymentRateLimitChecker(LimitCheckerFactory limitCheckerFactory, WingsPersistence persistence) {
    this.limitCheckerFactory = limitCheckerFactory;
    this.persistence = persistence;
  }

  /**
   * checks if the deployments being done is within allowed rate limits.
   * @throws UsageLimitExceededException in case limit exceeds
   * @throws LimitApproachingException in case user is close to hitting limit. These exceptions should not block
   * deployments, useful to give some warnings, though.
   */
  @Override
  public void check(String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      String deployMode = System.getenv(DeployMode.DEPLOY_MODE);
      if (DeployMode.isOnPrem(deployMode)) {
        return;
      }

      Action deployAction = new Action(accountId, ActionType.DEPLOY);
      final int warningPercentage = percentToWarnOn();

      try {
        RateLimitChecker checker = (RateLimitChecker) limitCheckerFactory.getInstance(deployAction);
        RateLimitChecker shortDurationChecker = shortDurationChecker(checker, deployAction);

        if (null != shortDurationChecker && !shortDurationChecker.checkAndConsume()) {
          log.info("Short Duration Deployment Limit Reached. accountId={}, Limit: {}", accountId,
              shortDurationChecker.getLimit());
          DeploymentErrorType errorType = DeploymentErrorType.builder()
                                              .errorType(DeploymentErrorType.ErrorType.SHORT_TERM_DEPLOYMENT_ERROR)
                                              .limit(SHORT_LIMIT_PERCENT)
                                              .build();
          throw new UsageLimitExceededException(ErrorCode.USAGE_LIMITS_EXCEEDED, Level.ERROR, WingsException.USER,
              shortDurationChecker.getLimit(), accountId, errorType);
        }

        if (!checker.checkAndConsume()) {
          RateLimit limit = checker.getLimit();
          log.info("Deployment Limit Reached. accountId={}, Limit: {}", accountId, limit.getCount());
          DeploymentErrorType errorType = DeploymentErrorType.builder()
                                              .errorType(DeploymentErrorType.ErrorType.LONG_TERM_DEPLOYMENT_ERROR)
                                              .limit(1.0)
                                              .build();
          throw new UsageLimitExceededException(
              ErrorCode.USAGE_LIMITS_EXCEEDED, Level.ERROR, WingsException.USER, limit, accountId, errorType);
        }

        RateLimitVicinityChecker vicinityChecker = (RateLimitVicinityChecker) checker;
        if (vicinityChecker.crossed(warningPercentage)) {
          RateLimit limit = vicinityChecker.getLimit();
          log.info("Deployment vicinity reached. accountId={}, Limit: {}", accountId, limit.getCount());
          throw new LimitApproachingException(limit, accountId, warningPercentage);
        }
      } catch (NoLimitConfiguredException e) {
        log.error(
            "No limit is configured for action: {} for account {}. Deployments will be allowed to maintain backward compatibility. But deployments are NOT being rate limited.",
            deployAction, accountId, e);
      }
    }
  }

  private static int percentToWarnOn() {
    String percent = System.getenv().get("DEPLOYMENT_RATE_LIMIT_WARN_PERCENTAGE");
    if (null != percent) {
      try {
        return Integer.parseInt(percent);
      } catch (NumberFormatException e) {
        log.error("Error reading DEPLOYMENT_RATE_LIMIT_WARN_PERCENTAGE from env variables. Found Value: {}", percent);
        return PERCENT_TO_WARN_ON_DEFAULT;
      }
    }

    return PERCENT_TO_WARN_ON_DEFAULT;
  }

  // This is prevent against case where a customer might have a burst of deployment attempts and end up consuming all 24
  // limits and then are blocked for 24 hours. With this change they can only consume 40% of 24 hour limit in an hour.
  @Nullable
  private RateLimitChecker shortDurationChecker(RateLimitChecker longDurationChecker, Action action) {
    RateLimit limit = longDurationChecker.getLimit();

    if (!(limit.getDurationUnit() == LONG_LIMIT_DURATION_UNIT && limit.getDuration() == LONG_LIMIT_DURATION)) {
      return null;
    }

    // 40% of 24 hour limit allowed per hour
    return new MongoSlidingWindowRateLimitChecker(
        new RateLimit((int) (limit.getCount() * SHORT_LIMIT_PERCENT), 1, TimeUnit.HOURS), persistence,
        action.key() + "_hourly");
  }
}
