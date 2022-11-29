/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.limits;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.alert.AlertData;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.checker.StaticLimitVicinityChecker;
import io.harness.limits.checker.StaticLimitVicinityCheckerMongoImpl;
import io.harness.limits.checkers.LimitChecker;
import io.harness.limits.checkers.StaticLimitChecker;
import io.harness.limits.configuration.NoLimitConfiguredException;
import io.harness.limits.lib.LimitType;
import io.harness.limits.lib.StaticLimit;

import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.ResourceUsageApproachingLimitAlert;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.limits.LimitVicinityHandler;

import com.google.inject.Inject;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LimitVicinityHandlerImpl implements LimitVicinityHandler {
  @Inject private LimitCheckerFactory limitCheckerFactory;
  @Inject private WingsPersistence persistence;
  @Inject private AlertService alertService;

  private static final int PERCENT_TO_WARN_ON_DEFAULT = 80;

  @Override
  public void checkAndAct(String accountId) {
    if (GLOBAL_ACCOUNT_ID.equals(accountId)) {
      return;
    }

    Arrays.stream(ActionType.values())
        .filter(actionType -> actionType.getAllowedLimitTypes().contains(LimitType.STATIC))
        .map(actionType -> new Action(accountId, actionType))
        .forEach(this::handleAction);
  }

  private void handleAction(Action action) {
    final int percentToWarnOn = percentToWarnOn();
    final String accountId = action.getAccountId();

    LimitChecker limitChecker;
    try {
      limitChecker = limitCheckerFactory.getInstance(action);
    } catch (NoLimitConfiguredException e) {
      log.error(
          "No limit configured. No check will be done. Configure a default limit in DefaultLimitsServiceImpl. Action: {}",
          action);
      return;
    }

    // only alert for static limit checks. Deployment limits alerts are handled separately.
    if (limitChecker instanceof StaticLimitChecker) {
      StaticLimit limit = ((StaticLimitChecker) limitChecker).getLimit();
      StaticLimitVicinityChecker checker = new StaticLimitVicinityCheckerMongoImpl(limit, action.key(), persistence);

      AlertData alertData =
          new ResourceUsageApproachingLimitAlert(limit, accountId, action.getActionType(), percentToWarnOn);

      if (checker.hasCrossedPercentLimit(percentToWarnOn)) {
        alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.RESOURCE_USAGE_APPROACHING_LIMIT, alertData);
      } else {
        alertService.closeAlert(accountId, GLOBAL_APP_ID, AlertType.RESOURCE_USAGE_APPROACHING_LIMIT, alertData);
      }
    } else {
      log.error("Unhandled type of limit checker. Either alert for it, or explicitly exclude it. Class: {}",
          limitChecker.getClass().getSimpleName());
    }
  }

  private static int percentToWarnOn() {
    String percent = System.getenv().get("RESOURCE_LIMIT_USAGE_WARN_PERCENTAGE");
    if (null != percent) {
      try {
        return Integer.parseInt(percent);
      } catch (NumberFormatException e) {
        log.error("Error reading RESOURCE_LIMIT_USAGE_WARN_PERCENTAGE from env variables. Found Value: {}", percent);
        return PERCENT_TO_WARN_ON_DEFAULT;
      }
    }

    return PERCENT_TO_WARN_ON_DEFAULT;
  }
}
